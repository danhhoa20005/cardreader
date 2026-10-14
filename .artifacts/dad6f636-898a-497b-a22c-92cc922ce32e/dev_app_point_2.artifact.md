# Backup Checkpoint: dev_app_point_2

Saved at: Stable milestone with Network-Aware 30s Countdown, 3-Attempt Login Retry Limit, Auto-Login with Popup Content Inspection, Boot Auto-Start, and 24/7 Screen-On.

## Architecture & Features Included

1. **Screen 1 (`MainActivity`)**:
   - Muted dark professional theme ("hơi hướng trầm").
   - Initial focus on "Lưu và Kết Nối" button.
   - 30-second countdown timer **only runs when internet connection is active**. If offline, countdown pauses/stops and displays a network warning banner.
   - Network monitoring to automatically resume countdown when internet is restored.
   - Persistence via `SharedPreferences`.

2. **Screen 2 (`WebViewActivity`)**:
   - Loads `http://diemdanhhs.com`.
   - Screen-on flag (`FLAG_KEEP_SCREEN_ON`) for 24/7 operation.
   - Network monitoring via `ConnectivityManager.NetworkCallback` to exit back to `MainActivity` with network warning when connection is lost.
   - Robust JavaScript automation:
     - Clicks hamburger menu at top-right (with coordinate fallback and React pointer/mouse event simulation).
     - Inspects popup content: if login/register tabs are present, autofills username and password using React native value setters and submits.
     - If user info is present, considers login successful.

3. **Auto-Start on Boot & Permissions**:
   - `BootReceiver` listens to `BOOT_COMPLETED` and `QUICKBOOT_POWERON`.
   - `SYSTEM_ALERT_WINDOW` permission requested on first launch to ensure background boot start reliability on Android 10+.
