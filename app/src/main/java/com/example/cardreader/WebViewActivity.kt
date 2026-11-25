package com.example.cardreader

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
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
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Màn hình WebView tự động hóa (Screen 2):
 * - Giữ nguyên kịch bản `setReactInputValue` chuẩn bản gốc CardReaderV1 kết hợp khóa phiên 1 chiều.
 * - Hỗ trợ điều khiển toàn diện từ `localhost:8888` / LAN: đăng nhập nhanh, chuyển mọi phân hệ,
 *   nhận thẻ RFID (USB HID vật lý + HTTP API `/api/rfid/scan`), thao tác chạm từ xa và chụp màn hình trực tiếp.
 */
class WebViewActivity : FragmentActivity(), RemoteScreenListener {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatusBanner: TextView

    private var username: String = ""
    private var password: String = ""
    private var targetUrl: String = ""
    private var currentLoadUrl: String = ""
    private var initialRoute: String = ""

    @Volatile
    private var hasTriggeredSessionAutomation: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var networkMonitor: NetworkMonitor? = null
    private var pendingAutomationRunnable: Runnable? = null
    private var hideBannerRunnable: Runnable? = null

    private val rfidReqSeq = AtomicLong(1L)
    private val pendingRfidCallbacks =
        ConcurrentHashMap<String, (Int, Boolean, String) -> Unit>()

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
        initialRoute = (intent.getStringExtra("initial_route") ?: "").trim()
            .ifEmpty {
                RemoteControlHub.pendingNavigateRoute.ifEmpty {
                    if (fallback.autoEnterCheckInPage) "/check-in-out" else ""
                }
            }
        RemoteControlHub.pendingNavigateRoute = ""

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

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            builtInZoomControls = true
            displayZoomControls = false
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
                webView.requestFocus()
                installWebBridgeObserver()

                // Đã kích hoạt tự động đăng nhập trong phiên này rồi -> chỉ đồng bộ trạng thái & giữ nguyên trang
                if (hasTriggeredSessionAutomation) return

