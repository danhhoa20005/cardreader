# CardReader: Android Kiosk 24/7, Điều Khiển Toàn Diện Localhost/LAN (8888) & Tích Hợp Đầu Đọc RFID

Ứng dụng Kiosk 24/7 tối ưu cho **Android TV Box** và **Điện thoại (Dọc & Ngang)** từ Android 6.0 đến Android 16 (`minSdk = 23`, `targetSdk = 35`), tự động kết nối hệ thống Điểm danh / Đón trả học sinh (`http://diemdanhhs.com` & `https://dontrahs.com`), tích hợp **Trung tâm Điều khiển & API Đầu đọc thẻ RFID trên `localhost:8888`** và cập nhật OTA trực tiếp qua **GitHub Releases**.

## Tính năng chính

1. **Tích hợp Đầu đọc thẻ RFID Đa kênh (`RfidScannerBuffer` & `/api/rfid/scan`):**
   - **Đầu đọc RFID phần cứng (USB / OTG / Bluetooth HID):** Bắt sự kiện quẹt thẻ trên mọi trang và tự động hiển thị kết quả điểm danh lên màn hình Kiosk kèm lưu lịch sử thời gian thực.
   - **HTTP API trên `localhost:8888` (Tự động tin cậy `127.0.0.1` không cần mã PIN):** Cho phép mọi dịch vụ đọc thẻ RFID (Serial/COM, Python, C#, Node.js, vi điều khiển) gửi mã thẻ qua `POST /api/rfid/scan` hoặc `GET /api/rfid/scan?cardId=...` và nhận ngay kết quả đồng bộ từ máy chủ.
   - **Đồng bộ danh sách mã thẻ của trường (`GET /api/rfid/cards`):** Tải danh sách thẻ RFID đã đăng ký và hỗ trợ quẹt thử 1 chạm trên Web Console.

2. **Điều khiển Toàn bộ Chức năng từ `http://127.0.0.1:8888` & `http://diemdanh.local:8888`:**
   - **Đăng nhập & Đổi hệ thống từ xa (`POST /api/web/login`, `POST /api/web/logout`):** Đăng nhập tức thì vào `diemdanhhs.com` hoặc `dontrahs.com`, tùy chọn tự mở thẳng **Trang Quẹt Thẻ (`/check-in-out`)**.
   - **Chuyển đổi 1 chạm toàn bộ 17 phân hệ (`POST /api/web/navigate`):** Quẹt thẻ RFID, Tín hiệu toàn trường, Danh sách điểm danh/đón trả, Trạng thái điểm danh, Danh sách học sinh, Lớp học, Giáo viên, Quy định điểm danh, Cấu hình nhà trường, Đơn xin nghỉ...
   - **Màn hình Trực tiếp Live View (`GET /api/web/screenshot` & `POST /api/web/action`):** Xem trực tiếp màn hình Kiosk trên trình duyệt và click chuột lên ảnh để chạm điều khiển từ xa, cuộn trang, bấm nút theo chữ hoặc nhập liệu.

3. **Giao diện Thích ứng UI/UX Pro Max & Cập nhật OTA GitHub (`GitHubUpdater`):**
   - Bộ 3 bố cục gốc cho Điện thoại Dọc, Điện thoại Ngang 2 cột và Android TV Box D-Pad.
   - Kiểm tra, tải và cài đặt bản cập nhật APK mới trực tiếp từ `https://github.com/danhhoa20005/cardreader/releases/latest`.

## Ví dụ Tích hợp Nhanh Đầu Đọc RFID Qua Localhost

```bash
# 1. Quẹt thẻ RFID từ dịch vụ trên localhost (POST hoặc GET):
curl -X POST http://127.0.0.1:8888/api/rfid/scan \
  -H "Content-Type: application/json" \
  -d '{"cardId":"0012345678","mode":"auto"}'

curl "http://127.0.0.1:8888/api/rfid/scan?cardId=0012345678"

# 2. Đăng nhập tài khoản nhà trường và vào thẳng chế độ Quẹt thẻ (/check-in-out):
curl -X POST http://127.0.0.1:8888/api/web/login \
  -H "Content-Type: application/json" \
  -d '{"username":"tai_khoan","password":"mat_khau","targetUrl":"http://diemdanhhs.com","route":"/check-in-out"}'

# 3. Điều hướng tới phân hệ bất kỳ:
curl -X POST http://127.0.0.1:8888/api/web/navigate \
  -H "Content-Type: application/json" \
  -d '{"route":"/danh-sach-diem-danh"}'
```

## Lệnh Kiểm thử & Xuất bản APK Tự động Tăng Version

```bash
# Tự động tăng version (+1), chạy Unit Test + Lint và xuất APK mới ra Downloads & apk_release/
./scripts/export_apk.sh

# Chạy bộ kiểm thử giao diện & WebView trên thiết bị / máy ảo Android
./gradlew connectedDebugAndroidTest
```
