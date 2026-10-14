package com.example.cardreader

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSaveAndConnect: Button
    private lateinit var tvWarning: TextView
    private lateinit var tvCountdown: TextView

    private var countDownTimer: CountDownTimer? = null
    private var isUserInteracting = false
    private var isNetworkConnected = false
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val PREFS_NAME = "AppPrefs"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen on 24/7 for Android Box deployment
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnSaveAndConnect = findViewById(R.id.btnSaveAndConnect)
        tvWarning = findViewById(R.id.tvWarning)
        tvCountdown = findViewById(R.id.tvCountdown)

        // Set initial focus to Save & Connect button as requested
        btnSaveAndConnect.requestFocus()

        // Check initial network state
        isNetworkConnected = checkInitialNetworkState()

        // Setup network monitoring
        setupNetworkMonitoring()

        // Request SYSTEM_ALERT_WINDOW (Display over other apps) permission so app appears in settings
        // and can reliably launch on boot across Android versions (Android 10+)
        if (!Settings.canDrawOverlays(this)) {
            try {
                val overlayIntent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(overlayIntent)
            } catch (_: Exception) {
            }
        }

        val loginError = intent.getStringExtra("login_error")
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedUser = prefs.getString(KEY_USERNAME, "") ?: ""
        val savedPass = prefs.getString(KEY_PASSWORD, "") ?: ""

        if (savedUser.isNotEmpty() && savedPass.isNotEmpty()) {
            etUsername.setText(savedUser)
            etPassword.setText(savedPass)
            tvCountdown.visibility = View.VISIBLE

            if (!loginError.isNullOrEmpty()) {
                tvWarning.text = loginError
                tvWarning.visibility = View.VISIBLE
            } else if (!isNetworkConnected) {
                tvWarning.text = "⚠️ Mất kết nối mạng. Vui lòng kiểm tra lại kết nối internet."
                tvWarning.visibility = View.VISIBLE
            } else {
                tvWarning.visibility = View.GONE
            }

            // Only start countdown if network is connected
            if (isNetworkConnected) {
                startCountdown(savedUser, savedPass)
            } else {
                tvCountdown.text = "Đang chờ kết nối internet để đếm ngược..."
            }

            // If user interacts or types, cancel auto countdown
            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!isUserInteracting) {
                        isUserInteracting = true
                        countDownTimer?.cancel()
                        tvCountdown.text = "Đã dừng đếm ngược. Nhấn 'Lưu và Kết Nối' khi thay đổi xong."
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            etUsername.addTextChangedListener(textWatcher)
            etPassword.addTextChangedListener(textWatcher)

        } else {
            // No data: show warning and require input
            if (!loginError.isNullOrEmpty()) {
                tvWarning.text = loginError
            } else if (!isNetworkConnected) {
                tvWarning.text = "⚠️ Mất kết nối mạng. Vui lòng kiểm tra lại kết nối internet."
            } else {
                tvWarning.text = "⚠️ Cảnh báo: Chưa có thông tin đăng nhập. Vui lòng nhập Tên đăng nhập và Mật khẩu để tiếp tục."
            }
            tvWarning.visibility = View.VISIBLE
            tvCountdown.visibility = View.GONE
        }

        btnSaveAndConnect.setOnClickListener {
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                if (user.isEmpty()) etUsername.error = "Vui lòng nhập tên đăng nhập"
                if (pass.isEmpty()) etPassword.error = "Vui lòng nhập mật khẩu"
                return@setOnClickListener
            }

            // Save credentials
            saveCredentials(user, pass)
            countDownTimer?.cancel()

            // Navigate to Screen 2
            navigateToWebView(user, pass)
        }
    }

    private fun checkInitialNetworkState(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                runOnUiThread {
                    isNetworkConnected = true
                    if (tvWarning.text.toString().contains("Mất kết nối mạng")) {
                        tvWarning.visibility = View.GONE
                    }
                    // When network comes back online, if saved data exists and user hasn't interacted, start countdown
                    val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    val savedUser = prefs.getString(KEY_USERNAME, "") ?: ""
                    val savedPass = prefs.getString(KEY_PASSWORD, "") ?: ""
                    if (savedUser.isNotEmpty() && savedPass.isNotEmpty() && !isUserInteracting && !isFinishing) {
                        tvCountdown.visibility = View.VISIBLE
                        startCountdown(savedUser, savedPass)
                    }
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                runOnUiThread {
                    isNetworkConnected = false
                    tvWarning.text = "⚠️ Mất kết nối mạng. Vui lòng kiểm tra lại kết nối internet."
                    tvWarning.visibility = View.VISIBLE
                    // Pause / cancel countdown when network is lost
                    countDownTimer?.cancel()
                    tvCountdown.text = "Đã tạm dừng đếm ngược (Mất kết nối mạng)..."
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    private fun startCountdown(user: String, pass: String) {
        if (!isNetworkConnected) return
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvCountdown.text = "Tự động chuyển sang màn hình 2 sau ${seconds}s..."
            }

            override fun onFinish() {
                if (!isFinishing && isNetworkConnected) {
                    saveCredentials(user, pass)
                    navigateToWebView(user, pass)
                }
            }
        }.start()
    }

    private fun saveCredentials(user: String, pass: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(KEY_USERNAME, user).putString(KEY_PASSWORD, pass).apply()
    }

    private fun navigateToWebView(user: String, pass: String) {
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra("username", user)
            putExtra("password", pass)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback!!)
            } catch (_: Exception) {
            }
        }
    }
}
