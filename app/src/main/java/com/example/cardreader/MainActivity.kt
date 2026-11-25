package com.example.cardreader

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import java.io.ByteArrayOutputStream
import java.net.URI

/**
 * Màn hình cấu hình (Screen 1) đạt chuẩn UI/UX Pro Max:
 * - Tương thích Dọc (Portrait), Ngang 2 cột (Landscape) trên điện thoại và Màn hình lớn / D-Pad trên TV Box.
 * - Hỗ trợ chọn nhanh hệ thống (diemdanhhs.com / dontrahs.com), điều khiển LAN cổng 8888 và cập nhật OTA qua GitHub.
 */
class MainActivity : FragmentActivity(), RemoteScreenListener {

    private lateinit var tvTitle: TextView
    private lateinit var tvVersionSubtitle: TextView
    private lateinit var tvLanInfo: TextView
    private lateinit var btnDomainDiemDanh: Button
    private lateinit var btnDomainDonTra: Button
    private lateinit var layoutWarning: View
    private lateinit var tvWarning: TextView
    private lateinit var layoutCountdown: View
    private lateinit var tvCountdown: TextView
    private lateinit var btnToggleCountdown: Button
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnTogglePassword: ImageButton
    private lateinit var btnSaveAndConnect: Button
    private lateinit var btnGithubUpdate: Button

    private var countDownTimer: CountDownTimer? = null
    private var isUserInteracting = false
    private var isPasswordVisible = false
    private var suppressWatchers = false
    private var isNetworkConnected = false
    private var networkMonitor: NetworkMonitor? = null

