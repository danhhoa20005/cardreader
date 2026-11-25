package com.example.cardreader

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kiểm thử UI / Widget và tích hợp nội bộ trên thiết bị Android thật/máy ảo cho MainActivity
 * cùng máy chủ điều khiển LAN cổng 8888.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        appContext.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        AppConfig.markOverlayPrompted(appContext)
    }

    @After
    fun tearDown() {
        appContext.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        AppConfig.markOverlayPrompted(appContext)
    }

    @Test
    fun mainActivity_displaysCoreWidgetsAndLanPort8888() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val tvTitle = activity.findViewById<TextView>(R.id.tvTitle)
                val tvLanInfo = activity.findViewById<TextView>(R.id.tvLanInfo)
                val btnDonTra = activity.findViewById<Button>(R.id.btnDomainDonTra)
                val btnDiemDanh = activity.findViewById<Button>(R.id.btnDomainDiemDanh)
                val layoutWarning = activity.findViewById<View>(R.id.layoutWarning)
                val tvWarning = activity.findViewById<TextView>(R.id.tvWarning)
                val layoutCountdown = activity.findViewById<View>(R.id.layoutCountdown)

                assertEquals("Hệ Thống Điểm Danh Học Sinh", tvTitle.text.toString())
                assertTrue(tvLanInfo.text.toString().contains(":8888"))
                assertEquals(View.VISIBLE, layoutWarning.visibility)
                assertTrue(tvWarning.text.toString().isNotBlank())
                assertEquals(View.GONE, layoutCountdown.visibility)

                // Kiểm tra chuyển đổi nhanh giữa diemdanhhs.com và dontrahs.com
                btnDonTra.performClick()
                assertEquals("https://dontrahs.com", AppConfig.load(appContext).targetUrl)
                assertTrue(btnDonTra.isSelected)

                btnDiemDanh.performClick()
                assertEquals("http://diemdanhhs.com", AppConfig.load(appContext).targetUrl)
                assertTrue(btnDiemDanh.isSelected)
            }
        }
    }

    @Test
    fun passwordVisibilityToggle_switchesInputTypeAndAccessibilityLabel() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val etPassword = activity.findViewById<EditText>(R.id.etPassword)
                val btnToggle = activity.findViewById<ImageButton>(R.id.btnTogglePassword)

                etPassword.setText("synthetic_pass_123")
                assertEquals("Hiện mật khẩu", btnToggle.contentDescription.toString())

                btnToggle.performClick()
                assertEquals(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    etPassword.inputType
                )
                assertEquals("Ẩn mật khẩu", btnToggle.contentDescription.toString())

                btnToggle.performClick()
                assertEquals(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    etPassword.inputType
                )
                assertEquals("Hiện mật khẩu", btnToggle.contentDescription.toString())
            }
        }
    }

    @Test
    fun emptyCredentialsValidation_setsFieldErrorsWithoutFinishing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val etUsername = activity.findViewById<EditText>(R.id.etUsername)
                val etPassword = activity.findViewById<EditText>(R.id.etPassword)
                val btnSave = activity.findViewById<Button>(R.id.btnSaveAndConnect)

                activity.updateConnectButtonState(true)
                etUsername.setText("")
                etPassword.setText("")
                btnSave.performClick()

                assertNotNull(etUsername.error)
                assertNotNull(etPassword.error)
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun countdownPauseResumeAndOfflineButtonState_workConsistently() {
        AppConfig.saveCredentials(appContext, "test_kiosk_user", "test_kiosk_pass")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val layoutCountdown = activity.findViewById<View>(R.id.layoutCountdown)
                val btnToggleCountdown = activity.findViewById<Button>(R.id.btnToggleCountdown)
                val btnSave = activity.findViewById<Button>(R.id.btnSaveAndConnect)

                assertEquals(View.VISIBLE, layoutCountdown.visibility)
                assertEquals("Tạm dừng", btnToggleCountdown.text.toString())

                // Pause countdown via UI button
                btnToggleCountdown.performClick()
                assertTrue(RemoteControlHub.isCountdownPaused)
                assertEquals("Tiếp tục", btnToggleCountdown.text.toString())

                // Resume countdown via UI button
                btnToggleCountdown.performClick()
                assertFalse(RemoteControlHub.isCountdownPaused)
                assertEquals("Tạm dừng", btnToggleCountdown.text.toString())

                // Verify offline network state disables connect button per Kiosk spec
                activity.updateConnectButtonState(false)
                assertFalse(btnSave.isEnabled)
                activity.updateConnectButtonState(true)
                assertTrue(btnSave.isEnabled)
            }
        }
    }

    @Test
    fun orientationRecreation_preservesTypedCredentialsPasswordVisibilityAndPausedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val etUsername = activity.findViewById<EditText>(R.id.etUsername)
                val etPassword = activity.findViewById<EditText>(R.id.etPassword)
                val btnTogglePass = activity.findViewById<ImageButton>(R.id.btnTogglePassword)

                etUsername.setText("portrait_user")
                etPassword.setText("portrait_pass")
                btnTogglePass.performClick()
            }

            // Giả lập xoay màn hình dọc <-> ngang
            scenario.recreate()

            scenario.onActivity { activity ->
                val etUsername = activity.findViewById<EditText>(R.id.etUsername)
                val etPassword = activity.findViewById<EditText>(R.id.etPassword)
                val btnTogglePass = activity.findViewById<ImageButton>(R.id.btnTogglePassword)
                val btnToggleCountdown = activity.findViewById<Button>(R.id.btnToggleCountdown)

                assertEquals("portrait_user", etUsername.text.toString())
                assertEquals("portrait_pass", etPassword.text.toString())
                assertEquals("Ẩn mật khẩu", btnTogglePass.contentDescription.toString())
                assertTrue(RemoteControlHub.isCountdownPaused)
                assertEquals("Tiếp tục", btnToggleCountdown.text.toString())
            }
        }
    }

    @Test
    fun embeddedLanServerPort8888_servesHealthStatusAndRemotePauseCommand() {
        AppConfig.saveCredentials(appContext, "test_kiosk_user", "test_kiosk_pass")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            // Verify /api/health on real port 8888 inside running app
            val healthConn = (URL("http://127.0.0.1:8888/api/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3_000
                readTimeout = 3_000
            }
            assertEquals(200, healthConn.responseCode)
            val healthJson = JSONObject(healthConn.inputStream.bufferedReader().use { it.readText() })
            healthConn.disconnect()
            assertTrue(healthJson.getBoolean("ok"))
            assertEquals(8888, healthJson.getInt("port"))

            // Send stop_countdown via LAN HTTP API and verify MainActivity updates UI state
            val cmdConn = (URL("http://127.0.0.1:8888/api/command").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 3_000
                readTimeout = 3_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write("{\"action\":\"stop_countdown\"}".toByteArray(Charsets.UTF_8)) }
            }
            assertEquals(200, cmdConn.responseCode)
            cmdConn.disconnect()

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val btnToggleCountdown = activity.findViewById<Button>(R.id.btnToggleCountdown)
                assertTrue(RemoteControlHub.isCountdownPaused)
                assertEquals("Tiếp tục", btnToggleCountdown.text.toString())
            }
        }
    }
}
