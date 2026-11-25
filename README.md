# CardReader: Android Kiosk 24/7, Điều Khiển LAN (Cổng 8888) & Cập Nhật OTA GitHub

Ứng dụng Kiosk 24/7 tối ưu cho **Android TV Box** và **Điện thoại (Dọc & Ngang)** từ Android 6.0 đến Android 16 (`minSdk = 23`, `targetSdk = 35`), tự động kết nối và đăng nhập hệ thống Điểm danh / Đón trả học sinh (`http://diemdanhhs.com` & `https://dontrahs.com`), tích hợp bảng điều khiển nội bộ qua mạng LAN trên cổng **8888** và cập nhật OTA trực tiếp qua **GitHub Releases**.

## Tính năng chính

1. **Giao diện Thích ứng UI/UX Pro Max (`MainActivity` - Screen 1):**
   - Bộ 3 bố cục tự động thích ứng: **Dọc (Portrait)** chuẩn 1 tay trên điện thoại, **Ngang 2 cột (Landscape)** trên điện thoại ngang, và **Màn hình lớn / D-Pad Remote** trên Android TV Box (`sw600dp`).
   - Chọn nhanh 1 chạm giữa **`diemdanhhs.com`** và **`dontrahs.com`**, kiểm tra & cài bản cập nhật trực tiếp từ GitHub, đếm ngược 30 giây phụ thuộc trạng thái mạng.

2. **Tự động hóa WebView 24/7 (`WebViewActivity` - Screen 2):**
   - Giữ màn hình luôn sáng (`FLAG_KEEP_SCREEN_ON`), tự xoay dọc/ngang mượt không tải lại trang (`configChanges`).
   - Sử dụng đúng kịch bản `setReactInputValue` chuẩn của bản gốc kết hợp khóa phiên một chiều để không bao giờ đăng nhập lặp lại sau khi tải trang.

3. **Bảng Điều khiển Web LAN Cổng `8888` (`http://diemdanh.local:8888`):**
   - Giao diện Operations Console thời gian thực: KPI Telemetry Strip, điều khiển tức thì, chọn nhanh máy chủ, lọc & sao chép nhật ký hoạt động, xác thực mã PIN an toàn (`SHA-256` constant-time + giới hạn tốc độ chống dò PIN).

4. **Cập nhật Phần mềm OTA qua GitHub Releases (`GitHubUpdater`):**
   - Kiểm tra phiên bản mới từ `https://api.github.com/repos/danhhoa20005/cardreader/releases/latest`, tải tệp APK hiển thị tiến trình `0% → 100%` và kích hoạt trình cài đặt qua `FileProvider` ngay trên thiết bị hoặc từ bảng điều khiển LAN.

## Lệnh Kiểm thử & Xuất bản APK Tự động Tăng Version

```bash
# Tự động tăng version (+1), chạy Unit Test + Lint và xuất APK mới ra Downloads & apk_release/
./scripts/export_apk.sh

# Chạy bộ kiểm thử giao diện & WebView trên thiết bị / máy ảo Android
./gradlew connectedDebugAndroidTest
```
