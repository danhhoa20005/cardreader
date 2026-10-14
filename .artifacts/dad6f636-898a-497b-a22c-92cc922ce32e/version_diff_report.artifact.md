# Project Status & Version Diff Report

This comprehensive report details the current status of the Android application, the features implemented across each development checkpoint (`dev_app_point_1` and `dev_app_point_2`), and the architectural differences between them.

---

## 1. Current Status & Implemented Features

The application is fully configured as a robust, unattended kiosk-style solution for Android Boxes, running 24/7 with automated attendance management (`http://diemdanhhs.com`).

### Core Feature Set:
1. **Screen 1 (`MainActivity` - Settings & Credential Input)**:
   - **Muted Dark Theme ("Hơi hướng trầm")**: Professional, easy-on-the-eyes dark palette (`#121824` background, `#1E293B` cards) tailored for Android Box displays.
   - **Network-Aware 30s Countdown**: When saved credentials exist, a 30-second countdown runs **strictly when internet is connected**. If offline, countdown pauses/stops and displays a network warning banner.
   - **Manual Override**: Users can type or edit credentials anytime during countdown, or click "Lưu và Kết Nối" to connect immediately.
   - **Persistence**: Credentials are saved securely via `SharedPreferences`.

2. **Screen 2 (`WebViewActivity` - Automated WebView)**:
   - **Automated Navigation**: Automatically loads `http://diemdanhhs.com`.
   - **JavaScript Automation**:
     - Clicks the hamburger menu at the top-right corner using React-compatible PointerEvent/MouseEvent simulation (with coordinate fallback).
     - Inspects popup content: differentiates between login/register tabs (not logged in) and user info display (already logged in).
     - Automatically autofills username and password using React native value setters and dispatches `input`, `change`, and `blur` events, then submits.
   - **Network Loss Protection**: If internet connection drops while on Screen 2, the app **immediately exits back to Screen 1 (`MainActivity`)** with a clear network warning banner ("⚠️ Mất kết nối mạng. Vui lòng kiểm tra lại kết nối internet.").

3. **Android Box 24/7 & Boot Integration**:
   - **Auto-Start on Boot**: Uses `BootReceiver` listening to `BOOT_COMPLETED` and `QUICKBOOT_POWERON` to launch `MainActivity` automatically upon device startup.
   - **Overlay Permission Prompt**: Requests `SYSTEM_ALERT_WINDOW` permission (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`) on first launch to ensure reliable background activity launching on Android 10+ devices.
   - **24/7 Operation**: Persistent `FLAG_KEEP_SCREEN_ON` flag keeps the screen awake continuously.

---

## 2. Features Implemented at Each Checkpoint

### Checkpoint `dev_app_point_1` (Baseline Stable)
- Basic two-screen architecture (`MainActivity` & `WebViewActivity`).
- Muted dark UI on Screen 1 with 30s countdown.
- WebView automation script targeting `http://diemdanhhs.com` with hamburger menu clicking and React input value injection.
- Initial boot receiver (`BootReceiver`) and 24/7 screen-on flag (`FLAG_KEEP_SCREEN_ON`).

### Checkpoint `dev_app_point_2` (Current Advanced Version)
- **Network-Aware Countdown**: Upgraded `MainActivity` countdown to tie directly into network state (`ConnectivityManager.NetworkCallback`). Countdown pauses when offline and resumes when online.
- **Smart Network Loss Redirection**: Upgraded network monitoring in `WebViewActivity` so that losing internet immediately terminates Screen 2 and returns to Screen 1 with an explicit network warning banner.
- **Popup Content Inspection**: Enhanced JavaScript automation to inspect the actual text inside the hamburger menu popup (login/register tabs vs user details) for absolute reliability.
- **System Overlay Permission**: Added automatic prompt for "Display over other apps" permission to guarantee unattended boot startup on modern Android Box firmware.

---

## 3. Key Differences Between Checkpoints

| Feature / Aspect | `dev_app_point_1` | `dev_app_point_2` (Current) |
| :--- | :--- | :--- |
| **Countdown Behavior** | Counted down unconditionally for 30s upon startup. | **Network-aware**: Pauses/stops when offline; ticks only when internet is active. |
| **Network Loss on Screen 2** | Attempted inline page reloads which could loop or fail silently. | **Immediate Exit**: Cleanly exits back to Screen 1 (`MainActivity`) displaying a prominent network warning banner. |
| **Popup State Detection** | Relied on basic text matching. | **Content Inspection**: Inspects popup drawer text to distinguish between login/register tabs and logged-in user profile info. |
| **Boot Reliability** | Standard boot receiver. | **Enhanced**: Includes `SYSTEM_ALERT_WINDOW` permission request to bypass Android 10+ background activity start restrictions on Android Boxes. |
