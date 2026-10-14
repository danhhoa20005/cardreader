# Backup Checkpoint: dev_app_point_1

Saved at: Stable milestone with Auto-Login, Boot on Receiver, 24/7 Screen-On, and Network Monitoring.

## Architecture & Features Included

1. **Screen 1 (`MainActivity`)**:
   - Muted dark professional theme ("hơi hướng trầm").
   - Initial focus on "Lưu và Kết Nối" button.
   - 30-second countdown when credentials exist, allowing user modification.
   - Warning banner and required input when no credentials exist.
   - Persistence via `SharedPreferences`.

2. **Screen 2 (`WebViewActivity`)**:
   - Loads `http://diemdanhhs.com`.
   - Screen-on flag (`FLAG_KEEP_SCREEN_ON`) for 24/7 operation.
   - Network monitoring via `ConnectivityManager.NetworkCallback` to auto-reload and re-run automation when connection is restored after loss.
   - Robust JavaScript automation:
     - Simulates React PointerEvent/MouseEvent click on hamburger menu at top-right (with coordinate fallback).
     - Waits for menu/popup and clicks "Đăng nhập" if needed.
     - Sets input values using React native value setters and dispatches `input`, `change`, `blur` events.
     - Clicks login submit button successfully.

3. **Auto-Start on Boot (`BootReceiver`)**:
   - Listens to `BOOT_COMPLETED`, `QUICKBOOT_POWERON`.
   - Automatically launches `MainActivity`.
   - Required permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`.