    companion object {
        const val EXTRA_PAUSE_AUTO_CONNECT = "pause_auto_connect"
        private const val STATE_USER_INTERACTING = "state_user_interacting"
        private const val STATE_PASSWORD_VISIBLE = "state_password_visible"
        private const val URL_DIEM_DANH = "http://diemdanhhs.com"
        private const val URL_DON_TRA = "https://dontrahs.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RemoteControlHub.init(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContentView(R.layout.activity_main)
        applyEdgeToEdgeInsets()

        tvTitle = findViewById(R.id.tvTitle)
        tvVersionSubtitle = findViewById(R.id.tvVersionSubtitle)
        tvLanInfo = findViewById(R.id.tvLanInfo)
        btnDomainDiemDanh = findViewById(R.id.btnDomainDiemDanh)
        btnDomainDonTra = findViewById(R.id.btnDomainDonTra)
        layoutWarning = findViewById(R.id.layoutWarning)
        tvWarning = findViewById(R.id.tvWarning)
        layoutCountdown = findViewById(R.id.layoutCountdown)
        tvCountdown = findViewById(R.id.tvCountdown)
        btnToggleCountdown = findViewById(R.id.btnToggleCountdown)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        btnSaveAndConnect = findViewById(R.id.btnSaveAndConnect)
        btnGithubUpdate = findViewById(R.id.btnGithubUpdate)

        val pauseAutoConnect = intent.getBooleanExtra(EXTRA_PAUSE_AUTO_CONNECT, false) ||
            (savedInstanceState?.getBoolean(STATE_USER_INTERACTING, false) == true)
        setPausedFlag(pauseAutoConnect)

        if (!Settings.canDrawOverlays(this) && !pauseAutoConnect && !AppConfig.isOverlayPrompted(this)) {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Quyền tự mở khi khởi động TV Box")
                .setMessage("Nếu cài trên Android TV Box cần tự chạy khi bật nguồn, hãy cấp quyền 'Hiển thị trên ứng dụng khác'. Trên điện thoại có thể bấm 'Bỏ qua'.")
                .setPositiveButton("Mở Cài đặt") { _, _ ->
                    AppConfig.markOverlayPrompted(this)
                    runCatching {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                }
                .setNegativeButton("Bỏ qua") { _, _ ->
                    AppConfig.markOverlayPrompted(this)
                }
                .setOnCancelListener {
                    AppConfig.markOverlayPrompted(this)
                }
                .show()
        }

        networkMonitor = NetworkMonitor(this, allowLanWithoutInternet = true) { connected ->
            isNetworkConnected = connected
            updateConnectButtonState(connected)
            if (connected) {
                if (tvWarning.text.toString().contains("Mất kết nối mạng")) {
                    hideWarning()
                }
                val current = AppConfig.load(this)
                if (current.hasCredentials() && !isUserInteracting && !isFinishing) {
                    startCountdown()
                }
            } else {
                showWarning("Mất kết nối mạng. Vui lòng kiểm tra dây LAN hoặc Wi-Fi.")
                countDownTimer?.cancel()
                countDownTimer = null
                RemoteControlHub.countdownSeconds = -1
                if (AppConfig.load(this).hasCredentials()) {
                    layoutCountdown.visibility = View.VISIBLE
                    tvCountdown.text = "Đã tạm dừng đếm ngược do mất kết nối mạng..."
                    updateCountdownToggleUi()
                }
            }
        }
        isNetworkConnected = networkMonitor?.isCurrentlyConnected() ?: false
        updateConnectButtonState(isNetworkConnected)
        if (isNetworkConnected) {
            btnSaveAndConnect.requestFocus()
        } else {
            etUsername.requestFocus()
        }
        networkMonitor?.register()

        val loginError = intent.getStringExtra("login_error")
        if (!loginError.isNullOrEmpty()) {
            RemoteControlHub.lastError = loginError
        }

        val settings = AppConfig.load(this)
        if (savedInstanceState == null) {
            populateFields(settings)
        } else if (savedInstanceState.getBoolean(STATE_PASSWORD_VISIBLE, false)) {
            applyPasswordVisibility(true)
        }
        updateDomainButtonsState(settings.targetUrl)
        updateLanBanner()

        if (settings.hasCredentials()) {
            layoutCountdown.visibility = View.VISIBLE
            updateCountdownToggleUi()

            if (!loginError.isNullOrEmpty()) {
                showWarning(loginError)
            } else if (!isNetworkConnected) {
                showWarning("Mất kết nối mạng. Vui lòng kiểm tra dây LAN hoặc Wi-Fi.")
            } else {
                hideWarning()
            }

            when {
                pauseAutoConnect -> {
                    RemoteControlHub.countdownSeconds = -1
                    tvCountdown.text = "Đã tạm dừng tự động kết nối. Bấm 'Tiếp tục' hoặc 'Lưu và Kết Nối'."
                }
                isNetworkConnected -> startCountdown()
                else -> {
                    RemoteControlHub.countdownSeconds = -1
                    tvCountdown.text = "Đang chờ kết nối mạng để tự động đếm ngược..."
                }
            }
        } else {
            RemoteControlHub.countdownSeconds = -1
            layoutCountdown.visibility = View.GONE
            when {
                !loginError.isNullOrEmpty() -> showWarning(loginError)
                !isNetworkConnected -> showWarning("Mất kết nối mạng. Vui lòng kiểm tra dây LAN hoặc Wi-Fi.")
                else -> showWarning(
                    "Chưa có thông tin đăng nhập. Nhập tại đây hoặc qua LAN http://${settings.mdnsHostname}:${BuildConfig.CONTROL_SERVER_PORT}"
                )
            }
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressWatchers && !isUserInteracting) {
                    pauseCountdown("Đã dừng đếm ngược. Nhấn 'Lưu và Kết Nối' khi hoàn tất chỉnh sửa.")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        etUsername.addTextChangedListener(textWatcher)
        etPassword.addTextChangedListener(textWatcher)

        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && btnSaveAndConnect.isEnabled) {
                btnSaveAndConnect.performClick()
                true
            } else {
                false
            }
        }

        btnDomainDiemDanh.setOnClickListener {
            selectSystemDomain(URL_DIEM_DANH)
        }
        btnDomainDonTra.setOnClickListener {
            selectSystemDomain(URL_DON_TRA)
        }

        btnTogglePassword.setOnClickListener {
            applyPasswordVisibility(!isPasswordVisible)
        }

        btnToggleCountdown.setOnClickListener {
            if (isUserInteracting) {
                resumeCountdown()
            } else {
                pauseCountdown("Đã tạm dừng đếm ngược. Bấm 'Tiếp tục' hoặc 'Lưu và Kết Nối'.")
            }
        }

        btnGithubUpdate.setOnClickListener {
            pauseCountdown("Đang kiểm tra bản cập nhật mới trên GitHub...")
            RemoteControlHub.checkGitHubUpdateAsync(autoInstallIfAvailable = true)
        }

        btnSaveAndConnect.setOnClickListener {
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                if (user.isEmpty()) etUsername.error = "Vui lòng nhập tên đăng nhập"
                if (pass.isEmpty()) etPassword.error = "Vui lòng nhập mật khẩu"
                return@setOnClickListener
            }

            val updated = AppConfig.saveCredentials(this, user, pass)
            countDownTimer?.cancel()
            countDownTimer = null
            RemoteControlHub.countdownSeconds = -1
            setPausedFlag(false)
            navigateToWebView(updated)
        }

