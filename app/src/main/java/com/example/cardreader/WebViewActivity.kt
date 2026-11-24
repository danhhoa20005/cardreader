package com.example.cardreader

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class WebViewActivity : ComponentActivity(), RemoteScreenListener {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutStatusToast: LinearLayout
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatusBanner: TextView

    private var username: String = ""
    private var password: String = ""
    private var targetUrl: String = ""
    private var currentLoadUrl: String = ""
    private var pageRetryCount: Int = 0
    private var loginAttempts: Int = 0
    private var loginVerified: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var networkMonitor: NetworkMonitor? = null
    private var pendingAutomationRunnable: Runnable? = null
    private var pendingRetryRunnable: Runnable? = null

    private enum class ToastStyle { INFO, WARN, OK, ERROR }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RemoteControlHub.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_webview)

        val fallbackConfig = AppConfig.load(this)
        username = intent.getStringExtra(MainActivity.EXTRA_USERNAME) ?: fallbackConfig.username
        password = intent.getStringExtra(MainActivity.EXTRA_PASSWORD) ?: fallbackConfig.password
        targetUrl = AppConfig.normalizeUrl(
            intent.getStringExtra(MainActivity.EXTRA_TARGET_URL) ?: fallbackConfig.targetUrl
        ).ifEmpty { BuildConfig.DEFAULT_TARGET_URL }

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        layoutStatusToast = findViewById(R.id.layoutStatusToast)
        ivStatusIcon = findViewById(R.id.ivStatusIcon)
        tvStatusBanner = findViewById(R.id.tvStatusBanner)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        val isLocalTarget = runCatching {
            val host = URI(targetUrl).host ?: ""
            host.endsWith(".local", ignoreCase = true) || host.startsWith("192.168.") || host.startsWith("10.")
        }.getOrDefault(false)

        networkMonitor = NetworkMonitor(this, allowLanWithoutInternet = isLocalTarget) { connected ->
            if (!connected && !isFinishing) {
                returnToMainWithError("Mất kết nối mạng. Vui lòng kiểm tra lại kết nối internet.")
            }
        }
        networkMonitor?.register()

        configureWebView()
        RemoteControlHub.attachListener(RemoteScreen.WEBVIEW, this)
        resolveAndLoadTargetUrl()
    }

    override fun onResume() {
        super.onResume()
        RemoteControlHub.attachListener(RemoteScreen.WEBVIEW, this)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        webView.addJavascriptInterface(WebAppBridge(), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                cancelPendingAutomation()
                loginVerified = false
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    RemoteControlHub.activeWebUrl = url
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url == null || !isTargetPageUrl(url)) return
                RemoteControlHub.activeWebUrl = url
                scheduleAutomation(delayMs = 1200L)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true) return

                val errorDesc = error?.description?.toString().orEmpty().ifBlank { "Lỗi kết nối" }
                handleMainFrameLoadFailure(errorDesc)
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                if (!isFinishing) {
                    returnToMainWithError("WebView vừa khởi động lại do bộ nhớ. Đang tự kết nối lại...")
                }
                return true
            }
        }
    }

    private fun resolveAndLoadTargetUrl() {
        pageRetryCount = 0
        RemoteControlHub.loginState = "LOADING"
        RemoteControlHub.lastError = ""
        RemoteControlHub.addLog("Đang mở trang điểm danh: $targetUrl")
        showBanner("Đang tải: $targetUrl", ToastStyle.INFO)

        ioExecutor.execute {
            val (resolvedUrl, _) = ServerResolver.resolveLocalHostIfNeeded(this, targetUrl)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                currentLoadUrl = resolvedUrl
                RemoteControlHub.activeWebUrl = resolvedUrl
                webView.loadUrl(resolvedUrl)
            }
        }
    }

    private fun handleMainFrameLoadFailure(errorDesc: String) {
        cancelPendingAutomation()
        RemoteControlHub.addLog("Lỗi tải trang ($errorDesc)")

        if (pageRetryCount < 1 && currentLoadUrl.isNotEmpty()) {
            pageRetryCount++
            showBanner("Lỗi tải trang ($errorDesc). Đang thử lại...", ToastStyle.WARN)
            pendingRetryRunnable?.let { mainHandler.removeCallbacks(it) }
            val retry = Runnable {
                if (!isFinishing && !isDestroyed) {
                    webView.loadUrl(currentLoadUrl)
                }
            }
            pendingRetryRunnable = retry
            mainHandler.postDelayed(retry, 2000L)
            return
        }

        returnToMainWithError("Không tải được trang ($errorDesc). Sẽ tự động thử lại.")
    }

    private fun isTargetPageUrl(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val loadedHost = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        val candidateHosts = listOfNotNull(
            runCatching { URI(currentLoadUrl).host?.lowercase() }.getOrNull(),
            runCatching { URI(targetUrl).host?.lowercase() }.getOrNull()
        )
        return candidateHosts.any { it == loadedHost } || url.contains("diemdanh", ignoreCase = true)
    }

    private fun scheduleAutomation(delayMs: Long) {
        cancelPendingAutomation()
        val task = Runnable {
            if (!isFinishing && !isDestroyed) {
                runAutomationScript()
            }
        }
        pendingAutomationRunnable = task
        mainHandler.postDelayed(task, delayMs)
    }

    private fun cancelPendingAutomation() {
        pendingAutomationRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingAutomationRunnable = null
    }

    private fun runAutomationScript() {
        val quotedUser = JSONObject.quote(username)
        val quotedPass = JSONObject.quote(password)

        val jsCode = """
            (function() {
                if (window.__crRunning) return;
                window.__crRunning = true;

                const targetUser = $quotedUser;
                const targetPass = $quotedPass;
                const targetUserLower = targetUser.toLowerCase();

                function sleep(ms) {
                    return new Promise(resolve => setTimeout(resolve, ms));
                }

                async function waitFor(fn, timeoutMs, intervalMs) {
                    const start = Date.now();
                    while (Date.now() - start < timeoutMs) {
                        const res = fn();
                        if (res) return res;
                        await sleep(intervalMs || 200);
                    }
                    return null;
                }

                function triggerReactClickAt(x, y) {
                    const opts = {
                        bubbles: true,
                        cancelable: true,
                        view: window,
                        clientX: x,
                        clientY: y,
                        screenX: x,
                        screenY: y,
                        button: 0,
                        buttons: 1
                    };
                    const el = document.elementFromPoint(x, y) || document.body;
                    el.dispatchEvent(new PointerEvent('pointerdown', opts));
                    el.dispatchEvent(new MouseEvent('mousedown', opts));
                    el.dispatchEvent(new MouseEvent('click', opts));
                }

                function triggerReactClick(element) {
                    if (!element) return;
                    const rect = element.getBoundingClientRect();
                    if (rect.width > 0 && rect.height > 0) {
                        triggerReactClickAt(rect.left + rect.width / 2, rect.top + rect.height / 2);
                    } else {
                        element.click();
                    }
                }

                function setReactInputValue(input, value) {
                    if (!input) return;
                    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                    setter.call(input, value);
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                    input.dispatchEvent(new Event('blur', { bubbles: true }));
                }

                function readStoredSessionUser() {
                    try {
                        const token = localStorage.getItem("accessToken");
                        if (!token) return null;
                        const rawUser = localStorage.getItem("user");
                        if (rawUser) {
                            const u = JSON.parse(rawUser);
                            return (u.username || u.email || u.name || "").toString();
                        }
                        const rawSession = localStorage.getItem("session");
                        if (rawSession) {
                            const s = JSON.parse(rawSession);
                            const u = s.user || {};
                            return (u.username || u.email || u.name || "").toString();
                        }
                        return "";
                    } catch (e) {
                        return null;
                    }
                }

                function findMenuButton() {
                    let btn = document.querySelector('header button') ||
                              document.querySelector('nav button') ||
                              document.querySelector('[aria-label*="menu" i]') ||
                              document.querySelector('svg.lucide-menu')?.closest('button') ||
                              document.querySelector('header svg')?.closest('button');
                    if (btn) return btn;

                    const candidates = Array.from(document.querySelectorAll('button, div[role="button"], span[role="button"], svg'));
                    return candidates.find(b => {
                        const r = b.getBoundingClientRect();
                        return r.top < 110 && r.right > (window.innerWidth - 110) && r.width > 0 && r.height > 0;
                    }) || null;
                }

                function findPopup() {
                    return document.querySelector('[role="dialog"]') ||
                           document.querySelector('.ant-modal') ||
                           document.querySelector('.ant-drawer') ||
                           document.querySelector('.modal') ||
                           document.querySelector('.popup') ||
                           document.querySelector('.drawer') ||
                           document.querySelector('div[class*="fixed"][class*="inset"]');
                }

                function findLoginInputs() {
                    const userInput = document.querySelector(
                        'input[placeholder*="Tên đăng nhập" i], input[placeholder*="tài khoản" i], input[name*="user" i], input[id*="email" i], input[type="text"]'
                    );
                    const passInput = document.querySelector('input[type="password"]');
                    if (userInput && passInput) return { userInput, passInput };
                    return null;
                }

                function detectErrorToast() {
                    const bodyText = (document.body.innerText || "").toLowerCase();
                    if (bodyText.includes("login failed") ||
                        bodyText.includes("sai tài khoản") ||
                        bodyText.includes("sai mật khẩu") ||
                        bodyText.includes("tài khoản hoặc mật khẩu không đúng") ||
                        bodyText.includes("không tồn tại")) {
                        const toastEl = document.querySelector('[data-sonner-toast], .ant-message-error, [role="status"], .destructive');
                        return toastEl ? toastEl.innerText.trim() : "Sai tài khoản hoặc mật khẩu";
                    }
                    return null;
                }

                async function performFormLogin() {
                    let inputs = findLoginInputs();
                    if (!inputs) {
                        const loginTrigger = Array.from(document.querySelectorAll('button, a, div[role="button"], span')).find(el => {
                            const t = (el.innerText || "").toLowerCase().trim();
                            return t === 'đăng nhập' || t === 'login' || t.includes('đăng nhập');
                        });
                        if (loginTrigger) {
                            triggerReactClick(loginTrigger);
                            inputs = await waitFor(findLoginInputs, 3500, 200);
                        }
                    }

                    if (!inputs) {
                        AndroidBridge.onLoginFailed("Không tìm thấy ô nhập đăng nhập trên trang");
                        return;
                    }

                    AndroidBridge.onAutomationStatus("Đang tự động đăng nhập tài khoản " + targetUser + "...");
                    setReactInputValue(inputs.userInput, targetUser);
                    setReactInputValue(inputs.passInput, targetPass);
                    await sleep(400);

                    const submitBtn = document.querySelector('button[type="submit"]') ||
                                      Array.from(document.querySelectorAll('button')).find(b => {
                                          const t = (b.innerText || "").toLowerCase();
                                          return t.includes("đăng nhập") || t.includes("login") || t.includes("xác nhận");
                                      });

                    if (!submitBtn) {
                        AndroidBridge.onLoginFailed("Không tìm thấy nút Đăng nhập");
                        return;
                    }

                    triggerReactClick(submitBtn);

                    const submitTime = Date.now();
                    const outcome = await waitFor(() => {
                        if (localStorage.getItem("accessToken")) return { ok: true };
                        const err = detectErrorToast();
                        if (err) return { ok: false, error: err };
                        if (!document.querySelector('input[type="password"]')) {
                            const popup = findPopup();
                            const txt = ((popup ? popup.innerText : "") + " " + (document.body.innerText || "")).toLowerCase();
                            if (txt.includes(targetUserLower)) return { ok: true };
                            if (!popup && Date.now() - submitTime > 1500) return { ok: true };
                        }
                        return null;
                    }, 7000, 250);

                    if (outcome && outcome.ok) {
                        AndroidBridge.onLoginSuccess(targetUser);
                    } else if (outcome && !outcome.ok) {
                        AndroidBridge.onLoginFailed(outcome.error || "Đăng nhập thất bại");
                    } else {
                        AndroidBridge.onLoginFailed("Hết thời gian chờ phản hồi đăng nhập");
                    }
                }

                async function execute() {
                    try {
                        const storedUser = readStoredSessionUser();
                        if (storedUser !== null) {
                            if (storedUser.toLowerCase() === targetUserLower) {
                                AndroidBridge.onLoginSuccess(targetUser);
                                return;
                            }
                            if (storedUser.length > 0) {
                                AndroidBridge.onAutomationStatus("Phát hiện tài khoản khác (" + storedUser + "), đang đăng xuất...");
                            }
                        }

                        await waitFor(() => findLoginInputs() || findMenuButton(), 8000, 250);

                        if (findLoginInputs()) {
                            await performFormLogin();
                            return;
                        }

                        const menuBtn = findMenuButton();
                        if (menuBtn) {
                            triggerReactClick(menuBtn);
                        } else {
                            triggerReactClickAt(window.innerWidth - 40, 35);
                        }

                        const popup = await waitFor(findPopup, 3500, 200);
                        const popupText = (popup ? popup.innerText : document.body.innerText).toLowerCase();

                        const hasLoginUi = popupText.includes("đăng nhập") ||
                                           popupText.includes("đăng ký") ||
                                           popupText.includes("login") ||
                                           popupText.includes("register") ||
                                           document.querySelector('input[type="password"]') !== null;

                        if (hasLoginUi) {
                            await performFormLogin();
                            return;
                        }

                        if (popupText.includes(targetUserLower)) {
                            const closeBtn = document.querySelector('button[aria-label*="close" i], .close-btn, .ant-drawer-close, .ant-modal-close') ||
                                             document.querySelector('svg.lucide-x')?.closest('button') ||
                                             Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('×') || b.innerText.includes('✕'));
                            if (closeBtn) triggerReactClick(closeBtn);
                            else triggerReactClickAt(50, 50);
                            AndroidBridge.onLoginSuccess(targetUser);
                            return;
                        }

                        const logoutBtn = Array.from(document.querySelectorAll('button, a, div[role="button"], span')).find(el => {
                            const t = (el.innerText || "").toLowerCase().trim();
                            return t === 'đăng xuất' || t === 'logout' || t.includes('đăng xuất');
                        });

                        if (logoutBtn) {
                            triggerReactClick(logoutBtn);
                            await sleep(1200);
                            const reopenedMenu = await waitFor(findMenuButton, 3000, 200);
                            if (reopenedMenu) triggerReactClick(reopenedMenu);
                            else triggerReactClickAt(window.innerWidth - 40, 35);
                            await waitFor(findPopup, 3000, 200);
                            await performFormLogin();
                        } else {
                            localStorage.clear();
                            sessionStorage.clear();
                            location.reload();
                        }
                    } catch (e) {
                        AndroidBridge.onLoginFailed(e && e.message ? e.message : "Lỗi script tự động hóa");
                    } finally {
                        window.__crRunning = false;
                    }
                }

                execute();
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    private fun showBanner(message: String, style: ToastStyle) {
        val (bgRes, borderRes, textRes, iconRes) = when (style) {
            ToastStyle.INFO -> Quad(R.color.status_info_bg, R.color.status_info_border, R.color.status_info_text, R.drawable.ic_server)
            ToastStyle.WARN -> Quad(R.color.status_warn_bg, R.color.status_warn_border, R.color.status_warn_text, R.drawable.ic_alert)
            ToastStyle.OK -> Quad(R.color.status_ok_bg, R.color.status_ok_border, R.color.status_ok_text, R.drawable.ic_check)
            ToastStyle.ERROR -> Quad(R.color.status_error_bg, R.color.status_error_border, R.color.status_error_text, R.drawable.ic_alert)
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f * resources.displayMetrics.density
            setColor(ContextCompat.getColor(this@WebViewActivity, bgRes))
            setStroke((1 * resources.displayMetrics.density).toInt(), ContextCompat.getColor(this@WebViewActivity, borderRes))
        }
        layoutStatusToast.background = bg
        ivStatusIcon.setImageResource(iconRes)
        ivStatusIcon.setColorFilter(ContextCompat.getColor(this, textRes))
        tvStatusBanner.setTextColor(ContextCompat.getColor(this, textRes))
        tvStatusBanner.text = message

        layoutStatusToast.animate().cancel()
        if (layoutStatusToast.visibility != View.VISIBLE) {
            if (isReducedMotionEnabled()) {
                layoutStatusToast.alpha = 1f
                layoutStatusToast.visibility = View.VISIBLE
            } else {
                layoutStatusToast.alpha = 0f
                layoutStatusToast.visibility = View.VISIBLE
                layoutStatusToast.animate().alpha(1f).setDuration(180L).start()
            }
        }
    }

    private fun hideBannerSmoothly() {
        if (layoutStatusToast.visibility != View.VISIBLE) return
        if (isReducedMotionEnabled()) {
            layoutStatusToast.visibility = View.GONE
        } else {
            layoutStatusToast.animate()
                .alpha(0f)
                .setDuration(220L)
                .withEndAction { layoutStatusToast.visibility = View.GONE }
                .start()
        }
    }

    private fun isReducedMotionEnabled(): Boolean {
        val scale = runCatching {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return scale == 0f
    }

    private fun returnToMainWithError(message: String, pauseAutoConnect: Boolean = false) {
        if (isFinishing) return
        RemoteControlHub.lastError = message
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            if (message.isNotEmpty()) putExtra("login_error", message)
            putExtra(MainActivity.EXTRA_PAUSE_AUTO_CONNECT, pauseAutoConnect)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(mainIntent)
        finish()
    }

    override fun onRemoteConfigUpdated(settings: AppSettings) {
        username = settings.username
        password = settings.password
        targetUrl = settings.targetUrl
    }

    override fun onRemoteCommand(action: String) {
        when (action) {
            "connect" -> {
                val updated = AppConfig.load(this)
                username = updated.username
                password = updated.password
                targetUrl = updated.targetUrl
                loginAttempts = 0
                loginVerified = false
                resolveAndLoadTargetUrl()
            }
            "reload_webview" -> {
                loginAttempts = 0
                loginVerified = false
                showBanner("Đang tải lại trang theo lệnh từ LAN...", ToastStyle.INFO)
                webView.reload()
            }
            "relogin" -> {
                loginAttempts = 0
                loginVerified = false
                showBanner("Đang đăng xuất và đăng nhập lại theo lệnh từ LAN...", ToastStyle.INFO)
                webView.evaluateJavascript(
                    "(function(){localStorage.clear();sessionStorage.clear();location.reload();})();",
                    null
                )
            }
            "open_settings" -> {
                returnToMainWithError("", pauseAutoConnect = true)
            }
        }
    }

    override fun onLanEndpointChanged(localUrl: String, ipUrl: String?) {}
    override fun onHubUpdateStateChanged() {}

    inner class WebAppBridge {
        @JavascriptInterface
        fun onAutomationStatus(status: String) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    RemoteControlHub.loginState = "LOGGING_IN"
                    RemoteControlHub.addLog(status)
                    showBanner(status, ToastStyle.INFO)
                }
            }
        }

        @JavascriptInterface
        fun onLoginSuccess(account: String) {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loginVerified = true
                loginAttempts = 0
                RemoteControlHub.loginState = "LOGGED_IN"
                RemoteControlHub.lastError = ""
                RemoteControlHub.addLog("Đăng nhập thành công: $account")
                showBanner("Đã đăng nhập tài khoản: $account", ToastStyle.OK)
                mainHandler.postDelayed({
                    if (!isFinishing && !isDestroyed && loginVerified) {
                        hideBannerSmoothly()
                    }
                }, 2800L)
            }
        }

        @JavascriptInterface
        fun onLoginFailed(reason: String) {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loginVerified = false
                loginAttempts++
                RemoteControlHub.loginState = "FAILED"
                RemoteControlHub.lastError = reason
                RemoteControlHub.addLog("Đăng nhập lỗi ($loginAttempts/$MAX_LOGIN_ATTEMPTS): $reason")
                if (loginAttempts < MAX_LOGIN_ATTEMPTS) {
                    showBanner(
                        "$reason — Thử lại ($loginAttempts/$MAX_LOGIN_ATTEMPTS)...",
                        ToastStyle.WARN
                    )
                    scheduleAutomation(delayMs = 2500L)
                } else {
                    returnToMainWithError("Đăng nhập thất bại sau $MAX_LOGIN_ATTEMPTS lần: $reason")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelPendingAutomation()
        pendingRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        networkMonitor?.unregister()
        RemoteControlHub.detachListener(this)
        ioExecutor.shutdownNow()
        webView.destroy()
    }

    private data class Quad(val bg: Int, val border: Int, val text: Int, val icon: Int)

    companion object {
        private const val MAX_LOGIN_ATTEMPTS = 3
    }
}
