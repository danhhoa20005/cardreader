package com.example.cardreader

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Màn hình WebView tự động hóa (Screen 2) sử dụng đúng kịch bản setReactInputValue & click
 * của bản gốc CardReaderV1, kết hợp khóa phiên một chiều (`hasTriggeredSessionAutomation`)
 * để sau khi đăng nhập và loading tuyệt đối không bao giờ kích hoạt lại lần thứ 2.
 */
class WebViewActivity : FragmentActivity(), RemoteScreenListener {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatusBanner: TextView

    private var username: String = ""
    private var password: String = ""
    private var targetUrl: String = ""
    private var currentLoadUrl: String = ""

    @Volatile
    private var hasTriggeredSessionAutomation: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var networkMonitor: NetworkMonitor? = null
    private var pendingAutomationRunnable: Runnable? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RemoteControlHub.init(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_webview)
        setupAdaptiveWindowInsets()
        applyOrientationSystemBars(resources.configuration)

        val fallback = AppConfig.load(this)
        username = (intent.getStringExtra("username") ?: fallback.username).trim()
        password = (intent.getStringExtra("password") ?: fallback.password).trim()
        targetUrl = AppConfig.normalizeUrl(
            intent.getStringExtra("target_url") ?: fallback.targetUrl
        ).ifEmpty { BuildConfig.DEFAULT_TARGET_URL }

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        tvStatusBanner = findViewById(R.id.tvStatusBanner)