        RemoteControlHub.attachListener(RemoteScreen.MAIN, this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_USER_INTERACTING, isUserInteracting)
        outState.putBoolean(STATE_PASSWORD_VISIBLE, isPasswordVisible)
    }

    override fun onResume() {
        super.onResume()
        RemoteControlHub.attachListener(RemoteScreen.MAIN, this)
        updateDomainButtonsState(AppConfig.load(this).targetUrl)
        updateLanBanner()
    }

    private fun selectSystemDomain(url: String) {
        val updated = AppConfig.saveTargetUrl(this, url)
        // Xóa user cũ để WebView làm mới sạch phiên khi chuyển hệ thống
        AppConfig.setLastWebUser(this, "")
        updateDomainButtonsState(updated.targetUrl)
        updateLanBanner()
        RemoteControlHub.addLog("Đã chọn máy chủ hệ thống: ${updated.targetUrl}")
    }

    private fun updateDomainButtonsState(targetUrl: String) {
        val lower = targetUrl.lowercase()
        val isDonTra = lower.contains("dontrahs.com")
        val isDiemDanh = lower.contains("diemdanhhs.com")
        btnDomainDiemDanh.isSelected = isDiemDanh
        btnDomainDonTra.isSelected = isDonTra
    }

    private fun applyEdgeToEdgeInsets() {
        val container = findViewById<View>(R.id.contentContainer) ?: return
        val baseLeft = container.paddingLeft
        val baseTop = container.paddingTop
        val baseRight = container.paddingRight
        val baseBottom = container.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            view.setPadding(
                baseLeft + insets.left,
                baseTop + insets.top,
                baseRight + insets.right,
                baseBottom + insets.bottom
            )
            windowInsets
        }
    }

    internal fun updateConnectButtonState(connected: Boolean) {
        btnSaveAndConnect.isEnabled = connected
        btnSaveAndConnect.alpha = if (connected) 1.0f else 0.55f
        btnSaveAndConnect.text = if (connected) "Lưu và Kết Nối" else "Đang chờ kết nối mạng..."
    }

    private fun applyPasswordVisibility(visible: Boolean) {
        isPasswordVisible = visible
        val selection = etPassword.selectionEnd.coerceAtLeast(0)
        val savedTypeface = etPassword.typeface
        suppressWatchers = true
        etPassword.inputType = if (visible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        etPassword.typeface = savedTypeface
        etPassword.setSelection(selection.coerceAtMost(etPassword.text?.length ?: 0))
        suppressWatchers = false

        btnTogglePassword.setImageResource(
            if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye
        )
        btnTogglePassword.contentDescription =
            if (visible) "Ẩn mật khẩu" else "Hiện mật khẩu"
    }

    private fun setPausedFlag(paused: Boolean) {
        isUserInteracting = paused
        RemoteControlHub.isCountdownPaused = paused
        if (::btnToggleCountdown.isInitialized) {
            updateCountdownToggleUi()
        }
    }

    private fun updateCountdownToggleUi() {
        btnToggleCountdown.text = if (isUserInteracting) "Tiếp tục" else "Tạm dừng"
    }

    private fun showWarning(message: String) {
        tvWarning.text = message
        layoutWarning.visibility = View.VISIBLE
    }

    private fun hideWarning() {
        tvWarning.text = ""
        layoutWarning.visibility = View.GONE
    }

    private fun pauseCountdown(reason: String) {
        setPausedFlag(true)
        countDownTimer?.cancel()
        countDownTimer = null
        RemoteControlHub.countdownSeconds = -1
        layoutCountdown.visibility = View.VISIBLE
        tvCountdown.text = reason
    }

    private fun resumeCountdown() {
        val typedUser = etUsername.text.toString().trim()
        val typedPass = etPassword.text.toString().trim()
        val settings = if (typedUser.isNotEmpty() && typedPass.isNotEmpty()) {
            AppConfig.saveCredentials(this, typedUser, typedPass)
        } else {
            AppConfig.load(this)
        }

        if (!settings.hasCredentials()) {
            showWarning("Chưa có thông tin đăng nhập. Vui lòng nhập tài khoản và mật khẩu.")
            return
        }

        setPausedFlag(false)
        layoutCountdown.visibility = View.VISIBLE
        if (isNetworkConnected) {
            startCountdown()
        } else {
            RemoteControlHub.countdownSeconds = -1
            tvCountdown.text = "Đang chờ kết nối mạng để tự động đếm ngược..."
        }
    }

    private fun populateFields(settings: AppSettings) {
        suppressWatchers = true
        etUsername.setText(settings.username)
        etPassword.setText(settings.password)
        suppressWatchers = false
    }

    private fun formatLanBannerText(localUrl: String, ipUrl: String?): String {
        val isCompactPortrait =
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                resources.configuration.smallestScreenWidthDp < 600
        return when {
            ipUrl.isNullOrBlank() -> "Điều khiển LAN: $localUrl"
            isCompactPortrait -> "Điều khiển LAN: $localUrl\nIP trực tiếp: $ipUrl"
            else -> "Điều khiển LAN: $localUrl  •  $ipUrl"
        }
    }

    private fun updateLanBanner() {
        val settings = AppConfig.load(this)
        val hostLabel = runCatching { URI(settings.targetUrl).host }
            .getOrNull().orEmpty().ifEmpty { "diemdanhhs.com" }
        tvVersionSubtitle.text =
            "Phiên bản v${BuildConfig.VERSION_NAME} • Máy chủ: $hostLabel • LAN: ${BuildConfig.CONTROL_SERVER_PORT}"

        val (localUrl, ipUrl) = RemoteControlHub.getControlUrls()
        tvLanInfo.text = formatLanBannerText(localUrl, ipUrl)
    }

    private fun startCountdown() {
        if (!isNetworkConnected || isUserInteracting) return
        layoutCountdown.visibility = View.VISIBLE
        updateCountdownToggleUi()
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(30_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                RemoteControlHub.countdownSeconds = seconds
                tvCountdown.text = "Tự động mở màn hình điểm danh sau ${seconds}s..."
            }

            override fun onFinish() {
                countDownTimer = null
                RemoteControlHub.countdownSeconds = -1
                if (!isFinishing && isNetworkConnected && !isUserInteracting) {
                    val current = AppConfig.load(this@MainActivity)
                    if (current.hasCredentials()) {
                        navigateToWebView(current)
                    }
                }
            }
        }.start()
    }

    private fun navigateToWebView(settings: AppSettings) {
        countDownTimer?.cancel()
        countDownTimer = null
        RemoteControlHub.countdownSeconds = -1
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra("username", settings.username)
            putExtra("password", settings.password)
            putExtra("target_url", settings.targetUrl)
            if (settings.autoEnterCheckInPage) {
                putExtra("initial_route", "/check-in-out")
            }
        }
        startActivity(intent)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val editingInput = etUsername.hasFocus() || etPassword.hasFocus()
        val dev = event.device
        if (!editingInput && dev != null && !dev.isVirtual) {
            if (AppConfig.load(this).hardwareRfidEnabled) {
                val isEnter = keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                val isBackspace = keyCode == KeyEvent.KEYCODE_DEL
                val uni = event.unicodeChar
                val ch = if (uni > 0) uni.toChar() else null
                if (isEnter || isBackspace || (ch != null && RfidScannerBuffer.isAllowedCardChar(ch))) {
                    val scanned = RemoteControlHub.rfidBuffer.onKeyChar(
                        keyChar = ch,
                        isEnter = isEnter,
                        isBackspace = isBackspace,
                        eventTimeMs = event.eventTime
                    )
                    if (scanned != null) {
                        RemoteControlHub.dispatchHardwareRfidCardAsync(scanned, source = "USB_HID")
                        return true
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCaptureScreenshotJpeg(): ByteArray? {
        if (isFinishing || isDestroyed) return null
        val targetView = window?.decorView?.rootView ?: return null
        val srcW = targetView.width
        val srcH = targetView.height
        if (srcW <= 0 || srcH <= 0) return null
        val scale = minOf(1f, 960f / maxOf(srcW, srcH).toFloat())
        val dstW = (srcW * scale).toInt().coerceAtLeast(1)
        val dstH = (srcH * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.RGB_565)
        return try {
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            targetView.draw(canvas)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)
            out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    override fun onRemoteConfigUpdated(settings: AppSettings) {
        populateFields(settings)
        updateDomainButtonsState(settings.targetUrl)
        updateLanBanner()
        if (settings.hasCredentials() && isNetworkConnected) {
            hideWarning()
            if (!isUserInteracting && countDownTimer == null) {
                startCountdown()
            }
        }
    }

    override fun onRemoteCommand(action: String) {
        when (action) {
            "connect" -> {
                val current = AppConfig.load(this)
                if (current.hasCredentials()) {
                    setPausedFlag(false)
                    navigateToWebView(current)
                }
            }
            "stop_countdown" -> {
                pauseCountdown("Đã tạm dừng đếm ngược từ trang điều khiển LAN.")
            }
            "resume_countdown" -> {
                resumeCountdown()
            }
        }
    }

    override fun onLanEndpointChanged(localUrl: String, ipUrl: String?) {
        tvLanInfo.text = formatLanBannerText(localUrl, ipUrl)
    }

    override fun onUpdateStatusChanged(state: String, message: String, progress: Int) {
        btnGithubUpdate.isEnabled = state != "CHECKING" && state != "DOWNLOADING"
        btnGithubUpdate.text = when (state) {
            "CHECKING" -> "Đang kiểm tra GitHub..."
            "DOWNLOADING" -> "Đang tải APK ($progress%)..."
            "AVAILABLE" -> "Cài bản mới ngay"
            "UP_TO_DATE" -> "Đang ở bản mới nhất"
            else -> "Kiểm tra cập nhật GitHub"
        }
        if (state == "ERROR") {
            showWarning(message)
        } else {
            layoutCountdown.visibility = View.VISIBLE
            tvCountdown.text = message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        countDownTimer = null
        networkMonitor?.unregister()
        RemoteControlHub.detachListener(this)
    }
}