                if (!pageLoaded && url != null && isMatchingTargetUrl(url)) {
                    pageLoaded = true
                    scheduleAutomation(delayMs = 2800L)
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
        webView.requestFocus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUser = intent.getStringExtra("username")?.trim()
        val newPass = intent.getStringExtra("password")?.trim()
        val newTarget = intent.getStringExtra("target_url")?.trim()
        val newRoute = intent.getStringExtra("initial_route")?.trim()
        var shouldReload = false
        if (!newUser.isNullOrEmpty() && newUser != username) {
            username = newUser
            shouldReload = true
        }
        if (!newPass.isNullOrEmpty() && newPass != password) {
            password = newPass
            shouldReload = true
        }
        if (!newTarget.isNullOrEmpty()) {
            val normalized = AppConfig.normalizeUrl(newTarget)
            if (normalized.isNotEmpty() && normalized != targetUrl) {
                targetUrl = normalized
                shouldReload = true
            }
        }
        if (!newRoute.isNullOrEmpty()) {
            initialRoute = newRoute
        }
        if (shouldReload) {
            loadResolvedTargetUrl()
        } else if (!newRoute.isNullOrEmpty()) {
            onRemoteNavigate(newRoute)
        }
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

    private fun showTransientBanner(text: String, isError: Boolean = false) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            hideBannerRunnable?.let { mainHandler.removeCallbacks(it) }
            tvStatusBanner.text = text
            tvStatusBanner.setBackgroundResource(
                if (isError) R.drawable.bg_warn_banner else R.drawable.bg_lan_banner
            )
            tvStatusBanner.visibility = View.VISIBLE
            val hide = Runnable {
                if (!isFinishing && !isDestroyed) {
                    tvStatusBanner.visibility = View.GONE
                }
            }
            hideBannerRunnable = hide
            mainHandler.postDelayed(hide, 4200L)
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
     * Cài cầu nối quan sát phiên làm việc & đầu đọc thẻ RFID thụ động trên trang Web:
     * - Ghi nhận tự động mọi lượt quẹt thẻ USB HID mà `useCardScanner` gọi tới `/api/check-in-out/<maThe>`.
     * - Bổ sung bộ nhận phím đầu đọc RFID toàn cục cho các trang quản lý khác ngoài `/` và `/check-in-out`.
     * - Đồng bộ thông tin trường học, tài khoản và route hiện tại về `localhost:8888`.
     */
    private fun installWebBridgeObserver() {
        val hwRfidEnabled = AppConfig.load(this).hardwareRfidEnabled
        val js = """
            (function() {
                function syncSession() {
                    try {
                        const token = localStorage.getItem('accessToken') || '';
                        const uRaw = JSON.parse(localStorage.getItem('user') || '{}');
                        const sRaw = JSON.parse(localStorage.getItem('session') || '{}');
                        const school = (uRaw.school && uRaw.school.name) || '';
                        const uname = uRaw.username || (sRaw.user && sRaw.user.username) || '';
                        const role = uRaw.type || (sRaw.user && sRaw.user.type) || '';
                        AndroidBridge.onSessionSync(!!token, token, school, uname, role, location.pathname || '/');
                    } catch (e) {}
                }
                syncSession();

                if (window.__crObserverInstalled) return;
                window.__crObserverInstalled = true;
                window.__crManualScanIds = window.__crManualScanIds || {};

                // Theo dõi điều hướng SPA (React Router)
                const origPush = history.pushState;
                history.pushState = function() {
                    const r = origPush.apply(this, arguments);
                    setTimeout(syncSession, 120);
                    return r;
                };
                const origReplace = history.replaceState;
                history.replaceState = function() {
                    const r = origReplace.apply(this, arguments);
                    setTimeout(syncSession, 120);
                    return r;
                };
                window.addEventListener('popstate', () => setTimeout(syncSession, 120));
                setInterval(syncSession, 4000);

                // Quan sát phản hồi từ API /check-in-out/<cardId> khi đầu đọc USB HID quẹt trên trang
                const origFetch = window.fetch;
                if (origFetch) {
                    window.fetch = async function(input, init) {
                        const url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                        const method = (init && init.method ? init.method : 'GET').toUpperCase();
                        const res = await origFetch.apply(this, arguments);
                        try {
                            if (method === 'POST' && url.indexOf('/check-in-out/') >= 0 && url.indexOf('/ma-the') < 0) {
                                const cardId = url.split('/check-in-out/').pop().split('?')[0].trim();
                                if (cardId && !window.__crManualScanIds[cardId]) {
                                    const cloned = res.clone();
                                    cloned.json().then(data => {
                                        const msg = (data && (data.message || data.error)) || ('HTTP ' + res.status);
                                        AndroidBridge.onPassiveRfidResult(cardId, res.status, res.ok, String(msg));
                                    }).catch(() => {});
                                }
                            }
                        } catch (e) {}
                        return res;
                    };
                }

                // Hiển thị thông báo nổi rõ nét trên màn hình Kiosk khi quẹt thẻ từ localhost hoặc ở trang phụ
                window.__crShowToast = function(cardId, ok, msg) {
                    try {
                        let box = document.getElementById('__cr_rfid_toast');
                        if (!box) {
                            box = document.createElement('div');
                            box.id = '__cr_rfid_toast';
                            box.style.cssText = 'position:fixed;top:18px;right:18px;z-index:2147483647;max-width:420px;padding:14px 18px;border-radius:12px;font-family:system-ui,sans-serif;box-shadow:0 12px 30px rgba(0,0,0,0.35);transition:opacity 0.25s ease;pointer-events:none;';
                            document.body.appendChild(box);
                        }
                        box.style.background = ok ? '#052E16' : '#450A0A';
                        box.style.border = ok ? '2px solid #22C55E' : '2px solid #EF4444';
                        box.style.color = '#F8FAFC';
                        box.style.opacity = '1';
                        box.innerHTML = '<div style="font-size:12px;opacity:0.85;margin-bottom:4px;">Thẻ RFID: <b>' + cardId + '</b></div>' +
                                        '<div style="font-size:15px;font-weight:700;color:' + (ok ? '#86EFAC' : '#FECACA') + ';">' + msg + '</div>';
                        clearTimeout(window.__crToastTimer);
                        window.__crToastTimer = setTimeout(() => { box.style.opacity = '0'; }, 4000);
                    } catch (e) {}
                };

                // Hỗ trợ đầu đọc thẻ RFID USB HID ngay cả khi người quản trị đang mở các trang danh sách/báo cáo khác
                if ($hwRfidEnabled) {
                    let buf = '';
                    let lastTs = 0;
                    window.addEventListener('keydown', function(ev) {
                        const p = location.pathname || '/';
                        // Ở trang '/' và '/check-in-out', useCardScanner của trang đã tự bắt phím
                        if (p === '/' || p === '/check-in-out') return;
                        const t = ev.target;
                        if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable)) return;
                        if (ev.ctrlKey || ev.altKey || ev.metaKey) return;
                        const now = Date.now();
                        if (now - lastTs > 300) buf = '';
                        lastTs = now;
                        if (ev.key === 'Enter') {
                            const code = buf.trim();
                            buf = '';
                            if (code.length >= 4) {
                                AndroidBridge.onFallbackHardwareRfid(code);
                            }
                        } else if (ev.key && ev.key.length === 1 && /^[0-9A-Za-z_-]$/.test(ev.key)) {
                            buf += ev.key;
                        }
                    }, true);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    /**
     * Kịch bản tự động đăng nhập chuẩn theo bản gốc CardReaderV1:
     * - Khóa `hasTriggeredSessionAutomation = true` một chiều.
     * - Sau khi đăng nhập thành công, tự động chuyển tới `initialRoute` (ví dụ `/check-in-out`) nếu được yêu cầu.
     */
    private fun runAutomationScript() {
        if (hasTriggeredSessionAutomation) return
        hasTriggeredSessionAutomation = true

        val quotedUser = JSONObject.quote(username)
        val quotedPass = JSONObject.quote(password)
        val quotedRoute = JSONObject.quote(initialRoute)

        val jsCode = """
            (function() {
                const user = $quotedUser;
                const pass = $quotedPass;
                const desiredRoute = $quotedRoute;

                function sleep(ms) {
                    return new Promise(resolve => setTimeout(resolve, ms));
                }

                function navigateSpaIfNeeded() {
                    if (desiredRoute && desiredRoute !== '/' && location.pathname !== desiredRoute) {
                        history.pushState({}, '', desiredRoute);
                        window.dispatchEvent(new PopStateEvent('popstate'));
                    }
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
                    await sleep(1800);

                    try {
                        if (localStorage.getItem('accessToken')) {
                            AndroidBridge.onTelemetry("LOGGED_IN", "Phiên đăng nhập đang hoạt động: " + user);
                            navigateSpaIfNeeded();
                            return;
                        }
                    } catch (e) {}

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

                    await sleep(1500);

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
                                if (desiredRoute && desiredRoute !== '/') {
                                    for (let i = 0; i < 12; i++) {
                                        await sleep(500);
                                        if (localStorage.getItem('accessToken')) {
                                            navigateSpaIfNeeded();
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    } else {
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
                        navigateSpaIfNeeded();
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

        @JavascriptInterface
        fun onSessionSync(
            loggedIn: Boolean,
            token: String?,
            schoolName: String?,
            accountName: String?,
            role: String?,
            pathname: String?
        ) {
            RemoteControlHub.updateWebSessionSnapshot(
                loggedIn = loggedIn,
                token = token,
                schoolName = schoolName ?: "",
                accountName = accountName ?: "",
                accountRole = role ?: "",
                route = pathname ?: "/"
            )
            if (loggedIn) {
                RemoteControlHub.loginState = "LOGGED_IN"
            }
        }

        @JavascriptInterface
        fun onPassiveRfidResult(cardId: String, httpStatus: Int, ok: Boolean, message: String) {
            RemoteControlHub.recordRfidEventFromWeb(cardId, "USB_HID", httpStatus, ok, message)
            showTransientBanner("Thẻ $cardId: $message", isError = !ok)
        }

        @JavascriptInterface
        fun onFallbackHardwareRfid(cardId: String) {
            RemoteControlHub.dispatchHardwareRfidCardAsync(cardId, source = "USB_HID")
        }

        @JavascriptInterface
        fun onRfidCallback(reqId: String, httpStatus: Int, ok: Boolean, message: String) {
            val cb = pendingRfidCallbacks.remove(reqId)
            cb?.invoke(httpStatus, ok, message)
        }
    }

    override fun onRemoteConfigUpdated(settings: AppSettings) {
        username = settings.username.trim()
        password = settings.password.trim()
        targetUrl = settings.targetUrl
    }

    override fun onRemoteDirectLogin(
        username: String,
        password: String,
        targetUrl: String,
        route: String
    ) {
        this.username = username.trim()
        this.password = password.trim()
        this.targetUrl = AppConfig.normalizeUrl(targetUrl).ifEmpty { BuildConfig.DEFAULT_TARGET_URL }
        this.initialRoute = route
        AppConfig.setLastWebUser(this, this.username)

        // Nếu đang ở khác tên miền -> nạp lại tên miền mới rồi chạy đăng nhập
        if (!isMatchingTargetUrl(webView.url ?: "")) {
            loadResolvedTargetUrl()
            return
        }

        // Đăng nhập tức thì bằng API chính thức ngay trong WebView để nạp localStorage chuẩn 100% của React SPA
        hasTriggeredSessionAutomation = true
        val apiBase = RfidScannerBuffer.resolveApiBaseUrl(this.targetUrl)
        val qUser = JSONObject.quote(this.username)
        val qPass = JSONObject.quote(this.password)
        val qApi = JSONObject.quote(apiBase)
        val qRoute = JSONObject.quote(route.ifBlank { "/" })

        val js = """
            (async function() {
                try {
                    const loginRes = await fetch($qApi + '/auth/login', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ username: $qUser, password: $qPass })
                    });
                    const loginJson = await loginRes.json();
                    const d = loginJson && loginJson.data;
                    if (!loginRes.ok || !d || !d.accessToken) {
                        AndroidBridge.onTelemetry('LOGIN_ERROR', (loginJson && loginJson.message) || 'Đăng nhập thất bại');
                        return;
                    }
                    const sessionObj = {
                        user: Object.assign({}, d.account || {}, {
                            schoolName: 'meInfo.', provinceName: '', districtName: '', wardName: '', educationDepartment: ''
                        }),
                        accessToken: d.accessToken,
                        resetToken: d.refreshToken || ''
                    };
                    localStorage.setItem('session', JSON.stringify(sessionObj));
                    localStorage.setItem('accessToken', d.accessToken);
                    const meRes = await fetch($qApi + '/auth/me', {
                        headers: { 'Authorization': 'Bearer ' + d.accessToken }
                    });
                    if (meRes.ok) {
                        const meJson = await meRes.json();
                        if (meJson && meJson.data) {
                            localStorage.setItem('user', JSON.stringify(meJson.data));
                        }
                    }
                    AndroidBridge.onTelemetry('LOGGED_IN', 'Đã đăng nhập nhanh qua localhost: ' + $qUser);
                    location.href = $qRoute;
                } catch (e) {
                    location.reload();
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onRemoteNavigate(routeOrUrl: String): Boolean {
        val trimmed = routeOrUrl.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            webView.loadUrl(trimmed)
            return true
        }
        val normalizedRoute = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        val qRoute = JSONObject.quote(normalizedRoute)
        val js = """
            (function() {
                try {
                    history.pushState({}, '', $qRoute);
                    window.dispatchEvent(new PopStateEvent('popstate'));
                    AndroidBridge.onSessionSync(
                        !!localStorage.getItem('accessToken'),
                        localStorage.getItem('accessToken') || '',
                        '', '', '', $qRoute
                    );
                } catch (e) {
                    location.href = $qRoute;
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
        RemoteControlHub.activeRoute = normalizedRoute
        return true
    }

    override fun onRemoteWebAction(json: JSONObject): JSONObject {
        val act = json.optString("action", "").trim().lowercase()
        return when (act) {
            "tap" -> {
                val xRatio = json.optDouble("xRatio", 0.5).coerceIn(0.0, 1.0).toFloat()
                val yRatio = json.optDouble("yRatio", 0.5).coerceIn(0.0, 1.0).toFloat()
                val w = webView.width.coerceAtLeast(1)
                val h = webView.height.coerceAtLeast(1)
                val x = xRatio * w
                val y = yRatio * h
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(downTime, downTime + 45L, MotionEvent.ACTION_UP, x, y, 0)
                webView.requestFocus()
                webView.dispatchTouchEvent(down)
                webView.dispatchTouchEvent(up)
                down.recycle()
                up.recycle()
                JSONObject().put("ok", true).put("message", "Đã chạm tại (${x.toInt()}, ${y.toInt()})")
            }

            "scroll" -> {
                val dy = json.optInt("dy", 360)
                val qDy = dy.coerceIn(-2000, 2000)
                webView.evaluateJavascript("window.scrollBy({ top: $qDy, behavior: 'smooth' });", null)
                JSONObject().put("ok", true).put("message", "Đã cuộn màn hình ($qDy px)")
            }

            "back" -> {
                if (webView.canGoBack()) webView.goBack()
                JSONObject().put("ok", true).put("message", "Đã quay lại trang trước")
            }

            "forward" -> {
                if (webView.canGoForward()) webView.goForward()
                JSONObject().put("ok", true).put("message", "Đã tiến tới trang tiếp")
            }

            "reload" -> {
                webView.reload()
                JSONObject().put("ok", true).put("message", "Đang tải lại trang")
            }

            "click_text" -> {
                val text = json.optString("text", "").trim()
                if (text.isEmpty()) {
                    return JSONObject().put("ok", false).put("message", "Thiếu nội dung nút cần bấm")
                }
                val qText = JSONObject.quote(text.lowercase())
                val js = """
                    (function() {
                        const target = $qText;
                        const nodes = Array.from(document.querySelectorAll('button, a, [role="button"], .ant-menu-item, .ant-tabs-tab, span, div'));
                        const el = nodes.find(n => {
                            const rect = n.getBoundingClientRect();
                            if (rect.width <= 0 || rect.height <= 0) return false;
                            const t = (n.innerText || '').trim().toLowerCase();
                            return t === target || (t.length <= 60 && t.includes(target));
                        });
                        if (el) { el.click(); return true; }
                        return false;
                    })();
                """.trimIndent()
                webView.evaluateJavascript(js, null)
                JSONObject().put("ok", true).put("message", "Đã gửi lệnh bấm nút '$text'")
            }

            "input_text" -> {
                val value = json.optString("value", "")
                val selector = json.optString("selector", "").trim()
                val qVal = JSONObject.quote(value)
                val qSel = JSONObject.quote(selector)
                val js = """
                    (function() {
                        const sel = $qSel;
                        let input = sel ? document.querySelector(sel) : document.activeElement;
                        if (!input || (input.tagName !== 'INPUT' && input.tagName !== 'TEXTAREA')) {
                            input = document.querySelector('input:not([type="hidden"])');
                        }
                        if (input) {
                            const proto = input.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                            const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                            setter.call(input, $qVal);
                            input.dispatchEvent(new Event('input', { bubbles: true }));
                            input.dispatchEvent(new Event('change', { bubbles: true }));
                        }
                    })();
                """.trimIndent()
                webView.evaluateJavascript(js, null)
                JSONObject().put("ok", true).put("message", "Đã điền dữ liệu vào ô nhập")
            }

            else -> JSONObject().put("ok", false).put("message", "Hành động không hợp lệ: $act")
        }
    }

    override fun onRemoteRfidScan(
        cardId: String,
        mode: String,
        source: String,
        callback: (httpStatus: Int, ok: Boolean, message: String) -> Unit
    ): Boolean {
        if (isFinishing || isDestroyed) return false
        val reqId = "rfid_${rfidReqSeq.incrementAndGet()}"
        pendingRfidCallbacks[reqId] = callback

        val apiBase = RfidScannerBuffer.resolveApiBaseUrl(webView.url ?: targetUrl)
        val qReq = JSONObject.quote(reqId)
        val qCard = JSONObject.quote(cardId)
        val qMode = JSONObject.quote(mode)
        val qApi = JSONObject.quote(apiBase)

        val js = """
            (async function() {
                const reqId = $qReq;
                const cardId = $qCard;
                const mode = $qMode;
                const apiBase = $qApi;
                try {
                    const token = localStorage.getItem('accessToken') || '';
                    if (!token) {
                        AndroidBridge.onRfidCallback(reqId, 401, false, 'Chưa đăng nhập tài khoản điểm danh trên trình duyệt');
                        return;
                    }

                    // Nếu yêu cầu giả lập bàn phím trên trang quẹt thẻ, bắn chuỗi phím cho useCardScanner
                    if (mode === 'keyboard' && (location.pathname === '/' || location.pathname === '/check-in-out')) {
                        if (document.activeElement && typeof document.activeElement.blur === 'function') {
                            document.activeElement.blur();
                        }
                        for (const ch of cardId) {
                            window.dispatchEvent(new KeyboardEvent('keydown', { key: ch, bubbles: true }));
                        }
                        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
                        AndroidBridge.onRfidCallback(reqId, 200, true, 'Đã truyền phím mã thẻ ' + cardId + ' vào bộ quét trên trang');
                        return;
                    }

                    window.__crManualScanIds = window.__crManualScanIds || {};
                    window.__crManualScanIds[cardId] = true;
                    try {
                        const res = await fetch(apiBase + '/check-in-out/' + encodeURIComponent(cardId), {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                                'Authorization': 'Bearer ' + token
                            }
                        });
                        let data = {};
                        try { data = await res.json(); } catch (e) {}
                        const msg = (data && (data.message || data.error)) || (res.ok ? 'Ghi nhận quẹt thẻ thành công' : ('Lỗi máy chủ HTTP ' + res.status));
                        if (typeof window.__crShowToast === 'function') {
                            window.__crShowToast(cardId, res.ok, String(msg));
                        }
                        AndroidBridge.onRfidCallback(reqId, res.status, res.ok, String(msg));
                    } finally {
                        delete window.__crManualScanIds[cardId];
                    }
                } catch (err) {
                    AndroidBridge.onRfidCallback(reqId, 0, false, 'Lỗi kết nối khi gửi thẻ RFID: ' + (err && err.message ? err.message : 'Không xác định'));
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
        // Bảo vệ tránh rò rỉ callback nếu WebView chuyển trang giữa chừng
        mainHandler.postDelayed({
            val leftover = pendingRfidCallbacks.remove(reqId)
            leftover?.invoke(504, false, "Hết thời gian chờ phản hồi từ trang điểm danh")
        }, 5800L)
        return true
    }

    override fun onCaptureScreenshotJpeg(): ByteArray? {
        if (isFinishing || isDestroyed) return null
        val targetView = window?.decorView?.rootView ?: webView
        val srcW = targetView.width
        val srcH = targetView.height
        if (srcW <= 0 || srcH <= 0) return null

        val maxDim = 960
        val scale = minOf(1f, maxDim.toFloat() / maxOf(srcW, srcH).toFloat())
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

    override fun onRemoteCommand(action: String) {
        when (action) {
            "connect" -> {
                val updated = AppConfig.load(this)
                username = updated.username.trim()
                password = updated.password.trim()
                targetUrl = updated.targetUrl
                if (updated.autoEnterCheckInPage) {
                    initialRoute = "/check-in-out"
                }
                loadResolvedTargetUrl()
            }
            "reload_webview" -> {
                RemoteControlHub.addLog("Tải lại trang WebView từ localhost/LAN")
                webView.reload()
            }
            "relogin" -> {
                RemoteControlHub.addLog("Đăng xuất và đăng nhập lại từ localhost/LAN")
                hasTriggeredSessionAutomation = false
                AppConfig.setLastWebUser(this, "")
                AppConfig.saveWebAuthToken(this, "")
                webView.evaluateJavascript(
                    "(function(){localStorage.clear();sessionStorage.clear();location.reload();})();",
                    null
                )
            }
            "logout_web" -> {
                hasTriggeredSessionAutomation = true
                AppConfig.setLastWebUser(this, "")
                AppConfig.saveWebAuthToken(this, "")
                webView.evaluateJavascript(
                    "(function(){localStorage.clear();sessionStorage.clear();location.href='/';})();",
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
        hideBannerRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRfidCallbacks.clear()
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