        networkMonitor = NetworkMonitor(this, allowLanWithoutInternet = true) { connected ->
            if (!connected && !isFinishing) {
                returnToMainWithError("Mất kết nối mạng. Vui lòng kiểm tra lại kết nối internet.")
            }
        }
        networkMonitor?.register()

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(WebTelemetryBridge(), "AndroidBridge")

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
            private var pageLoaded = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!url.isNullOrBlank()) {
                    RemoteControlHub.activeWebUrl = url
                }
                if (!hasTriggeredSessionAutomation) {
                    pageLoaded = false
                    cancelPendingAutomation()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) {
                    RemoteControlHub.activeWebUrl = url
                }
                // Đã kích hoạt tự động đăng nhập trong phiên này rồi -> giữ nguyên trang sau khi loading xong
                if (hasTriggeredSessionAutomation) return

                if (!pageLoaded && url != null && isMatchingTargetUrl(url)) {
                    pageLoaded = true
                    scheduleAutomation(delayMs = 3000L)
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                RemoteControlHub.addLog("WebView tự làm mới bộ nhớ")
                if (!isFinishing) {
                    returnToMainWithError("Trình duyệt vừa làm mới bộ nhớ. Đang kết nối lại...")
                }
                return true
            }
        }

        RemoteControlHub.attachListener(RemoteScreen.WEBVIEW, this)
        loadResolvedTargetUrl()
    }

    override fun onResume() {
        super.onResume()
        applyOrientationSystemBars(resources.configuration)
        RemoteControlHub.attachListener(RemoteScreen.WEBVIEW, this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationSystemBars(newConfig)
        findViewById<View>(R.id.webRootContainer)?.let { ViewCompat.requestApplyInsets(it) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyOrientationSystemBars(resources.configuration)
        }
    }

    private fun setupAdaptiveWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<View>(R.id.webRootContainer) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val isPortrait =
                resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            if (isPortrait) {
                val safe = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            } else {
                val cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                view.setPadding(cutout.left, 0, cutout.right, 0)
            }
            windowInsets
        }
    }

    private fun applyOrientationSystemBars(config: Configuration) {
        runCatching {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun scheduleAutomation(delayMs: Long) {
        if (hasTriggeredSessionAutomation) return
        cancelPendingAutomation()
        val runnable = Runnable {
            if (!isFinishing && !isDestroyed && !hasTriggeredSessionAutomation) {
                runAutomationScript()
            }
        }
        pendingAutomationRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPendingAutomation() {
        pendingAutomationRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingAutomationRunnable = null
    }

    private fun loadResolvedTargetUrl() {
        hasTriggeredSessionAutomation = false
        RemoteControlHub.loginState = "LOADING"
        RemoteControlHub.lastError = ""
        RemoteControlHub.addLog("Đang mở trang: $targetUrl")

        // Nếu người quản trị đổi sang tài khoản khác với lần trước, dọn sạch phiên cũ ở tầng Native trước khi tải trang
        val previousUser = AppConfig.getLastWebUser(this)
        if (previousUser.isNotEmpty() && !previousUser.equals(username, ignoreCase = true)) {
            runCatching {
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
        if (username.isNotEmpty()) {
            AppConfig.setLastWebUser(this, username)
        }

        ioExecutor.execute {
            val (resolved, _) = ServerResolver.resolveLocalHostIfNeeded(this, targetUrl)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                currentLoadUrl = resolved
                RemoteControlHub.activeWebUrl = resolved
                webView.loadUrl(resolved)
            }
        }
    }

    private fun isMatchingTargetUrl(url: String): Boolean {
        if (url.contains("diemdanhhs.com", ignoreCase = true) ||
            url.contains("dontrahs.com", ignoreCase = true)
        ) {
            return true
        }
        val loadedHost = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        val expectedHosts = listOfNotNull(
            runCatching { URI(currentLoadUrl).host?.lowercase() }.getOrNull(),
            runCatching { URI(targetUrl).host?.lowercase() }.getOrNull()
        )
        return expectedHosts.any { it == loadedHost }
    }

    private fun returnToMainWithError(message: String, pauseAutoConnect: Boolean = false) {
        if (isFinishing) return
        if (message.isNotEmpty()) {
            RemoteControlHub.lastError = message
        }
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            if (message.isNotEmpty()) putExtra("login_error", message)
            putExtra("is_reconnecting", true)
            putExtra(MainActivity.EXTRA_PAUSE_AUTO_CONNECT, pauseAutoConnect)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(mainIntent)
        finish()
    }

    /**
     * Kịch bản tự động đăng nhập chuẩn theo bản gốc CardReaderV1:
     * - Khóa `hasTriggeredSessionAutomation = true` ngay khi bắt đầu để sau khi bấm Đăng nhập và Loading
     *   không bao giờ kích hoạt lại lần thứ 2.
     * - Dùng nguyên bản `setReactInputValue` của file gốc.
     */
    private fun runAutomationScript() {
        if (hasTriggeredSessionAutomation) return
        hasTriggeredSessionAutomation = true

        val quotedUser = JSONObject.quote(username)
        val quotedPass = JSONObject.quote(password)

        val jsCode = """
            (function() {
                const user = $quotedUser;
                const pass = $quotedPass;

                function sleep(ms) {
                    return new Promise(resolve => setTimeout(resolve, ms));
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

                async function triggerReactClick(element) {
                    if (!element) return;
                    try {
                        element.scrollIntoView({ block: 'center', inline: 'center' });
                    } catch (e) {}
                    await sleep(150);
                    const rect = element.getBoundingClientRect();
                    const x = rect.left + rect.width / 2;
                    const y = rect.top + rect.height / 2;
                    if (rect.width > 0 && rect.height > 0 && x >= 0 && y >= 0 && x <= window.innerWidth && y <= window.innerHeight) {
                        triggerReactClickAt(x, y);
                    } else if (typeof element.click === 'function') {
                        element.click();
                    }
                }

                function setReactInputValue(input, value) {
                    if (!input) return;
                    const nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                    nativeInputValueSetter.call(input, value);
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                    input.dispatchEvent(new Event('blur', { bubbles: true }));
                }

                async function execute() {
                    if (!user || !pass) return;
                    await sleep(2000);

                    // Nếu phiên đăng nhập của đúng tài khoản này vẫn đang còn hiệu lực, giữ nguyên màn hình điểm danh
                    try {
                        if (localStorage.getItem('accessToken')) {
                            AndroidBridge.onTelemetry("LOGGED_IN", "Phiên đăng nhập đang hoạt động: " + user);
                            return;
                        }
                    } catch (e) {}

                    // Step 1: Click hamburger menu at top right (chuẩn bản gốc)
                    let menuBtn = document.querySelector('header button') ||
                                  document.querySelector('nav button') ||
                                  document.querySelector('[aria-label*="menu" i]') ||
                                  (document.querySelector('svg.lucide-menu') ? document.querySelector('svg.lucide-menu').closest('button') : null) ||
                                  (document.querySelector('header svg') ? document.querySelector('header svg').closest('button') : null);

                    if (!menuBtn) {
                        const buttons = Array.from(document.querySelectorAll('button, div[role="button"], span[role="button"], svg'));
                        menuBtn = buttons.find(b => {
                            const rect = b.getBoundingClientRect();
                            return rect.top < 100 && rect.right > (window.innerWidth - 100) && rect.width > 0 && rect.height > 0;
                        });
                    }

                    if (menuBtn) {
                        await triggerReactClick(menuBtn);
                    } else {
                        triggerReactClickAt(window.innerWidth - 40, 35);
                    }

                    // Step 2: Wait for popup/menu to appear completely
                    await sleep(1500);

                    // Step 3: Inspect popup content (chuẩn bản gốc)
                    let popup = document.querySelector('[role="dialog"]') ||
                                document.querySelector('.modal') ||
                                document.querySelector('.popup') ||
                                document.querySelector('.drawer') ||
                                document.querySelector('div[class*="fixed"][class*="inset"]') ||
                                document.querySelector('div[class*="modal"]') ||
                                document.querySelector('div[class*="popup"]');

                    const popupText = popup ? popup.innerText.toLowerCase() : document.body.innerText.toLowerCase();

                    const hasLoginOrRegisterTab = popupText.includes("đăng nhập") ||
                                                   popupText.includes("đăng ký") ||
                                                   popupText.includes("login") ||
                                                   popupText.includes("register") ||
                                                   document.querySelector('input[type="password"]') !== null;

                    if (hasLoginOrRegisterTab) {
                        if (!document.querySelector('input[type="password"]')) {
                            const clickableElements = Array.from(document.querySelectorAll('button, a, div[role="button"], span'));
                            const loginTrigger = clickableElements.find(el => {
                                const t = el.innerText ? el.innerText.toLowerCase().trim() : '';
                                return t === 'đăng nhập' || t === 'login' || t.includes('đăng nhập');
                            });

                            if (loginTrigger) {
                                await triggerReactClick(loginTrigger);
                                await sleep(1500);
                            }
                        }

                        let userInput = document.querySelector('input#email, input[placeholder*="Tên đăng nhập" i], input[type="text"], input[name*="user"], input[placeholder*="tài khoản" i], input[placeholder*="user" i]');
                        let passInput = document.querySelector('input#password, input[type="password"]');

                        if (userInput && passInput) {
                            setReactInputValue(userInput, user);
                            setReactInputValue(passInput, pass);
                            await sleep(800);

                            const submitBtn = document.querySelector('button[type="submit"]') ||
                                              Array.from(document.querySelectorAll('button')).find(b => {
                                                  const t = b.innerText ? b.innerText.toLowerCase() : '';
                                                  return t.includes("đăng nhập") || t.includes("login") || t.includes("xác nhận");
                                              });
                            if (submitBtn) {
                                await triggerReactClick(submitBtn);
                                AndroidBridge.onTelemetry("LOGGED_IN", "Đã tự động điền và bấm Đăng nhập: " + user);
                            }
                        }
                    } else {
                        // Đã ở trong phiên đăng nhập -> đóng popup nếu đang mở và giữ nguyên trang
                        let closeBtn = document.querySelector('button[aria-label*="close" i]') ||
                                       document.querySelector('.close-btn') ||
                                       (document.querySelector('svg.lucide-x') ? document.querySelector('svg.lucide-x').closest('button') : null) ||
                                       Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('×') || b.innerText.includes('✕'));
                        if (closeBtn) {
                            await triggerReactClick(closeBtn);
                        } else if (popup) {
                            triggerReactClickAt(50, 50);
                        }
                        AndroidBridge.onTelemetry("LOGGED_IN", "Đã giữ nguyên phiên đăng nhập: " + user);
                    }
                }

                execute();
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    inner class WebTelemetryBridge {
        @JavascriptInterface
        fun onTelemetry(state: String, message: String) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    RemoteControlHub.loginState = state
                    RemoteControlHub.lastError = ""
                    RemoteControlHub.addLog(message)
                }
            }
        }
    }

    override fun onRemoteConfigUpdated(settings: AppSettings) {
        username = settings.username.trim()
        password = settings.password.trim()
        targetUrl = settings.targetUrl
    }

    override fun onRemoteCommand(action: String) {
        when (action) {
            "connect" -> {
                val updated = AppConfig.load(this)
                username = updated.username.trim()
                password = updated.password.trim()
                targetUrl = updated.targetUrl
                loadResolvedTargetUrl()
            }
            "reload_webview" -> {
                RemoteControlHub.addLog("Tải lại trang WebView từ LAN")
                webView.reload()
            }
            "relogin" -> {
                RemoteControlHub.addLog("Đăng xuất và đăng nhập lại từ LAN")
                hasTriggeredSessionAutomation = false
                AppConfig.setLastWebUser(this, "")
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

    override fun onDestroy() {
        super.onDestroy()
        cancelPendingAutomation()
        networkMonitor?.unregister()
        RemoteControlHub.detachListener(this)
        ioExecutor.shutdownNow()
        webView.destroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
