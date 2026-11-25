package com.example.cardreader

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class RemoteScreen {
    MAIN,
    WEBVIEW
}

interface RemoteScreenListener {
    fun onRemoteConfigUpdated(settings: AppSettings)
    fun onRemoteCommand(action: String)
    fun onLanEndpointChanged(localUrl: String, ipUrl: String?)
    fun onUpdateStatusChanged(state: String, message: String, progress: Int) {}
    fun onRemoteNavigate(routeOrUrl: String): Boolean = false
    fun onRemoteWebAction(json: JSONObject): JSONObject? = null
    fun onRemoteDirectLogin(username: String, password: String, targetUrl: String, route: String) {}
    fun onRemoteRfidScan(
        cardId: String,
        mode: String,
        source: String,
        callback: (httpStatus: Int, ok: Boolean, message: String) -> Unit
    ): Boolean = false
    fun onCaptureScreenshotJpeg(): ByteArray? = null
}

data class AttendanceRouteItem(
    val route: String,
    val label: String,
    val group: String
)

/**
 * Điều phối viên trung tâm giữ máy chủ điều khiển localhost/LAN cổng 8888, mDNS (`diemdanh.local`),
 * bộ xử lý đầu đọc thẻ RFID (USB HID + HTTP API) và trình cập nhật OTA GitHub hoạt động xuyên suốt.
 */
object RemoteControlHub : ControlServerDelegate {

    val ATTENDANCE_ROUTES: List<AttendanceRouteItem> = listOf(
        AttendanceRouteItem("/check-in-out", "Trang Quẹt Thẻ RFID", "RFID Kiosk"),
        AttendanceRouteItem("/", "Trang Chủ / Sơ Đồ", "RFID Kiosk"),
        AttendanceRouteItem("/tin-hieu-toan-truong", "Tín Hiệu Toàn Trường", "Điểm Danh"),
        AttendanceRouteItem("/danh-sach-diem-danh", "Danh Sách Điểm Danh", "Điểm Danh"),
        AttendanceRouteItem("/trang-thai-diem-danh", "Trạng Thái Điểm Danh", "Điểm Danh"),
        AttendanceRouteItem("/tin-hieu-diem-danh", "Tín Hiệu Điểm Danh", "Điểm Danh"),
        AttendanceRouteItem("/danh-sach-hoc-sinh", "Danh Sách Học Sinh", "Quản Lý"),
        AttendanceRouteItem("/tt-lop-hoc", "Thông Tin Lớp Học", "Quản Lý"),
        AttendanceRouteItem("/qly-lop-hoc", "Quản Lý Lớp Học", "Quản Lý"),
        AttendanceRouteItem("/ds-giao-vien", "Phân Quyền Giáo Viên", "Quản Lý"),
        AttendanceRouteItem("/quy-dinh-diem-danh", "Quy Định Điểm Danh", "Nhà Trường"),
        AttendanceRouteItem("/cau-hinh-nha-truong", "Cấu Hình Nhà Trường", "Nhà Trường"),
        AttendanceRouteItem("/thong-bao", "Thông Báo Nội Bộ", "Nhà Trường"),
        AttendanceRouteItem("/hoat-dong", "Hoạt Động", "Nhà Trường"),
        AttendanceRouteItem("/don-xin-nghi", "Đơn Xin Nghỉ", "Nhà Trường"),
        AttendanceRouteItem("/thung-rac", "Thùng Rác", "Hệ Thống"),
        AttendanceRouteItem("/doi-mat-khau", "Đổi Mật Khẩu", "Hệ Thống")
    )

    private val initialized = AtomicBoolean(false)
    private val updateBusy = AtomicBoolean(false)
    private lateinit var appContext: Context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateExecutor = Executors.newSingleThreadExecutor()
    private val rfidExecutor = Executors.newCachedThreadPool()

    private var mdnsAdvertiser: MdnsAdvertiser? = null
    private var controlServer: LocalControlServer? = null
    private var lanMonitor: NetworkMonitor? = null

    val rfidBuffer = RfidScannerBuffer()

    @Volatile
    var currentScreen: RemoteScreen = RemoteScreen.MAIN
        private set

    @Volatile
    var countdownSeconds: Int = -1

    @Volatile
    var isCountdownPaused: Boolean = false

    @Volatile
    var activeWebUrl: String = ""

    @Volatile
    var activeRoute: String = "/"

    @Volatile
    var loginState: String = "IDLE"

    @Volatile
    var webLoggedIn: Boolean = false

    @Volatile
    var webSchoolName: String = ""

    @Volatile
    var webAccountName: String = ""

    @Volatile
    var webAccountRole: String = ""

    @Volatile
    var pendingNavigateRoute: String = ""

    @Volatile
    var lastError: String = ""

    @Volatile
    var currentLanIp: String? = null
        private set

    @Volatile
    var updateState: String = "IDLE"
        private set

