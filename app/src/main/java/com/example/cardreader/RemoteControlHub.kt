package com.example.cardreader

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class RemoteScreen {
    MAIN,
    WEBVIEW
}

interface RemoteScreenListener {
    fun onRemoteConfigUpdated(settings: AppSettings)
    fun onRemoteCommand(action: String)
    fun onLanEndpointChanged(localUrl: String, ipUrl: String?)
    fun onUpdateStatusChanged(state: String, message: String, progress: Int) {}
}

/**
 * Điều phối viên cấp tiến trình giữ Web Control Server cổng 8888, mDNS (`diemdanh.local`)
 * và trình cập nhật OTA qua GitHub Releases hoạt động xuyên suốt giữa các màn hình.
 */
object RemoteControlHub : ControlServerDelegate {

    private val initialized = AtomicBoolean(false)
    private val updateBusy = AtomicBoolean(false)
    private lateinit var appContext: Context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateExecutor = Executors.newSingleThreadExecutor()

    private var mdnsAdvertiser: MdnsAdvertiser? = null
    private var controlServer: LocalControlServer? = null
    private var lanMonitor: NetworkMonitor? = null

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
    var loginState: String = "IDLE"

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

    private val logs = ArrayDeque<String>(64)
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

    fun addLog(message: String) {
        val sanitized = message.replace(Regex("(?i)(password|pin|token)=([^&\\s]+)"), "$1=***")
        val stamp = synchronized(timeFormat) { timeFormat.format(Date()) }
        synchronized(logs) {
            if (logs.size >= 60) logs.removeFirst()
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
                put("mdnsHostname", settings.mdnsHostname)
                put("githubRepo", settings.githubRepo)
                put("lanIp", lanIp ?: "")
                put("controlUrlLocal", mdnsUrl)
                put("controlUrlIp", ipUrl ?: "")
                put("networkConnected", NetworkMonitor.hasNetworkAccess(appContext, allowLanWithoutInternet = true))
                put("internetConnected", NetworkMonitor.hasInternetAccess(appContext))
                put("loginState", loginState)
                put("lastError", lastError)

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

        val updated = AppConfig.saveSettings(
            context = appContext,
            username = newUsername,
            password = rawPass,
            targetUrl = newTargetUrl,
            mdnsHostname = newMdns,
            githubRepo = newRepo,
            adminPin = pinToSave,
            clearAdminPin = clearPin
        )

        if (prev.mdnsHostname != updated.mdnsHostname) {
            updateMdnsHostname(updated.mdnsHostname)
        }
        clearWebSessionsIfNeeded(prev.adminPin, updated.adminPin)
        addLog("Đã cập nhật cấu hình từ web LAN (Tài khoản: ${updated.username.ifEmpty { "trống" }}, URL: ${updated.targetUrl})")

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
                if (applyAndConnect) "Đã lưu cấu hình và đang mở WebView" else "Đã lưu cấu hình lên thiết bị"
            )
        }
    }

    override fun handleRemoteCommand(action: String): JSONObject {
        addLog("Nhận lệnh điều khiển từ LAN: $action")
        val settings = AppConfig.load(appContext)

        return when (action) {
            "connect" -> {
                if (!settings.hasCredentials()) {
                    jsonResult(false, "Chưa có tài khoản hoặc mật khẩu. Hãy lưu thông tin trước.")
                } else {
                    mainHandler.post { dispatchConnectCommand(settings) }
                    jsonResult(true, "Đang chuyển sang màn hình WebView...")
                }
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

            "reload_webview", "relogin", "stop_countdown", "resume_countdown" -> {
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
