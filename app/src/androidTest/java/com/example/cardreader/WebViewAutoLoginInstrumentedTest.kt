package com.example.cardreader

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kiểm thử tích hợp Android WebView xác minh luồng tự động đăng nhập chỉ gửi form ĐÚNG 1 LẦN,
 * không bị đăng nhập lặp lại khi:
 * 1) Modal Ant Design đóng bằng `display: none` (vẫn giữ thẻ input#password trong DOM),
 * 2) `localStorage.user` lưu email/tên trường khác với chuỗi `username` đăng nhập,
 * 3) SPA điều hướng bằng `history.pushState` kích hoạt `onPageFinished`.
 */
@RunWith(AndroidJUnit4::class)
class WebViewAutoLoginInstrumentedTest {

    private lateinit var appContext: Context
    private var mockWebServer: ServerSocket? = null
    private val serverRunning = AtomicBoolean(false)
    private val pageRequestCount = AtomicInteger(0)
    private val loginApiSubmitCount = AtomicInteger(0)

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        appContext.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        AppConfig.markOverlayPrompted(appContext)
        startLocalSpaFixtureServer()
    }

    @After
    fun tearDown() {
        serverRunning.set(false)
        runCatching { mockWebServer?.close() }
        appContext.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        AppConfig.markOverlayPrompted(appContext)
    }

    @Test
    fun autoLogin_submitsExactlyOnceAndNeverLoopsAfterSuccess() {
        val port = mockWebServer!!.localPort
        val targetUrl = "http://127.0.0.1:$port"
        AppConfig.saveSettings(
            context = appContext,
            username = "test_school_acc",
            password = "test_secret_pass",
            targetUrl = targetUrl,
            mdnsHostname = "diemdanh.local"
        )

        val intent = Intent(appContext, WebViewActivity::class.java).apply {
            putExtra("username", "test_school_acc")
            putExtra("password", "test_secret_pass")
            putExtra("target_url", targetUrl)
        }

        ActivityScenario.launch<WebViewActivity>(intent).use {
            // Chờ luồng tự động đăng nhập theo đúng nhịp thời gian của script gốc hoàn tất
            val deadline = System.currentTimeMillis() + 16_000L
            while (RemoteControlHub.loginState != "LOGGED_IN" && System.currentTimeMillis() < deadline) {
                Thread.sleep(250L)
            }
            assertEquals("LOGGED_IN", RemoteControlHub.loginState)

            // Giữ thêm 6 giây sau khi đăng nhập và chuyển route SPA để chứng minh KHÔNG bị reload / đăng nhập lại
            Thread.sleep(6_000L)

            assertEquals("LOGGED_IN", RemoteControlHub.loginState)
            assertEquals("Chỉ được phép gửi đăng nhập đúng 1 lần duy nhất", 1, loginApiSubmitCount.get())
            assertEquals("Không được tự động reload lại trang sau khi đã đăng nhập", 1, pageRequestCount.get())
        }
    }

    private fun startLocalSpaFixtureServer() {
        val ss = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress("127.0.0.1", 0))
        }
        mockWebServer = ss
        serverRunning.set(true)

        val spaHtml = """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body>
              <header style="display:flex;justify-content:flex-end;padding:12px;">
                <button id="menuBtn" aria-label="menu" onclick="document.getElementById('drawer').style.display='block'">Menu</button>
              </header>
              <div id="drawer" class="ant-drawer" style="display:none;padding:16px;">
                <button id="openLoginBtn" onclick="document.getElementById('modalWrap').style.display='block'">Đăng nhập</button>
              </div>
              <div id="modalWrap" class="ant-modal-wrap" style="display:none;">
                <div class="ant-modal-content">
                  <h3>ĐĂNG NHẬP THÀNH VIÊN</h3>
                  <form id="loginForm" onsubmit="handleLogin(event)">
                    <input id="email" type="text" placeholder="Tên đăng nhập" />
                    <input id="password" type="password" placeholder="Mật khẩu" />
                    <button id="submitLogin" type="submit">Đăng nhập</button>
                  </form>
                </div>
              </div>
              <main id="screenContent">Trang chủ điểm danh</main>
              <script>
                localStorage.clear();
                async function handleLogin(ev) {
                  ev.preventDefault();
                  const u = document.getElementById('email').value;
                  const p = document.getElementById('password').value;
                  await fetch('/api/mock-login?u=' + encodeURIComponent(u));
                  if (u === 'test_school_acc' && p === 'test_secret_pass') {
                    localStorage.setItem('accessToken', 'real_like_jwt_token');
                    // Cố tình lưu email/schoolName KHÁC HẲN username để kiểm tra hồi quy vòng lặp xóa localStorage
                    localStorage.setItem('user', JSON.stringify({ email: 'truonghoc@edusing.vn', name: 'Trường THPT Thực Nghiệm' }));
                    // Đóng modal bằng display:none (giữ nguyên input#password ẩn trong DOM như Ant Design)
                    document.getElementById('modalWrap').style.display = 'none';
                    document.getElementById('drawer').style.display = 'none';
                    history.pushState({}, '', '/danh-sach-truong-hoc');
                    document.getElementById('screenContent').textContent = 'Đã vào màn hình điểm danh';
                  }
                }
              </script>
            </body>
            </html>
        """.trimIndent()

        Thread({
            while (serverRunning.get() && !ss.isClosed) {
                val client = runCatching { ss.accept() }.getOrNull() ?: break
                Thread {
                    client.use { sock ->
                        runCatching {
                            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                            val reqLine = reader.readLine() ?: return@use
                            while (true) {
                                val h = reader.readLine() ?: break
                                if (h.isEmpty()) break
                            }
                            val path = reqLine.split(' ').getOrNull(1).orEmpty()
                            val (body, type) = if (path.startsWith("/api/mock-login")) {
                                loginApiSubmitCount.incrementAndGet()
                                "{\"ok\":true}" to "application/json; charset=utf-8"
                            } else if (path == "/favicon.ico") {
                                "" to "image/x-icon"
                            } else {
                                pageRequestCount.incrementAndGet()
                                spaHtml to "text/html; charset=utf-8"
                            }
                            val bytes = body.toByteArray(Charsets.UTF_8)
                            val resp = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: $type\r\n" +
                                "Content-Length: ${bytes.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            val out = sock.getOutputStream()
                            out.write(resp.toByteArray(Charsets.UTF_8))
                            out.write(bytes)
                            out.flush()
                        }
                    }
                }.start()
            }
        }, "spa-fixture-server").start()
    }
}