    @Volatile
    var updateMessage: String = "Chưa kiểm tra cập nhật"
        private set

    @Volatile
    var updateProgress: Int = -1
        private set

    @Volatile
    var latestRelease: ReleaseInfo? = null
        private set

    @Volatile
    private var activeListener: RemoteScreenListener? = null

    private val logs = ArrayDeque<String>(80)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext

        val settings = AppConfig.load(appContext)
        currentLanIp = MdnsAdvertiser.findLocalIpv4Address()

        mdnsAdvertiser = MdnsAdvertiser(
            context = appContext,
            httpPort = BuildConfig.CONTROL_SERVER_PORT,
            onLog = { addLog(it) }
        ).apply {
            start(settings.mdnsHostname)
        }

        controlServer = LocalControlServer(
            port = BuildConfig.CONTROL_SERVER_PORT,
            delegate = this,
            onLog = { addLog(it) }
        ).apply {
            start()
        }

        lanMonitor = NetworkMonitor(appContext, allowLanWithoutInternet = true) { connected ->
            currentLanIp = if (connected) MdnsAdvertiser.findLocalIpv4Address() else null
            if (connected) {
                mdnsAdvertiser?.announceNow()
                addLog("Mạng LAN kết nối (IP: ${currentLanIp ?: "đang lấy IP"})")
            } else {
                addLog("Mạng LAN bị ngắt")
            }
            notifyEndpointChanged()
        }.apply {
            register()
        }
    }

    fun attachListener(screen: RemoteScreen, listener: RemoteScreenListener) {
        currentScreen = screen
        activeListener = listener
        notifyEndpointChanged()
    }

    fun detachListener(listener: RemoteScreenListener) {
        if (activeListener === listener) {
            activeListener = null
        }
    }

    fun getControlUrls(): Pair<String, String?> {
        val settings = AppConfig.load(appContext)
        val mdnsUrl = "http://${settings.mdnsHostname}:${BuildConfig.CONTROL_SERVER_PORT}"
        val ip = currentLanIp ?: MdnsAdvertiser.findLocalIpv4Address()?.also { currentLanIp = it }
        val ipUrl = ip?.let { "http://$it:${BuildConfig.CONTROL_SERVER_PORT}" }
        return mdnsUrl to ipUrl
    }

    fun updateMdnsHostname(hostname: String) {
        mdnsAdvertiser?.updateHostname(hostname)
        notifyEndpointChanged()
    }

    fun clearWebSessionsIfNeeded(previousPin: String, newPin: String) {
        if (previousPin != newPin) {
            controlServer?.clearSessions()
        }
    }

    fun updateWebSessionSnapshot(
        loggedIn: Boolean,
        token: String?,
        schoolName: String,
        accountName: String,
        accountRole: String,
        route: String
    ) {
        webLoggedIn = loggedIn
        if (schoolName.isNotBlank()) webSchoolName = schoolName
        if (accountName.isNotBlank()) webAccountName = accountName
        if (accountRole.isNotBlank()) webAccountRole = accountRole
        if (route.isNotBlank()) activeRoute = route
        if (!loggedIn) {
            webSchoolName = ""
            webAccountName = ""
            webAccountRole = ""
            if (::appContext.isInitialized) {
                AppConfig.saveWebAuthToken(appContext, "")
            }
        } else if (!token.isNullOrBlank() && ::appContext.isInitialized) {
            AppConfig.saveWebAuthToken(appContext, token)
        }
    }

    fun recordRfidEventFromWeb(cardId: String, source: String, httpStatus: Int, ok: Boolean, message: String) {
        val cleanId = RfidScannerBuffer.normalizeCardId(cardId)
        if (cleanId.isEmpty()) return
        rfidBuffer.recordScan(
            cardId = cleanId,
            source = source,
            httpStatus = httpStatus,
            ok = ok,
            message = message,
            route = activeRoute
        )
        addLog("RFID [$source] $cleanId -> ${if (ok) "OK" else "LỖI $httpStatus"}: $message")
    }

    fun dispatchHardwareRfidCardAsync(cardId: String, source: String = "USB_HID") {
        val cleanId = RfidScannerBuffer.normalizeCardId(cardId)
        if (cleanId.isEmpty()) return
        rfidExecutor.execute {
            handleRfidScan(cleanId, mode = "auto", source = source)
        }
    }

    fun addLog(message: String) {
        val sanitized = message.replace(Regex("(?i)(password|pin|token|authorization)=([^&\\s]+)"), "$1=***")
        val stamp = synchronized(timeFormat) { timeFormat.format(Date()) }
        synchronized(logs) {
            if (logs.size >= 80) logs.removeFirst()
            logs.addLast("[$stamp] $sanitized")
        }
    }

    fun checkGitHubUpdateAsync(autoInstallIfAvailable: Boolean = false) {
        if (!updateBusy.compareAndSet(false, true)) return
        val repo = AppConfig.load(appContext).githubRepo
        publishUpdateStatus("CHECKING", "Đang kiểm tra bản mới trên GitHub ($repo)...", -1)
        addLog("Kiểm tra bản cập nhật GitHub: $repo")

        updateExecutor.execute {
            try {
                when (val res = GitHubUpdater.checkLatestRelease(repo, BuildConfig.VERSION_NAME)) {
                    is UpdateCheckResult.NotConfigured -> {
                        publishUpdateStatus("ERROR", "Chưa cấu hình kho GitHub hợp lệ.", -1)
                    }
                    is UpdateCheckResult.UpToDate -> {
                        latestRelease = null
                        val msg = "Đang ở bản mới nhất (v${BuildConfig.VERSION_NAME})"
                        publishUpdateStatus("UP_TO_DATE", msg, -1)
                        addLog(msg)
                    }
                    is UpdateCheckResult.UpdateAvailable -> {
                        latestRelease = res.release
                        val sizeMb = String.format(Locale.US, "%.1f MB", res.release.fileSizeBytes / 1048576.0)
                        val msg = "Có bản mới ${res.release.tagName} ($sizeMb) trên GitHub!"
                        publishUpdateStatus("AVAILABLE", msg, -1)
                        addLog(msg)
                        if (autoInstallIfAvailable) {
                            updateBusy.set(false)
                            downloadAndInstallLatestAsync()
                            return@execute
                        }
                    }
                    is UpdateCheckResult.Error -> {
                        publishUpdateStatus("ERROR", res.message, -1)
                        addLog("Cập nhật GitHub: ${res.message}")
                    }
                }
            } finally {
                updateBusy.set(false)
            }
        }
    }

    fun downloadAndInstallLatestAsync() {
        if (!updateBusy.compareAndSet(false, true)) return
        updateExecutor.execute {
            try {
                var targetRelease = latestRelease
                if (targetRelease == null) {
                    val repo = AppConfig.load(appContext).githubRepo
                    publishUpdateStatus("CHECKING", "Đang lấy thông tin bản mới từ GitHub...", 0)
                    when (val res = GitHubUpdater.checkLatestRelease(repo, BuildConfig.VERSION_NAME)) {
                        is UpdateCheckResult.UpdateAvailable -> {
                            targetRelease = res.release
                            latestRelease = res.release
                        }
                        is UpdateCheckResult.UpToDate -> {
                            publishUpdateStatus("UP_TO_DATE", "Thiết bị đang ở bản mới nhất (v${BuildConfig.VERSION_NAME}).", -1)
                            return@execute
                        }
                        is UpdateCheckResult.Error -> {
                            publishUpdateStatus("ERROR", res.message, -1)
                            return@execute
                        }
                        is UpdateCheckResult.NotConfigured -> {
                            publishUpdateStatus("ERROR", "Chưa cấu hình kho GitHub.", -1)
                            return@execute
                        }
                    }
                }

                val rel = targetRelease ?: return@execute
                addLog("Đang tải bản cập nhật ${rel.tagName} (${rel.assetName})...")
                publishUpdateStatus("DOWNLOADING", "Đang tải ${rel.tagName} (0%)...", 0)

                val apkFile = GitHubUpdater.downloadApk(appContext, rel) { pct ->
                    publishUpdateStatus("DOWNLOADING", "Đang tải ${rel.tagName} ($pct%)...", pct)
                }

                val installIntent = GitHubUpdater.buildInstallIntent(appContext, apkFile)
                publishUpdateStatus(
                    "READY_INSTALL",
                    "Đã tải xong ${rel.tagName}. Đang mở trình cài đặt trên thiết bị...",
                    100
                )
                addLog("Đã tải xong ${rel.tagName}, mở trình cài đặt APK")
                mainHandler.post {
                    runCatching { appContext.startActivity(installIntent) }
                        .onFailure { err ->
                            val msg = "Không mở được trình cài đặt: ${err.message ?: "Thiếu quyền cài đặt"}"
                            publishUpdateStatus("ERROR", msg, -1)
                            addLog(msg)
                        }
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Lỗi khi tải bản cập nhật từ GitHub"
                publishUpdateStatus("ERROR", msg, -1)
                addLog("Lỗi tải APK cập nhật: $msg")
            } finally {
                updateBusy.set(false)
            }
        }
    }

    private fun publishUpdateStatus(state: String, message: String, progress: Int) {
        updateState = state
        updateMessage = message
        updateProgress = progress
        mainHandler.post {
            activeListener?.onUpdateStatusChanged(state, message, progress)
        }
    }

    override fun getAdminPin(): String = AppConfig.load(appContext).adminPin

    override fun isLocalhostBypassEnabled(): Boolean = AppConfig.load(appContext).allowLocalhostBypass

    override fun buildStatusJson(authorized: Boolean): JSONObject {
        val settings = AppConfig.load(appContext)
        val (mdnsUrl, ipUrl) = getControlUrls()
        val lanIp = currentLanIp ?: MdnsAdvertiser.findLocalIpv4Address()

        return JSONObject().apply {
            put("versionName", BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("port", BuildConfig.CONTROL_SERVER_PORT)
            put("locked", !authorized)
            put("hasAdminPin", settings.hasAdminPin())

            if (authorized) {
                put("currentScreen", currentScreen.name)
                put("countdownSeconds", if (currentScreen == RemoteScreen.MAIN) countdownSeconds else -1)
                put("countdownPaused", isCountdownPaused)
                put("username", settings.username)
                put("hasPassword", settings.hasCredentials())
                put("targetUrl", settings.targetUrl)
                put("activeWebUrl", activeWebUrl)
                put("activeRoute", activeRoute)
                put("mdnsHostname", settings.mdnsHostname)
                put("githubRepo", settings.githubRepo)
                put("autoEnterCheckInPage", settings.autoEnterCheckInPage)
                put("hardwareRfidEnabled", settings.hardwareRfidEnabled)
                put("allowLocalhostBypass", settings.allowLocalhostBypass)
                put("lanIp", lanIp ?: "")
                put("controlUrlLocalhost", "http://127.0.0.1:${BuildConfig.CONTROL_SERVER_PORT}")
                put("controlUrlLocal", mdnsUrl)
                put("controlUrlIp", ipUrl ?: "")
                put("networkConnected", NetworkMonitor.hasNetworkAccess(appContext, allowLanWithoutInternet = true))
                put("internetConnected", NetworkMonitor.hasInternetAccess(appContext))
                put("loginState", loginState)
                put("lastError", lastError)

                put("webSession", JSONObject().apply {
                    put("loggedIn", webLoggedIn || AppConfig.getWebAuthToken(appContext).isNotEmpty())
                    put("schoolName", webSchoolName)
                    put("accountName", webAccountName.ifEmpty { settings.username })
                    put("accountRole", webAccountRole)
                    put("activeRoute", activeRoute)
                })

                put("rfid", JSONObject().apply {
                    put("totalScans", rfidBuffer.totalScans)
                    put("successCount", rfidBuffer.successCount)
                    put("errorCount", rfidBuffer.errorCount)
                    put("hardwareRfidEnabled", settings.hardwareRfidEnabled)
                    put("autoEnterCheckInPage", settings.autoEnterCheckInPage)
                    put("lastScan", rfidBuffer.getLastScan()?.toJson() ?: JSONObject.NULL)
                    put("recentScans", rfidBuffer.historyJsonArray(25))
                })

                val routesArr = JSONArray()
                for (r in ATTENDANCE_ROUTES) {
                    routesArr.put(JSONObject().apply {
                        put("route", r.route)
                        put("label", r.label)
                        put("group", r.group)
                    })
                }
                put("routes", routesArr)

                put("updateState", updateState)
                put("updateMessage", updateMessage)
                put("updateProgress", updateProgress)
                val rel = latestRelease
                put("updateAvailable", rel != null)
                put("latestVersion", rel?.tagName ?: "")
                put("latestNotes", rel?.releaseNotes ?: "")

                val logArray = JSONArray()
                synchronized(logs) {
                    for (entry in logs) logArray.put(entry)
                }
                put("logs", logArray)
            }
        }
    }

    override fun handleConfigUpdate(json: JSONObject): JSONObject {
        val prev = AppConfig.load(appContext)
        val newUsername = json.optString("username", prev.username)
        val rawPass = if (json.has("password")) json.optString("password", "") else null
        val newTargetUrl = json.optString("targetUrl", prev.targetUrl)
        val newMdns = json.optString("mdnsHostname", prev.mdnsHostname)
        val newRepo = if (json.has("githubRepo")) json.optString("githubRepo", prev.githubRepo) else null
        val clearPin = json.optBoolean("clearPin", false)
        val rawPin = if (json.has("adminPin")) json.optString("adminPin", "").trim() else ""
        val pinToSave = when {
            clearPin -> ""
            rawPin.isNotEmpty() -> rawPin
            else -> null
        }
        val autoCheckIn = if (json.has("autoEnterCheckInPage")) json.optBoolean("autoEnterCheckInPage") else null
        val hwRfid = if (json.has("hardwareRfidEnabled")) json.optBoolean("hardwareRfidEnabled") else null
        val localhostBypass = if (json.has("allowLocalhostBypass")) json.optBoolean("allowLocalhostBypass") else null

        val updated = AppConfig.saveSettings(
            context = appContext,
            username = newUsername,
            password = rawPass,
            targetUrl = newTargetUrl,
            mdnsHostname = newMdns,
            githubRepo = newRepo,
            adminPin = pinToSave,
            clearAdminPin = clearPin,
            autoEnterCheckInPage = autoCheckIn,
            hardwareRfidEnabled = hwRfid,
            allowLocalhostBypass = localhostBypass
        )

        if (prev.mdnsHostname != updated.mdnsHostname) {
            updateMdnsHostname(updated.mdnsHostname)
        }
        clearWebSessionsIfNeeded(prev.adminPin, updated.adminPin)
        addLog("Cập nhật cấu hình (Tài khoản: ${updated.username.ifEmpty { "trống" }}, URL: ${updated.targetUrl})")

        val applyAndConnect = json.optBoolean("applyAndConnect", false)
        mainHandler.post {
            activeListener?.onRemoteConfigUpdated(updated)
            if (applyAndConnect) {
                dispatchConnectCommand(updated)
            }
        }

        return JSONObject().apply {
            put("ok", true)
            put(
                "message",
                if (applyAndConnect) "Đã lưu cấu hình và đang mở màn hình Điểm danh" else "Đã lưu cấu hình lên thiết bị"
            )
        }
    }

    override fun handleWebLogin(json: JSONObject): JSONObject {
        val prev = AppConfig.load(appContext)
        val user = json.optString("username", prev.username).trim()
        val passInput = if (json.has("password")) json.optString("password", "").trim() else ""
        val pass = passInput.ifEmpty { prev.password }
        val target = json.optString("targetUrl", prev.targetUrl).ifBlank { prev.targetUrl }
        val autoCheckIn = if (json.has("autoEnterCheckInPage")) {
            json.optBoolean("autoEnterCheckInPage", prev.autoEnterCheckInPage)
        } else {
            prev.autoEnterCheckInPage
        }
        val desiredRoute = json.optString(
            "route",
            if (autoCheckIn) "/check-in-out" else "/"
        ).trim().ifEmpty { "/" }

        if (user.isEmpty() || pass.isEmpty()) {
            return jsonResult(false, "Thiếu tên đăng nhập hoặc mật khẩu để đăng nhập")
        }

        val updated = AppConfig.saveSettings(
            context = appContext,
            username = user,
            password = pass,
            targetUrl = target,
            mdnsHostname = prev.mdnsHostname,
            autoEnterCheckInPage = autoCheckIn
        )

        // Đăng nhập nhanh qua API chính thức để lấy ngay thông tin trường & token phục vụ RFID tức thì
        val directResult = authenticateBackendDirect(user, pass, updated.targetUrl)
        pendingNavigateRoute = desiredRoute

        mainHandler.post {
            val listener = activeListener
            if (currentScreen == RemoteScreen.WEBVIEW && listener != null) {
                listener.onRemoteDirectLogin(user, pass, updated.targetUrl, desiredRoute)
            } else {
                val intent = Intent(appContext, WebViewActivity::class.java).apply {
                    putExtra("username", user)
                    putExtra("password", pass)
                    putExtra("target_url", updated.targetUrl)
                    putExtra("initial_route", desiredRoute)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                appContext.startActivity(intent)
            }
        }

        val okDirect = directResult.optBoolean("ok", false)
        addLog("Đăng nhập từ localhost/LAN ($user @ ${updated.targetUrl}) -> ${directResult.optString("message")}")
        return JSONObject().apply {
            put("ok", true)
            put("apiAuthenticated", okDirect)
            put("schoolName", directResult.optString("schoolName", webSchoolName))
            put("accountRole", directResult.optString("accountRole", webAccountRole))
            put("targetUrl", updated.targetUrl)
            put("route", desiredRoute)
            put(
                "message",
                if (okDirect) {
                    "Đăng nhập thành công (${directResult.optString("schoolName").ifEmpty { user }}) và đang mở $desiredRoute"
                } else {
                    "Đã gửi lệnh đăng nhập tài khoản $user trên trình duyệt Kiosk"
                }
            )
        }
    }

    override fun handleWebLogout(): JSONObject {
        AppConfig.saveWebAuthToken(appContext, "")
        AppConfig.setLastWebUser(appContext, "")
        updateWebSessionSnapshot(
            loggedIn = false,
            token = null,
            schoolName = "",
            accountName = "",
            accountRole = "",
            route = "/"
        )
        loginState = "LOGGED_OUT"
        addLog("Đã đăng xuất phiên điểm danh từ localhost/LAN")
        mainHandler.post {
            val listener = activeListener
            if (currentScreen == RemoteScreen.WEBVIEW && listener != null) {
                listener.onRemoteCommand("logout_web")
            }
        }
        return jsonResult(true, "Đã đăng xuất tài khoản điểm danh và xóa phiên trình duyệt")
    }

    override fun handleWebNavigate(json: JSONObject): JSONObject {
        val rawRoute = json.optString("route", "").trim()
        val rawUrl = json.optString("url", "").trim()
        val destination = rawRoute.ifEmpty { rawUrl }
        if (destination.isEmpty()) {
            return jsonResult(false, "Thiếu trường route hoặc url để điều hướng")
        }

        val settings = AppConfig.load(appContext)
        pendingNavigateRoute = destination
        addLog("Điều hướng từ localhost/LAN tới: $destination")

        mainHandler.post {
            val listener = activeListener
            if (currentScreen == RemoteScreen.WEBVIEW && listener != null) {
                listener.onRemoteNavigate(destination)
            } else {
                val intent = Intent(appContext, WebViewActivity::class.java).apply {
                    putExtra("username", settings.username)
                    putExtra("password", settings.password)
                    putExtra("target_url", settings.targetUrl)
                    putExtra("initial_route", destination)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                appContext.startActivity(intent)
            }
        }
        return JSONObject().apply {
            put("ok", true)
            put("route", destination)
            put("message", "Đang chuyển màn hình Kiosk tới $destination")
        }
    }

    override fun handleWebAction(json: JSONObject): JSONObject {
        val listener = activeListener
        if (currentScreen != RemoteScreen.WEBVIEW || listener == null) {
            return jsonResult(false, "Thiết bị chưa mở màn hình WebView. Hãy bấm 'Mở Điểm Danh' trước.")
        }
        val ref = AtomicReference<JSONObject>()
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                ref.set(listener.onRemoteWebAction(json) ?: jsonResult(false, "Hành động không được hỗ trợ"))
            } catch (e: Exception) {
                ref.set(jsonResult(false, e.message ?: "Lỗi thực thi hành động trên WebView"))
            } finally {
                latch.countDown()
            }
        }
        latch.await(4, TimeUnit.SECONDS)
        return ref.get() ?: jsonResult(false, "Hết thời gian chờ phản hồi từ WebView")
    }

    override fun captureScreenJpeg(): ByteArray? {
        val listener = activeListener ?: return null
        val ref = AtomicReference<ByteArray?>()
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                ref.set(listener.onCaptureScreenshotJpeg())
            } catch (_: Exception) {
                ref.set(null)
            } finally {
                latch.countDown()
            }
        }
        latch.await(3, TimeUnit.SECONDS)
        return ref.get()
    }

    override fun handleRfidScan(cardId: String, mode: String, source: String): JSONObject {
        val cleanId = RfidScannerBuffer.normalizeCardId(cardId)
        if (cleanId.isEmpty()) {
            return jsonResult(false, "Mã thẻ RFID không hợp lệ")
        }

        // 1. Nếu WebView đang hiển thị và mode != "direct_only", ưu tiên thực thi qua WebView để hiển thị thông báo lên màn hình TV
        val listener = activeListener
        if (currentScreen == RemoteScreen.WEBVIEW && listener != null && mode != "backend_only") {
            val latch = CountDownLatch(1)
            val resultRef = AtomicReference<Triple<Int, Boolean, String>?>(null)
            val accepted = AtomicBoolean(false)

            mainHandler.post {
                val handled = listener.onRemoteRfidScan(cleanId, mode, source) { status, ok, msg ->
                    resultRef.set(Triple(status, ok, msg))
                    latch.countDown()
                }
                accepted.set(handled)
                if (!handled) latch.countDown()
            }

            latch.await(6, TimeUnit.SECONDS)
            val webRes = resultRef.get()
            if (accepted.get() && webRes != null) {
                val (httpStatus, ok, msg) = webRes
                val rec = rfidBuffer.recordScan(cleanId, source, httpStatus, ok, msg, activeRoute)
                addLog("RFID [$source] $cleanId -> ${if (ok) "THÀNH CÔNG" else "HTTP $httpStatus"}: $msg")
                return rec.toJson().put("executedVia", "webview")
            }
        }

        // 2. Thực thi trực tiếp tới API điểm danh bằng Bearer token (hoặc tự đăng nhập lấy token nếu đã cấu hình tài khoản)
        val direct = sendCheckInOutDirect(cleanId)
        val httpStatus = direct.optInt("httpStatus", 0)
        val ok = direct.optBoolean("ok", false)
        val msg = direct.optString("message", "Không có phản hồi")
        val rec = rfidBuffer.recordScan(cleanId, source, httpStatus, ok, msg, activeRoute)
        addLog("RFID [$source/API] $cleanId -> ${if (ok) "THÀNH CÔNG" else "HTTP $httpStatus"}: $msg")
        return rec.toJson().put("executedVia", "direct_api")
    }

    override fun handleRfidHistory(limit: Int): JSONObject {
        return JSONObject().apply {
            put("ok", true)
            put("totalScans", rfidBuffer.totalScans)
            put("successCount", rfidBuffer.successCount)
            put("errorCount", rfidBuffer.errorCount)
            put("history", rfidBuffer.historyJsonArray(limit))
        }
    }

    override fun handleRfidClear(): JSONObject {
        rfidBuffer.clearHistory()
        addLog("Đã xóa lịch sử quẹt thẻ RFID")
        return jsonResult(true, "Đã xóa lịch sử quẹt thẻ RFID")
    }

    override fun handleRfidKnownCards(): JSONObject {
        val settings = AppConfig.load(appContext)
        val apiBase = RfidScannerBuffer.resolveApiBaseUrl(activeWebUrl.ifBlank { settings.targetUrl })
        var token = AppConfig.getWebAuthToken(appContext)
        if (token.isEmpty() && settings.hasCredentials()) {
            val authRes = authenticateBackendDirect(settings.username, settings.password, settings.targetUrl)
            token = authRes.optString("accessToken", "")
        }
        if (token.isEmpty()) {
            return jsonResult(false, "Chưa đăng nhập tài khoản nhà trường. Hãy đăng nhập trước để tải danh sách mã thẻ.")
        }

        return try {
            val conn = (URL("$apiBase/check-in-out/ma-the").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 6_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
            conn.disconnect()

            if (code !in 200..299) {
                return jsonResult(false, "Máy chủ trả về mã HTTP $code khi tải danh sách thẻ")
            }
            val parsed = JSONObject(body)
            val dataObj = parsed.optJSONObject("data")
            val arr = dataObj?.optJSONArray("maThe") ?: parsed.optJSONArray("maThe") ?: JSONArray()
            val cardsOut = JSONArray()
            val maxPreview = minOf(arr.length(), 200)
            for (i in 0 until maxPreview) {
                cardsOut.put(arr.optString(i))
            }
            JSONObject().apply {
                put("ok", true)
                put("totalCards", arr.length())
                put("cards", cardsOut)
                put("message", "Đã tải ${arr.length()} mã thẻ RFID từ hệ thống")
            }
        } catch (e: Exception) {
            jsonResult(false, "Không tải được danh sách mã thẻ: ${e.message ?: "Lỗi mạng"}")
        }
    }

    private fun authenticateBackendDirect(username: String, password: String, targetUrl: String): JSONObject {
        val apiBase = RfidScannerBuffer.resolveApiBaseUrl(targetUrl)
        return try {
            val payload = JSONObject().apply {
                put("username", username)
                put("password", password)
            }.toString()

            val conn = (URL("$apiBase/auth/login").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6_000
                readTimeout = 6_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
            conn.disconnect()

            val json = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
            val data = json.optJSONObject("data")
            val token = data?.optString("accessToken", "") ?: ""
            if (code in 200..299 && token.isNotEmpty()) {
                AppConfig.saveWebAuthToken(appContext, token)
                var schoolName = ""
                var role = data?.optJSONObject("account")?.optString("type", "") ?: ""
                // Lấy thông tin trường chi tiết từ /auth/me
                runCatching {
                    val meConn = (URL("$apiBase/auth/me").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5_000
                        readTimeout = 5_000
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                    if (meConn.responseCode in 200..299) {
                        val meBody = meConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val meData = JSONObject(meBody).optJSONObject("data")
                        schoolName = meData?.optJSONObject("school")?.optString("name", "") ?: ""
                        role = meData?.optString("type", role) ?: role
                    }
                    meConn.disconnect()
                }
                updateWebSessionSnapshot(
                    loggedIn = true,
                    token = token,
                    schoolName = schoolName,
                    accountName = username,
                    accountRole = role,
                    route = activeRoute
                )
                loginState = "LOGGED_IN"
                JSONObject().apply {
                    put("ok", true)
                    put("accessToken", token)
                    put("schoolName", schoolName)
                    put("accountRole", role)
                    put("message", json.optString("message", "Đăng nhập thành công"))
                }
            } else {
                JSONObject().apply {
                    put("ok", false)
                    put("message", json.optString("message", "Sai tài khoản hoặc mật khẩu (HTTP $code)"))
                }
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("message", "Lỗi kết nối máy chủ xác thực: ${e.message ?: "Không xác định"}")
            }
        }
    }

    private fun sendCheckInOutDirect(cardId: String): JSONObject {
        val settings = AppConfig.load(appContext)
        val apiBase = RfidScannerBuffer.resolveApiBaseUrl(activeWebUrl.ifBlank { settings.targetUrl })
        var token = AppConfig.getWebAuthToken(appContext)

        if (token.isEmpty() && settings.hasCredentials()) {
            val authRes = authenticateBackendDirect(settings.username, settings.password, settings.targetUrl)
            token = authRes.optString("accessToken", "")
        }
        if (token.isEmpty()) {
            return JSONObject().apply {
                put("ok", false)
                put("httpStatus", 401)
                put("message", "Chưa có phiên đăng nhập. Hãy đăng nhập trên localhost:8888 trước khi quẹt thẻ.")
            }
        }

        fun postOnce(bearer: String): Triple<Int, Boolean, String> {
            val conn = (URL("$apiBase/check-in-out/$cardId").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6_000
                readTimeout = 6_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $bearer")
            }
            conn.outputStream.use { }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
            conn.disconnect()
            val parsed = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            val msg = parsed.optString("message", "").ifBlank {
                if (code in 200..299) "Ghi nhận quẹt thẻ thành công" else "Lỗi máy chủ (HTTP $code)"
            }
            return Triple(code, code in 200..299, msg)
        }

        return try {
            var (code, ok, msg) = postOnce(token)
            // Nếu token hết hạn (401) và đã có tài khoản lưu sẵn -> tự động làm mới phiên 1 lần
            if (code == 401 && settings.hasCredentials()) {
                val refreshed = authenticateBackendDirect(settings.username, settings.password, settings.targetUrl)
                val newToken = refreshed.optString("accessToken", "")
                if (newToken.isNotEmpty()) {
                    val retry = postOnce(newToken)
                    code = retry.first
                    ok = retry.second
                    msg = retry.third
                }
            }
            JSONObject().apply {
                put("ok", ok)
                put("httpStatus", code)
                put("message", msg)
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("httpStatus", 0)
                put("message", "Lỗi kết nối khi gửi mã thẻ: ${e.message ?: "Mất mạng"}")
            }
        }
    }

    override fun handleRemoteCommand(action: String): JSONObject {
        addLog("Nhận lệnh điều khiển: $action")
        val settings = AppConfig.load(appContext)

        return when (action) {
            "connect" -> {
                if (!settings.hasCredentials()) {
                    jsonResult(false, "Chưa có tài khoản hoặc mật khẩu. Hãy lưu thông tin trước.")
                } else {
                    mainHandler.post { dispatchConnectCommand(settings) }
                    jsonResult(true, "Đang chuyển sang màn hình Điểm danh...")
                }
            }

            "open_checkin" -> {
                handleWebNavigate(JSONObject().put("route", "/check-in-out"))
            }

            "open_home" -> {
                handleWebNavigate(JSONObject().put("route", "/"))
            }

            "open_settings" -> {
                mainHandler.post {
                    val listener = activeListener
                    if (currentScreen == RemoteScreen.WEBVIEW && listener != null) {
                        listener.onRemoteCommand("open_settings")
                    } else if (currentScreen == RemoteScreen.MAIN && listener != null) {
                        listener.onRemoteCommand("stop_countdown")
                    } else {
                        val i = Intent(appContext, MainActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_PAUSE_AUTO_CONNECT, true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        appContext.startActivity(i)
                    }
                }
                jsonResult(true, "Đang mở màn hình Cài đặt")
            }

            "reload_webview", "relogin", "logout_web", "stop_countdown", "resume_countdown" -> {
                mainHandler.post {
                    val listener = activeListener
                    if (listener != null) {
                        listener.onRemoteCommand(action)
                    } else if (action == "reload_webview" || action == "relogin") {
                        dispatchConnectCommand(settings)
                    }
                }
                val msg = when (action) {
                    "reload_webview" -> "Đã gửi lệnh tải lại trang WebView"
                    "relogin" -> "Đã gửi lệnh đăng xuất và đăng nhập lại"
                    "logout_web" -> "Đã gửi lệnh đăng xuất phiên điểm danh"
                    "resume_countdown" -> "Đã tiếp tục bộ đếm tự động kết nối"
                    else -> "Đã tạm dừng bộ đếm tự động kết nối"
                }
                jsonResult(true, msg)
            }

            else -> jsonResult(false, "Lệnh không được hỗ trợ: $action")
        }
    }

    override fun handleUpdateCheck(): JSONObject {
        checkGitHubUpdateAsync(autoInstallIfAvailable = false)
        return jsonResult(true, "Đang kiểm tra bản cập nhật mới nhất trên GitHub...")
    }

    override fun handleUpdateInstall(): JSONObject {
        downloadAndInstallLatestAsync()
        return jsonResult(true, "Đang tải và khởi chạy cài đặt bản cập nhật từ GitHub...")
    }

    private fun dispatchConnectCommand(settings: AppSettings) {
        val listener = activeListener
        if (listener != null) {
            listener.onRemoteCommand("connect")
        } else {
            val intent = Intent(appContext, WebViewActivity::class.java).apply {
                putExtra("username", settings.username)
                putExtra("password", settings.password)
                putExtra("target_url", settings.targetUrl)
                if (settings.autoEnterCheckInPage) {
                    putExtra("initial_route", "/check-in-out")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            appContext.startActivity(intent)
        }
    }

    private fun notifyEndpointChanged() {
        val (mdnsUrl, ipUrl) = getControlUrls()
        mainHandler.post {
            activeListener?.onLanEndpointChanged(mdnsUrl, ipUrl)
        }
    }

    private fun jsonResult(ok: Boolean, message: String): JSONObject =
        JSONObject().apply {
            put("ok", ok)
            put("message", message)
        }
}
