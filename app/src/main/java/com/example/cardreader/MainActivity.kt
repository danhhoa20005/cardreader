package com.example.cardreader

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : ComponentActivity(), RemoteScreenListener {

    private lateinit var tvVersionBadge: TextView
    private lateinit var ivNetworkIcon: ImageView
    private lateinit var tvNetworkBadge: TextView
    private lateinit var tvLanLocalUrl: TextView
    private lateinit var tvLanIpUrl: TextView
    private lateinit var layoutWarning: LinearLayout
    private lateinit var tvWarning: TextView
    private lateinit var layoutCountdown: LinearLayout
    private lateinit var tvCountdown: TextView
    private lateinit var progressCountdown: ProgressBar
    private lateinit var btnPauseCountdown: Button
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnTogglePassword: ImageButton
    private lateinit var btnSaveAndConnect: Button
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnToggleAdvanced: Button
    private lateinit var tvUpdateStatus: TextView
    private lateinit var layoutAdvancedSettings: LinearLayout
    private lateinit var etTargetUrl: EditText
    private lateinit var etMdnsHostname: EditText
    private lateinit var etAdminPin: EditText
    private lateinit var etGithubRepo: EditText

    private var countDownTimer: CountDownTimer? = null
    private var isUserInteracting = false
    private var isPasswordVisible = false
    private var suppressWatchers = false
    private var isNetworkConnected = false
    private var isUpdateFlowActive = false
    private var isOverlayDialogShowing = false
    private var waitingOverlaySettingsReturn = false
    private var networkMonitor: NetworkMonitor? = null

    private var pendingInstallApk: File? = null
    private var downloadDialog: AlertDialog? = null

    private val installPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val apk = pendingInstallApk
            if (apk != null && apk.exists() && packageManager.canRequestPackageInstalls()) {
                pendingInstallApk = null
                launchApkInstaller(apk)
            } else if (apk != null) {
                isUpdateFlowActive = false
                tvUpdateStatus.text = "Chưa bật quyền cài đặt APK. Hãy kiểm tra lại khi bật quyền xong."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RemoteControlHub.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        bindViews()

        val settings = AppConfig.load(this)
        tvVersionBadge.text = "v${BuildConfig.VERSION_NAME}"
        populateFields(settings)
        updateSummaryText(settings)

        if (settings.hasCredentials()) {
            btnSaveAndConnect.requestFocus()
        } else {
            etUsername.requestFocus()
        }

        val loginError = intent.getStringExtra("login_error")
        val pauseAutoConnect = intent.getBooleanExtra(EXTRA_PAUSE_AUTO_CONNECT, false)
        if (pauseAutoConnect) {
            isUserInteracting = true
        }

        if (!loginError.isNullOrEmpty()) {
            RemoteControlHub.lastError = loginError
        }

        networkMonitor = NetworkMonitor(this, allowLanWithoutInternet = true) { connected ->
            isNetworkConnected = connected
            updateNetworkBadge(connected)
            if (connected) {
                if (tvWarning.text.toString().contains("Mất kết nối mạng")) {
                    layoutWarning.visibility = View.GONE
                }
                val currentSettings = AppConfig.load(this)
                if (currentSettings.hasCredentials() && !isUserInteracting && !isUpdateFlowActive && !isFinishing) {
                    layoutCountdown.visibility = View.VISIBLE
                    startCountdown(currentSettings)
                }
            } else {
                showWarning("Mất kết nối mạng (Wi-Fi/LAN). Vui lòng kiểm tra dây mạng hoặc Wi-Fi.")
                countDownTimer?.cancel()
                RemoteControlHub.countdownSeconds = -1
                if (AppConfig.load(this).hasCredentials()) {
                    layoutCountdown.visibility = View.VISIBLE
                    progressCountdown.progress = 0
                    tvCountdown.text = "Đã tạm dừng đếm ngược (Mất kết nối mạng)"
                }
            }
        }
        isNetworkConnected = networkMonitor?.isCurrentlyConnected() ?: false
        updateNetworkBadge(isNetworkConnected)
        networkMonitor?.register()

        configureInitialState(settings, loginError, pauseAutoConnect)
        attachInteractionWatchers()
        setupListeners()

        RemoteControlHub.attachListener(RemoteScreen.MAIN, this)

        val pendingApkPath = intent.getStringExtra(EXTRA_PENDING_APK_PATH)
        if (!pendingApkPath.isNullOrBlank()) {
            val apk = File(pendingApkPath)
            if (apk.exists()) {
                isUpdateFlowActive = true
                countDownTimer?.cancel()
                installDownloadedApk(apk)
                return
            }
        }

        maybePromptOverlayPermission(settings)
        maybeAutoCheckUpdateOnLaunch(settings)
    }

    private fun bindViews() {
        tvVersionBadge = findViewById(R.id.tvVersionBadge)
        ivNetworkIcon = findViewById(R.id.ivNetworkIcon)
        tvNetworkBadge = findViewById(R.id.tvNetworkBadge)
        tvLanLocalUrl = findViewById(R.id.tvLanLocalUrl)
        tvLanIpUrl = findViewById(R.id.tvLanIpUrl)
        layoutWarning = findViewById(R.id.layoutWarning)
        tvWarning = findViewById(R.id.tvWarning)
        layoutCountdown = findViewById(R.id.layoutCountdown)
        tvCountdown = findViewById(R.id.tvCountdown)
        progressCountdown = findViewById(R.id.progressCountdown)
        btnPauseCountdown = findViewById(R.id.btnPauseCountdown)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        btnSaveAndConnect = findViewById(R.id.btnSaveAndConnect)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        btnToggleAdvanced = findViewById(R.id.btnToggleAdvanced)
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus)
        layoutAdvancedSettings = findViewById(R.id.layoutAdvancedSettings)
        etTargetUrl = findViewById(R.id.etTargetUrl)
        etMdnsHostname = findViewById(R.id.etMdnsHostname)
        etAdminPin = findViewById(R.id.etAdminPin)
        etGithubRepo = findViewById(R.id.etGithubRepo)
    }

    private fun updateNetworkBadge(connected: Boolean) {
        if (connected) {
            ivNetworkIcon.setImageResource(R.drawable.ic_wifi)
            ivNetworkIcon.setColorFilter(ContextCompat.getColor(this, R.color.status_ok_text))
            tvNetworkBadge.setTextColor(ContextCompat.getColor(this, R.color.status_ok_text))
            tvNetworkBadge.text = "Điều khiển từ xa qua LAN (Online)"
        } else {
            ivNetworkIcon.setImageResource(R.drawable.ic_wifi_off)
            ivNetworkIcon.setColorFilter(ContextCompat.getColor(this, R.color.status_error_text))
            tvNetworkBadge.setTextColor(ContextCompat.getColor(this, R.color.status_error_text))
            tvNetworkBadge.text = "Chưa có kết nối Wi-Fi / LAN"
        }
    }

    private fun showWarning(message: String) {
        tvWarning.text = message
        layoutWarning.visibility = View.VISIBLE
    }

    private fun populateFields(settings: AppSettings) {
        suppressWatchers = true
        etUsername.setText(settings.username)
        etPassword.setText(settings.password)
        etTargetUrl.setText(settings.targetUrl)
        etMdnsHostname.setText(settings.mdnsHostname)
        etAdminPin.setText(settings.adminPin)
        etGithubRepo.setText(settings.githubRepo)
        suppressWatchers = false
    }

    private fun updateSummaryText(settings: AppSettings) {
        tvUpdateStatus.text = "Trang điểm danh: ${settings.targetUrl}"
    }

    private fun configureInitialState(
        settings: AppSettings,
        loginError: String?,
        pauseAutoConnect: Boolean
    ) {
        if (settings.hasCredentials()) {
            layoutCountdown.visibility = View.VISIBLE

            when {
                !loginError.isNullOrEmpty() -> showWarning(loginError)
                !isNetworkConnected -> showWarning("Mất kết nối mạng (Wi-Fi/LAN). Vui lòng kiểm tra kết nối.")
                else -> layoutWarning.visibility = View.GONE
            }

            if (pauseAutoConnect) {
                RemoteControlHub.countdownSeconds = -1
                progressCountdown.progress = 0
                tvCountdown.text = "Đang tạm dừng theo lệnh từ LAN."
                btnPauseCountdown.text = "Tiếp tục"
            } else if (isNetworkConnected) {
                startCountdown(settings)
            } else {
                RemoteControlHub.countdownSeconds = -1
                progressCountdown.progress = 0
                tvCountdown.text = "Đang chờ kết nối mạng để đếm ngược..."
            }
        } else {
            RemoteControlHub.countdownSeconds = -1
            val msg = when {
                !loginError.isNullOrEmpty() -> loginError
                !isNetworkConnected -> "Mất kết nối mạng (Wi-Fi/LAN). Vui lòng kiểm tra kết nối."
                else -> "Chưa có tài khoản điểm danh. Nhập tại đây hoặc qua web http://${settings.mdnsHostname}:${BuildConfig.CONTROL_SERVER_PORT}"
            }
            showWarning(msg)
            layoutCountdown.visibility = View.GONE
        }
    }

    private fun attachInteractionWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressWatchers) {
                    stopCountdownByUser("Đã dừng đếm ngược. Nhấn 'Lưu và Kết Nối' khi chỉnh sửa xong.")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        etUsername.addTextChangedListener(watcher)
        etPassword.addTextChangedListener(watcher)
        etTargetUrl.addTextChangedListener(watcher)
        etMdnsHostname.addTextChangedListener(watcher)
        etAdminPin.addTextChangedListener(watcher)
        etGithubRepo.addTextChangedListener(watcher)
    }

    private fun setupListeners() {
        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val selection = etPassword.selectionEnd.coerceAtLeast(0)
            etPassword.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            etPassword.setSelection(selection.coerceAtMost(etPassword.text?.length ?: 0))
            btnTogglePassword.setImageResource(
                if (isPasswordVisible) R.drawable.ic_eye_off else R.drawable.ic_eye
            )
            btnTogglePassword.contentDescription =
                if (isPasswordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu"
        }

        btnPauseCountdown.setOnClickListener {
            val current = AppConfig.load(this)
            if (isUserInteracting) {
                isUserInteracting = false
                if (current.hasCredentials() && isNetworkConnected && !isUpdateFlowActive) {
                    btnPauseCountdown.text = "Tạm dừng"
                    startCountdown(current)
                }
            } else {
                stopCountdownByUser("Đã tạm dừng đếm ngược.")
            }
        }

        btnSaveAndConnect.setOnClickListener {
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                if (user.isEmpty()) {
                    etUsername.error = "Vui lòng nhập tên đăng nhập"
                    etUsername.requestFocus()
                } else {
                    etPassword.error = "Vui lòng nhập mật khẩu"
                    etPassword.requestFocus()
                }
                return@setOnClickListener
            }

            val prev = AppConfig.load(this)
            val newPin = etAdminPin.text.toString().trim()
            val updated = AppConfig.saveSettings(
                context = this,
                username = user,
                password = pass,
                targetUrl = etTargetUrl.text.toString(),
                mdnsHostname = etMdnsHostname.text.toString(),
                githubRepo = etGithubRepo.text.toString(),
                adminPin = newPin,
                clearAdminPin = newPin.isEmpty()
            )
            if (prev.mdnsHostname != updated.mdnsHostname) {
                RemoteControlHub.updateMdnsHostname(updated.mdnsHostname)
            }
            RemoteControlHub.clearWebSessionsIfNeeded(prev.adminPin, updated.adminPin)

            countDownTimer?.cancel()
            RemoteControlHub.countdownSeconds = -1
            navigateToWebView(updated)
        }

        btnToggleAdvanced.setOnClickListener {
            val show = layoutAdvancedSettings.visibility != View.VISIBLE
            layoutAdvancedSettings.visibility = if (show) View.VISIBLE else View.GONE
            btnToggleAdvanced.text = if (show) {
                "Thu gọn Cấu hình"
            } else {
                "Cấu hình LAN & GitHub"
            }
            if (show) {
                stopCountdownByUser("Đã dừng đếm ngược khi mở phần cấu hình.")
            }
        }

        btnCheckUpdate.setOnClickListener {
            val repoInput = AppConfig.normalizeGithubRepo(etGithubRepo.text.toString())
            AppConfig.saveGithubRepo(this, repoInput)
            suppressWatchers = true
            etGithubRepo.setText(repoInput)
            suppressWatchers = false

            if (!AppConfig.isValidGithubRepo(repoInput)) {
                layoutAdvancedSettings.visibility = View.VISIBLE
                btnToggleAdvanced.text = "Thu gọn Cấu hình"
                etGithubRepo.error = "Nhập đúng định dạng owner/repo"
                etGithubRepo.requestFocus()
                tvUpdateStatus.text = "Nhập GitHub repo (vd: username/cardreader) rồi bấm kiểm tra lại."
                stopCountdownByUser("Đã dừng đếm ngược để cấu hình GitHub.")
                return@setOnClickListener
            }

            if (!NetworkMonitor.hasInternetAccess(this)) {
                tvUpdateStatus.text = "Cần kết nối Internet để kiểm tra cập nhật GitHub."
                return@setOnClickListener
            }

            stopCountdownByUser("Đã dừng đếm ngược để kiểm tra cập nhật.")
            btnCheckUpdate.isEnabled = false
            tvUpdateStatus.text = "Đang kiểm tra bản mới trên GitHub ($repoInput)..."
            RemoteControlHub.triggerUpdateCheck(isAutoCheck = false) {
                btnCheckUpdate.isEnabled = true
                handleHubUpdateResult(isAutoCheck = false)
            }
        }
    }

    private fun stopCountdownByUser(message: String) {
        isUserInteracting = true
        countDownTimer?.cancel()
        RemoteControlHub.countdownSeconds = -1
        if (layoutCountdown.visibility == View.VISIBLE || AppConfig.load(this).hasCredentials()) {
            layoutCountdown.visibility = View.VISIBLE
            progressCountdown.progress = 0
            tvCountdown.text = message
            btnPauseCountdown.text = "Tiếp tục"
        }
    }

    private fun startCountdown(settings: AppSettings) {
        if (!isNetworkConnected || isUpdateFlowActive || isUserInteracting || isOverlayDialogShowing) return
        countDownTimer?.cancel()
        btnPauseCountdown.text = "Tạm dừng"
        progressCountdown.max = 30
        countDownTimer = object : CountDownTimer(30_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = ((millisUntilFinished + 999L) / 1000L).toInt()
                RemoteControlHub.countdownSeconds = seconds
                progressCountdown.progress = seconds.coerceIn(0, 30)
                tvCountdown.text = "Tự động kết nối sau ${seconds}s"
            }

            override fun onFinish() {
                RemoteControlHub.countdownSeconds = -1
                progressCountdown.progress = 0
                if (!isFinishing && isNetworkConnected && !isUpdateFlowActive && !isUserInteracting) {
                    navigateToWebView(AppConfig.load(this@MainActivity))
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        RemoteControlHub.attachListener(RemoteScreen.MAIN, this)
        if (waitingOverlaySettingsReturn) {
            waitingOverlaySettingsReturn = false
            val current = AppConfig.load(this)
            if (current.hasCredentials() && isNetworkConnected && !isUserInteracting && !isUpdateFlowActive) {
                layoutCountdown.visibility = View.VISIBLE
                startCountdown(current)
            }
        }
    }

    private fun navigateToWebView(settings: AppSettings) {
        countDownTimer?.cancel()
        RemoteControlHub.countdownSeconds = -1
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra(EXTRA_USERNAME, settings.username)
            putExtra(EXTRA_PASSWORD, settings.password)
            putExtra(EXTRA_TARGET_URL, settings.targetUrl)
        }
        startActivity(intent)
        finish()
    }

    private fun maybePromptOverlayPermission(settings: AppSettings) {
        if (Settings.canDrawOverlays(this) || settings.overlayPrompted) return

        isOverlayDialogShowing = true
        countDownTimer?.cancel()
        RemoteControlHub.countdownSeconds = -1
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Cấp quyền tự khởi động Android Box")
            .setMessage("Để ứng dụng tự động mở lại sau khi Android Box khởi động, vui lòng bật quyền 'Hiển thị trên các ứng dụng khác'.")
            .setPositiveButton("Mở Cài đặt") { _, _ ->
                isOverlayDialogShowing = false
                waitingOverlaySettingsReturn = true
                AppConfig.markOverlayPrompted(this)
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }.onFailure {
                    waitingOverlaySettingsReturn = false
                }
            }
            .setNegativeButton("Để sau") { _, _ ->
                isOverlayDialogShowing = false
                AppConfig.markOverlayPrompted(this)
                val current = AppConfig.load(this)
                if (current.hasCredentials() && isNetworkConnected && !isUserInteracting && !isUpdateFlowActive) {
                    startCountdown(current)
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun maybeAutoCheckUpdateOnLaunch(settings: AppSettings) {
        if (!AppConfig.isValidGithubRepo(settings.githubRepo)) return
        if (!NetworkMonitor.hasInternetAccess(this)) return
        RemoteControlHub.triggerUpdateCheck(isAutoCheck = true) {
            handleHubUpdateResult(isAutoCheck = true)
        }
    }

    private fun handleHubUpdateResult(isAutoCheck: Boolean) {
        if (isFinishing || isDestroyed) return
        val release = RemoteControlHub.latestRelease
        if (RemoteControlHub.updateState == "AVAILABLE" && release != null) {
            isUpdateFlowActive = true
            countDownTimer?.cancel()
            RemoteControlHub.countdownSeconds = -1
            tvCountdown.text = "Đã tìm thấy bản cập nhật ${release.tagName}"
            tvUpdateStatus.text = "Có bản mới: ${release.tagName} (${release.assetName})"
            showUpdateConfirmDialog(release)
        } else if (!isAutoCheck) {
            tvUpdateStatus.text = RemoteControlHub.updateMessage
        }
    }

    private fun showUpdateConfirmDialog(release: ReleaseInfo) {
        val notes = release.releaseNotes.ifBlank { "Bản cập nhật mới từ GitHub Releases." }
        val sizeMb = if (release.fileSizeBytes > 0) {
            String.format(java.util.Locale.US, "%.1f MB", release.fileSizeBytes / (1024.0 * 1024.0))
        } else {
            "Không rõ dung lượng"
        }

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Cập nhật CardReader ${release.tagName}")
            .setMessage("Phiên bản hiện tại: v${BuildConfig.VERSION_NAME}\nPhiên bản mới: ${release.tagName} ($sizeMb)\n\n$notes")
            .setPositiveButton("Tải và Cài đặt") { _, _ ->
                startApkDownloadAndInstall(release)
            }
            .setNegativeButton("Bỏ qua") { _, _ ->
                isUpdateFlowActive = false
                val current = AppConfig.load(this)
                if (current.hasCredentials() && isNetworkConnected && !isUserInteracting) {
                    startCountdown(current)
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun startApkDownloadAndInstall(release: ReleaseInfo) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 24)
        }
        val statusText = TextView(this).apply {
            text = "Đang tải ${release.assetName}..."
            textSize = 14f
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            setPadding(0, 24, 0, 0)
        }
        container.addView(statusText)
        container.addView(progressBar)

        downloadDialog?.dismiss()
        downloadDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Đang tải bản cập nhật")
            .setView(container)
            .setCancelable(false)
            .show()

        RemoteControlHub.downloadAndPrepareInstall(
            release = release,
            onProgress = { percent ->
                progressBar.progress = percent
                statusText.text = "Đang tải ${release.assetName}: $percent%"
            },
            onReady = { apkFile ->
                downloadDialog?.dismiss()
                downloadDialog = null
                if (!isFinishing && !isDestroyed) {
                    installDownloadedApk(apkFile)
                }
            },
            onError = { errMsg ->
                downloadDialog?.dismiss()
                downloadDialog = null
                isUpdateFlowActive = false
                tvUpdateStatus.text = "Tải cập nhật thất bại: $errMsg"
            }
        )
    }

    private fun installDownloadedApk(apkFile: File) {
        if (!packageManager.canRequestPackageInstalls()) {
            pendingInstallApk = apkFile
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Cấp quyền cài đặt APK")
                .setMessage("Android yêu cầu bật quyền 'Cài đặt ứng dụng không xác định' cho CardReader để hoàn tất nâng cấp.")
                .setPositiveButton("Mở Cài đặt") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                    installPermissionLauncher.launch(intent)
                }
                .setNegativeButton("Hủy") { _, _ ->
                    pendingInstallApk = null
                    isUpdateFlowActive = false
                }
                .setCancelable(false)
                .show()
            return
        }
        launchApkInstaller(apkFile)
    }

    private fun launchApkInstaller(apkFile: File) {
        isUpdateFlowActive = false
        tvUpdateStatus.text = "Đang mở trình cài đặt APK..."
        runCatching {
            startActivity(GitHubUpdater.buildInstallIntent(this, apkFile))
        }.onFailure { err ->
            tvUpdateStatus.text = "Không thể mở trình cài đặt: ${err.message}"
        }
    }

    override fun onRemoteConfigUpdated(settings: AppSettings) {
        populateFields(settings)
        updateSummaryText(settings)
        layoutWarning.visibility = View.GONE
    }

    override fun onRemoteCommand(action: String) {
        when {
            action == "connect" -> {
                val current = AppConfig.load(this)
                if (current.hasCredentials()) {
                    navigateToWebView(current)
                }
            }
            action == "stop_countdown" -> {
                stopCountdownByUser("Đã dừng đếm ngược từ trang điều khiển LAN.")
            }
            action.startsWith("install_apk:") -> {
                val path = action.removePrefix("install_apk:")
                val apk = File(path)
                if (apk.exists()) {
                    isUpdateFlowActive = true
                    countDownTimer?.cancel()
                    installDownloadedApk(apk)
                }
            }
        }
    }

    override fun onLanEndpointChanged(localUrl: String, ipUrl: String?) {
        tvLanLocalUrl.text = localUrl
        tvLanIpUrl.text = if (!ipUrl.isNullOrBlank()) {
            "Hoặc truy cập trực tiếp: $ipUrl"
        } else {
            "Đang chờ địa chỉ IP từ mạng LAN..."
        }
    }

    override fun onHubUpdateStateChanged() {
        if (RemoteControlHub.updateState != "IDLE") {
            tvUpdateStatus.text = RemoteControlHub.updateMessage
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        downloadDialog?.dismiss()
        networkMonitor?.unregister()
        RemoteControlHub.detachListener(this)
    }

    companion object {
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_TARGET_URL = "target_url"
        const val EXTRA_PAUSE_AUTO_CONNECT = "pause_auto_connect"
        const val EXTRA_PENDING_APK_PATH = "pending_apk_path"
    }
}
