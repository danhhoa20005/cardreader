# CardReader — Android TV Box Kiosk & LAN Remote Control

Ứng dụng Android Kiosk 24/7 cho Android TV Box tự động mở và đăng nhập hệ thống điểm danh (`http://diemdanhhs.com`), hỗ trợ điều khiển từ xa trong mạng LAN qua `http://diemdanh.local:8080` và tự động cập nhật OTA qua GitHub Releases.

## Tính năng chính
- **Tự khởi động & Chạy 24/7:** Hỗ trợ `BootReceiver`, `FLAG_KEEP_SCREEN_ON`, tự đếm ngược 30s kết nối WebView và tự đăng nhập React SPA (retry tối đa 3 lần khi lỗi).
- **Điều khiển từ xa qua LAN (`:8080`):** TV Box tích hợp HTTP Control Server (`LocalControlServer`) + phát mDNS `diemdanh.local` (`MdnsAdvertiser` + `NsdManager`). Từ điện thoại/máy tính cùng LAN, truy cập `http://diemdanh.local:8080` (hoặc `http://<IP-Box>:8080`) để đổi tài khoản, đổi URL, reload/relogin WebView, xem log live, đặt mã PIN bảo vệ và kích hoạt cập nhật.
- **Tự cập nhật OTA qua GitHub Releases:** Kiểm tra `https://api.github.com/repos/danhhoa20005/cardreader/releases/latest`, tải APK và cài đặt qua `FileProvider`.

## Phát hành bản cập nhật mới
Mỗi khi có phiên bản mới, chỉ cần tạo và đẩy tag theo chuẩn semver (`v1.0.1`, `v1.0.2`, ...):
```bash
git tag v1.0.1
git push origin v1.0.1
```
GitHub Actions (`.github/workflows/release.yml`) sẽ tự chạy test, ký Release APK bằng cùng keystore và phát hành bản Release kèm file `.apk` để TV Box tự nhận bản cập nhật.
