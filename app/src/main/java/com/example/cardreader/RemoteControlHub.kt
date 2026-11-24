package com.example.cardreader

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
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
    fun onHubUpdateStateChanged()
}

/**
 * Process-level coordinator that keeps the LAN Web Control Server (:8080) and mDNS
 * responder (`diemdanh.local`) running across Activity transitions on the Android TV Box.
 */
object RemoteControlHub : ControlServerDelegate {

    private val initialized = AtomicBoolean(false)
    private lateinit var appContext: Context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newCachedThreadPool()

    private var mdnsAdvertiser: MdnsAdvertiser? = null
    private var controlServer: LocalControlServer? = null
    private var lanMonitor: NetworkMonitor? = null

    @Volatile
    var currentScreen: RemoteScreen = RemoteScreen.MAIN
        private set

    @Volatile
    var countdownSeconds: Int = -1

    @Volatile
    var activeWebUrl: String = ""

    @Volatile
    var loginState: String = "IDLE"

    @Volatile
    var lastError: String = ""

    @Volatile
    var updateState: String = "IDLE"

    @Volatile
    var updateMessage: String = "Chưa kiểm tra cập nhật."

    @Volatile
    var latestRelease: ReleaseInfo? = null

    @Volatile
    var downloadProgress: Int = 0

