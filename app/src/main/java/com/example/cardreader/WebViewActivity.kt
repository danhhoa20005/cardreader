package com.example.cardreader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.fragment.app.FragmentActivity

class WebViewActivity : FragmentActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private var username: String? = null
    private var password: String? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen on 24/7 for Android Box deployment
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_webview)

        username = intent.getStringExtra("username")
        password = intent.getStringExtra("password")

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        // Setup network monitoring to exit to MainActivity with reconnection flag when connection is lost
        setupNetworkMonitoring()

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

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
                pageLoaded = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!pageLoaded && url?.contains("diemdanhhs.com") == true) {
                    pageLoaded = true
                    // Wait for 3 seconds for React SPA to fully render and attach event listeners
                    Handler(Looper.getMainLooper()).postDelayed({
                        runAutomationScript()
                    }, 3000)
                }
            }
        }

        webView.loadUrl("http://diemdanhhs.com")
    }

    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                // When network is lost, exit back to MainActivity with network warning message and reconnect flag
                runOnUiThread {
                    if (!isFinishing) {
                        val mainIntent = android.content.Intent(this@WebViewActivity, MainActivity::class.java).apply {
                            putExtra("login_error", "⚠️ Mất kết nối mạng. Vui lòng kiểm tra lại kết nối internet.")
                            putExtra("is_reconnecting", true)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(mainIntent)
                        finish()
                    }
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    private fun runAutomationScript() {
        val user = username ?: ""
        val pass = password ?: ""

        val jsCode = """
            (function() {
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

                function triggerReactClick(element) {
                    if (!element) return;
                    const rect = element.getBoundingClientRect();
                    const x = rect.left + rect.width / 2;
                    const y = rect.top + rect.height / 2;
                    triggerReactClickAt(x, y);
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
                    console.log("Starting automation script with popup inspection and user verification...");
                    await sleep(2000);

                    // Step 1: Click hamburger menu at top right
                    let menuBtn = document.querySelector('header button') || 
                                  document.querySelector('nav button') ||
                                  document.querySelector('[aria-label*="menu" i]') ||
                                  document.querySelector('svg.lucide-menu')?.closest('button') ||
                                  document.querySelector('header svg')?.closest('button');

                    if (!menuBtn) {
                        const buttons = Array.from(document.querySelectorAll('button, div[role="button"], span[role="button"], svg'));
                        menuBtn = buttons.find(b => {
                            const rect = b.getBoundingClientRect();
                            return rect.top < 100 && rect.right > (window.innerWidth - 100) && rect.width > 0 && rect.height > 0;
                        });
                    }

                    if (menuBtn) {
                        triggerReactClick(menuBtn);
                    } else {
                        triggerReactClickAt(window.innerWidth - 40, 35);
                    }

                    // Step 2: Wait for popup/menu to appear completely
                    await sleep(1500);

                    // Step 3: Inspect popup content
                    let popup = document.querySelector('[role="dialog"]') ||
                                document.querySelector('.modal') ||
                                document.querySelector('.popup') ||
                                document.querySelector('.drawer') ||
                                document.querySelector('div[class*="fixed"][class*="inset"]') ||
                                document.querySelector('div[class*="modal"]') ||
                                document.querySelector('div[class*="popup"]');

                    const popupText = popup ? popup.innerText.toLowerCase() : document.body.innerText.toLowerCase();

                    // Check if popup contains login or register tabs/buttons or password input
                    const hasLoginOrRegisterTab = popupText.includes("đăng nhập") || 
                                                   popupText.includes("đăng ký") || 
                                                   popupText.includes("login") || 
                                                   popupText.includes("register") ||
                                                   document.querySelector('input[type="password"]') !== null;

                    const targetUser = "$user".toLowerCase();

                    if (hasLoginOrRegisterTab) {
                        console.log("Popup contains login/register tabs -> Not logged in. Attempting auto login...");
                        
                        const clickableElements = Array.from(document.querySelectorAll('button, a, div[role="button"], span'));
                        const loginTrigger = clickableElements.find(el => {
                            const t = el.innerText ? el.innerText.toLowerCase().trim() : '';
                            return t === 'đăng nhập' || t === 'login' || t.includes('đăng nhập');
                        });

                        if (loginTrigger) {
                            triggerReactClick(loginTrigger);
                            await sleep(1500);
                        }

                        let userInput = document.querySelector('input[type="text"], input[name*="user"], input[placeholder*="tài khoản" i], input[placeholder*="user" i]');
                        let passInput = document.querySelector('input[type="password"]');

                        if (userInput && passInput) {
                            setReactInputValue(userInput, "$user");
                            setReactInputValue(passInput, "$pass");
                            await sleep(800);

                            const submitBtn = document.querySelector('button[type="submit"]') ||
                                              Array.from(document.querySelectorAll('button')).find(b => {
                                                  const t = b.innerText ? b.innerText.toLowerCase() : '';
                                                  return t.includes("đăng nhập") || t.includes("login") || t.includes("xác nhận");
                                              });
                            if (submitBtn) {
                                triggerReactClick(submitBtn);
                                console.log("Clicked login submit button successfully");
                            }
                        }
                    } else {
                        console.log("Popup displays user information -> Already logged in.");
                        if (popupText.includes(targetUser)) {
                            console.log("Logged in user matches target user ($user) -> Do nothing and close popup.");
                            let closeBtn = document.querySelector('button[aria-label*="close" i]') ||
                                           document.querySelector('.close-btn') ||
                                           document.querySelector('svg.lucide-x')?.closest('button') ||
                                           Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('×') || b.innerText.includes('✕'));
                            if (closeBtn) {
                                triggerReactClick(closeBtn);
                            } else {
                                triggerReactClickAt(50, 50);
                            }
                        } else {
                            console.log("Logged in user differs from target user -> Clicking Logout and re-logging in.");
                            const clickableElements = Array.from(document.querySelectorAll('button, a, div[role="button"], span'));
                            const logoutBtn = clickableElements.find(el => {
                                const t = el.innerText ? el.innerText.toLowerCase().trim() : '';
                                return t === 'đăng xuất' || t === 'logout' || t.includes('đăng xuất');
                            });

                            if (logoutBtn) {
                                triggerReactClick(logoutBtn);
                                console.log("Clicked Logout button successfully. Waiting for logout state...");
                                await sleep(2000);

                                // Re-open hamburger menu
                                if (menuBtn) {
                                    triggerReactClick(menuBtn);
                                } else {
                                    triggerReactClickAt(window.innerWidth - 40, 35);
                                }
                                await sleep(1500);

                                const loginTrigger = Array.from(document.querySelectorAll('button, a, div[role="button"], span')).find(el => {
                                    const t = el.innerText ? el.innerText.toLowerCase().trim() : '';
                                    return t === 'đăng nhập' || t === 'login' || t.includes('đăng nhập');
                                });

                                if (loginTrigger) {
                                    triggerReactClick(loginTrigger);
                                    await sleep(1500);
                                }

                                let userInput = document.querySelector('input[type="text"], input[name*="user"], input[placeholder*="tài khoản" i], input[placeholder*="user" i]');
                                let passInput = document.querySelector('input[type="password"]');

                                if (userInput && passInput) {
                                    setReactInputValue(userInput, "$user");
                                    setReactInputValue(passInput, "$pass");
                                    await sleep(800);

                                    const submitBtn = document.querySelector('button[type="submit"]') ||
                                                      Array.from(document.querySelectorAll('button')).find(b => {
                                                          const t = b.innerText ? b.innerText.toLowerCase() : '';
                                                          return t.includes("đăng nhập") || t.includes("login") || t.includes("xác nhận");
                                                      });
                                    if (submitBtn) {
                                        triggerReactClick(submitBtn);
                                        console.log("Clicked login submit button successfully after logout");
                                    }
                                }
                            } else {
                                console.log("Logout button not found.");
                            }
                        }
                    }
                }
                execute();
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback!!)
            } catch (_: Exception) {
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