    @Volatile
    var currentLanIp: String? = null
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
        val stamp = synchronized(timeFormat) { timeFormat.format(Date()) }
        synchronized(logs) {
            if (logs.size >= 60) logs.removeFirst()
            logs.addLast("[$stamp] $message")
        }
    }

    fun triggerUpdateCheck(isAutoCheck: Boolean, onDone: (() -> Unit)? = null) {
        val settings = AppConfig.load(appContext)
        val repo = settings.githubRepo
        if (!AppConfig.isValidGithubRepo(repo)) {
            if (!isAutoCheck) {
                updateState = "ERROR"
                updateMessage = "Chưa cấu hình GitHub repo (owner/repo)."
                notifyUpdateStateChanged()
            }
            onDone?.invoke()
            return
        }
        if (!NetworkMonitor.hasInternetAccess(appContext)) {
            if (!isAutoCheck) {
                updateState = "ERROR"
                updateMessage = "Cần kết nối Internet để kiểm tra GitHub Releases."
                notifyUpdateStateChanged()
            }
            onDone?.invoke()
            return
        }

        updateState = "CHECKING"
        updateMessage = "Đang kiểm tra $repo..."
        notifyUpdateStateChanged()

        ioExecutor.execute {
            val result = GitHubUpdater.checkLatestRelease(repo, BuildConfig.VERSION_NAME)
            when (result) {
                is UpdateCheckResult.NotConfigured -> {
                    updateState = "IDLE"
                    updateMessage = "Chưa cấu hình GitHub repo."
                }
                is UpdateCheckResult.UpToDate -> {
                    latestRelease = null
                    updateState = "UP_TO_DATE"
                    updateMessage = "Đang ở bản mới nhất (${result.latestTag})."
                    if (!isAutoCheck) addLog("Kiểm tra cập nhật: đang ở bản mới nhất (${result.latestTag})")
                }
                is UpdateCheckResult.Error -> {
                    updateState = "ERROR"
                    updateMessage = result.message
                    if (!isAutoCheck) addLog("Lỗi kiểm tra cập nhật: ${result.message}")
                }
                is UpdateCheckResult.UpdateAvailable -> {
                    latestRelease = result.release
                    updateState = "AVAILABLE"
                    updateMessage = "Có bản mới ${result.release.tagName} (${result.release.assetName})"
                    addLog("Phát hiện bản mới trên GitHub: ${result.release.tagName}")
                }
            }
            notifyUpdateStateChanged()
            mainHandler.post { onDone?.invoke() }
        }
    }

    fun downloadAndPrepareInstall(
        release: ReleaseInfo,
        onProgress: ((Int) -> Unit)? = null,
        onReady: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        updateState = "DOWNLOADING"
        downloadProgress = 0
        updateMessage = "Đang tải ${release.assetName}"
        notifyUpdateStateChanged()
        addLog("Bắt đầu tải bản cập nhật ${release.tagName}...")

        ioExecutor.execute {
            try {
                val apkFile = GitHubUpdater.downloadApk(appContext, release) { percent ->
                    downloadProgress = percent
                    updateMessage = "Đang tải ${release.assetName}"
                    mainHandler.post { onProgress?.invoke(percent) }
                }
                updateState = "READY_TO_INSTALL"
                downloadProgress = 100
                updateMessage = "Đã tải xong ${release.assetName}, đang mở trình cài đặt..."
                addLog("Đã tải xong ${release.assetName}")
                notifyUpdateStateChanged()
                mainHandler.post { onReady(apkFile) }
            } catch (e: Exception) {
                val msg = e.message ?: "Lỗi tải APK"
                updateState = "ERROR"
                updateMessage = "Tải cập nhật thất bại: $msg"
                addLog("Tải cập nhật thất bại: $msg")
                notifyUpdateStateChanged()
                mainHandler.post { onError(msg) }
            }
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
            put("locked", !authorized)
            put("hasAdminPin", settings.hasAdminPin())

            if (authorized) {
                put("currentScreen", currentScreen.name)
                put("countdownSeconds", if (currentScreen == RemoteScreen.MAIN) countdownSeconds else -1)
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
                put("downloadProgress", downloadProgress)
                put("canInstallUpdate", latestRelease != null && updateState != "DOWNLOADING")

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
        val newRepo = json.optString("githubRepo", prev.githubRepo)
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
        addLog("Đã cập nhật cấu hình từ web LAN (User: ${updated.username}, URL: ${updated.targetUrl})")

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
                if (applyAndConnect) "Đã lưu cấu hình và đang áp dụng vào WebView" else "Đã lưu cấu hình lên TV Box"
            )
        }
    }

    override fun handleRemoteCommand(action: String): JSONObject {
        addLog("Nhận lệnh điều khiển từ LAN: $action")
        val settings = AppConfig.load(appContext)

        when (action) {
            "connect" -> {
                if (!settings.hasCredentials()) {
                    return JSONObject().apply {
                        put("ok", false)
                        put("message", "Chưa có tài khoản hoặc mật khẩu. Hãy nhập trước.")
                    }
                }
                mainHandler.post { dispatchConnectCommand(settings) }
                return jsonResult(true, "Đang chuyển TV Box sang WebView...")
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
                return jsonResult(true, "Đang mở màn hình Cài đặt trên TV Box")
            }

            "reload_webview", "relogin", "stop_countdown" -> {
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
                    "relogin" -> "Đã gửi lệnh đăng nhập lại"
                    else -> "Đã dừng bộ đếm tự động kết nối"
                }
                return jsonResult(true, msg)
            }

            "check_update" -> {
                triggerUpdateCheck(isAutoCheck = false)
                return jsonResult(true, "Đang kiểm tra bản mới trên GitHub...")
            }

            "install_update" -> {
                val release = latestRelease ?: return jsonResult(false, "Chưa có bản cập nhật nào sẵn sàng.")
                if (updateState == "DOWNLOADING") {
                    return jsonResult(false, "Đang tải bản cập nhật, vui lòng đợi...")
                }
                downloadAndPrepareInstall(
                    release = release,
                    onReady = { apkFile ->
                        val listener = activeListener
                        if (listener != null && currentScreen == RemoteScreen.MAIN) {
                            listener.onRemoteCommand("install_apk:${apkFile.absolutePath}")
                        } else if (appContext.packageManager.canRequestPackageInstalls()) {
                            runCatching {
                                appContext.startActivity(GitHubUpdater.buildInstallIntent(appContext, apkFile))
                            }.onFailure { err ->
                                addLog("Không mở được trình cài đặt APK: ${err.message}")
                            }
                        } else {
                            // Bring MainActivity forward to ask for Unknown Sources permission
                            val i = Intent(appContext, MainActivity::class.java).apply {
                                putExtra(MainActivity.EXTRA_PENDING_APK_PATH, apkFile.absolutePath)
                                putExtra(MainActivity.EXTRA_PAUSE_AUTO_CONNECT, true)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            appContext.startActivity(i)
                        }
                    },
                    onError = {}
                )
                return jsonResult(true, "Đang tải APK ${release.tagName} về TV Box...")
            }

            else -> return jsonResult(false, "Lệnh không được hỗ trợ: $action")
        }
    }

    private fun dispatchConnectCommand(settings: AppSettings) {
        val listener = activeListener
        if (listener != null) {
            listener.onRemoteCommand("connect")
        } else {
            val intent = Intent(appContext, WebViewActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_USERNAME, settings.username)
                putExtra(MainActivity.EXTRA_PASSWORD, settings.password)
                putExtra(MainActivity.EXTRA_TARGET_URL, settings.targetUrl)
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

    private fun notifyUpdateStateChanged() {
        mainHandler.post {
            activeListener?.onHubUpdateStateChanged()
        }
    }

    private fun jsonResult(ok: Boolean, message: String): JSONObject =
        JSONObject().apply {
            put("ok", ok)
            put("message", message)
        }
}
