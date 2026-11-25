package com.example.cardreader

/**
 * Bảng điều khiển quản trị thiết bị qua mạng LAN cổng 8888 (http://diemdanh.local:8888),
 * thiết kế theo chuẩn UI/UX Pro Max (Real-Time Operations Console, WCAG AA, tích hợp GitHub OTA).
 */
object WebDashboardHtml {
    val HTML: String = """
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>CardReader: Bảng Điều Khiển LAN (Cổng 8888)</title>
<style>
  :root {
    --bg: #0F172A;
    --surface: #1E293B;
    --surface-elevated: #273549;
    --surface-hover: #334155;
    --input: #0B1120;
    --border: #334155;
    --border-subtle: #26334D;
    --focus-ring: #60A5FA;
    --text-primary: #F8FAFC;
    --text-secondary: #CBD5E1;
    --text-muted: #94A3B8;
    --blue: #2563EB;
    --blue-hover: #1D4ED8;
    --blue-soft: #172554;
    --blue-text: #93C5FD;
    --green: #15803D;
    --green-hover: #166534;
    --green-soft: #052E16;
    --green-text: #86EFAC;
    --amber: #D97706;
    --amber-soft: #451A03;
    --amber-text: #FDE68A;
    --red: #DC2626;
    --red-soft: #450A0A;
    --red-text: #FECACA;
    --radius-lg: 14px;
    --radius-md: 10px;
    --radius-sm: 8px;
  }
  *, *::before, *::after { box-sizing: border-box; }
  html {
    color-scheme: dark;
    scrollbar-width: thin;
    scrollbar-color: #475569 #0B1120;
  }
  ::selection {
    background: rgba(37, 99, 235, 0.38);
    color: #F8FAFC;
  }
  body {
    margin: 0;
    padding: 20px 16px 40px;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    font-size: 14px;
    line-height: 1.5;
    background: var(--bg);
    color: var(--text-primary);
    -webkit-font-smoothing: antialiased;
  }
  :focus-visible {
    outline: 2px solid var(--focus-ring);
    outline-offset: 2px;
  }
  .wrap { max-width: 1040px; margin: 0 auto; }
  header {
    display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
    gap: 12px; padding-bottom: 16px; margin-bottom: 18px; border-bottom: 1px solid var(--border);
  }
  .brand-row { display: flex; align-items: center; gap: 12px; }
  .brand-mark {
    display: inline-flex; align-items: center; justify-content: center;
    width: 42px; height: 42px; border-radius: var(--radius-md);
    background: var(--blue-soft); border: 1px solid #1E40AF; color: var(--blue-text);
    flex-shrink: 0;
  }
  h1 { margin: 0; font-size: 18px; font-weight: 700; letter-spacing: -0.01em; }
  .sub-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 3px; }
  .sub { font-size: 13px; color: var(--text-muted); font-variant-numeric: tabular-nums; }
  .hdr-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
  .pill {
    display: inline-flex; align-items: center; gap: 8px;
    min-height: 38px; padding: 6px 13px; border-radius: 999px;
    font-size: 12px; font-weight: 600;
    background: var(--surface); border: 1px solid var(--border); color: var(--text-secondary);
  }
  .pill.ok { background: var(--green-soft); border-color: var(--green); color: var(--green-text); }
  .pill.warn { background: var(--amber-soft); border-color: var(--amber); color: var(--amber-text); }
  .pill.err { background: var(--red-soft); border-color: var(--red); color: var(--red-text); }
  .dot {
    width: 8px; height: 8px; border-radius: 50%; background: currentColor; flex-shrink: 0;
  }
  .pill.ok .dot {
    box-shadow: 0 0 0 3px rgba(134, 239, 172, 0.2);
  }

  /* KPI Strip */
  .kpi-grid {
    display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px;
  }
  .kpi {
    background: var(--surface); border: 1px solid var(--border);
    border-radius: var(--radius-md); padding: 13px 15px;
  }
  .kpi-label { font-size: 12px; color: var(--text-muted); margin-bottom: 4px; }
  .kpi-val {
    font-size: 15px; font-weight: 700; color: var(--text-primary);
    font-variant-numeric: tabular-nums; word-break: break-word;
  }
  .kpi-sub { font-size: 12px; color: var(--text-secondary); margin-top: 3px; font-variant-numeric: tabular-nums; }

  .grid-2 { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
  .form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
  .card {
    background: var(--surface); border: 1px solid var(--border);
    border-radius: var(--radius-lg); padding: 18px; margin-bottom: 16px;
  }
  .card-head {
    display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
    gap: 8px; margin-bottom: 14px;
  }
  .card-title { display: inline-flex; align-items: center; gap: 8px; }
  .card h2 { margin: 0; font-size: 15px; font-weight: 600; color: var(--text-primary); }
  .kv {
    display: flex; justify-content: space-between; align-items: baseline;
    gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--border-subtle); font-size: 13px;
  }
  .kv:last-child { border-bottom: none; }
  .kv .k { color: var(--text-muted); flex-shrink: 0; }
  .kv .v {
    font-weight: 600; text-align: right; word-break: break-word;
    font-variant-numeric: tabular-nums;
  }
  .v.ok { color: var(--green-text); }
  .v.warn { color: var(--amber-text); }
  .v.err { color: var(--red-text); }

  .field { margin-bottom: 12px; }
  label { display: block; font-size: 13px; font-weight: 500; color: var(--text-secondary); margin-bottom: 6px; }
  .secret-wrap { display: flex; align-items: stretch; gap: 8px; }
  .secret-wrap input { flex: 1; min-width: 0; }
  input[type="text"], input[type="password"] {
    width: 100%; min-height: 44px; padding: 10px 13px;
    border-radius: var(--radius-sm); border: 1px solid var(--border);
    background: var(--input); color: var(--text-primary); font-size: 16px;
    caret-color: var(--focus-ring);
    transition: border-color 140ms ease;
  }
  input[type="text"]::placeholder, input[type="password"]::placeholder { color: var(--text-muted); }
  input[type="text"]:focus, input[type="password"]:focus { border-color: var(--focus-ring); }

  .preset-row { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
  .chip-btn {
    min-height: 36px; padding: 6px 12px; font-size: 12px; border-radius: 999px;
    background: var(--input); border: 1px solid var(--border); color: var(--text-secondary);
  }
  .chip-btn.active {
    background: var(--blue-soft); border-color: var(--focus-ring); color: var(--text-primary);
  }

  .btn-row { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 12px; }
  .cmd-stack { display: grid; grid-template-columns: 1fr; gap: 10px; }
  button {
    display: inline-flex; align-items: center; justify-content: center; gap: 8px;
    min-height: 44px; padding: 10px 16px; border-radius: var(--radius-sm);
    border: 1px solid var(--border); background: var(--surface-elevated);
    color: var(--text-primary); font-size: 13px; font-weight: 600; cursor: pointer;
    transition: background-color 140ms ease, border-color 140ms ease;
  }
  button:hover:not(:disabled) { background: var(--surface-hover); }
  button:disabled { opacity: 0.52; cursor: not-allowed; }
  .btn-left { justify-content: flex-start; padding-left: 14px; }
  .btn-icon { width: 44px; min-width: 44px; padding: 0; flex-shrink: 0; }
  .btn-sm { min-height: 38px; padding: 6px 12px; font-size: 12px; }
  .btn-primary { background: var(--blue); border-color: var(--blue); color: #FFFFFF; }
  .btn-primary:hover:not(:disabled) { background: var(--blue-hover); border-color: var(--blue-hover); }
  .btn-green { background: var(--green); border-color: var(--green); color: #FFFFFF; }
  .btn-green:hover:not(:disabled) { background: var(--green-hover); border-color: var(--green-hover); }
  .btn-amber { background: var(--amber-soft); border-color: var(--amber); color: var(--amber-text); }
  .btn-amber:hover:not(:disabled) { background: #5C2406; }

  .ota-box {
    background: var(--input); border: 1px solid var(--border);
    border-radius: var(--radius-md); padding: 12px 14px; margin-bottom: 12px;
  }
  .ota-status { font-size: 13px; font-weight: 600; color: var(--text-primary); }
  .ota-notes {
    margin-top: 6px; font-size: 12px; color: var(--text-muted);
    white-space: pre-wrap; max-height: 90px; overflow-y: auto;
  }
  .progress-track {
    width: 100%; height: 8px; background: #1E293B; border-radius: 999px;
    overflow: hidden; margin-top: 10px;
  }
  .progress-bar {
    height: 100%; width: 0%; background: var(--blue);
    transition: width 180ms ease;
  }

  .checkline {
    display: inline-flex; align-items: center; gap: 10px;
    min-height: 44px; margin-top: 4px; font-size: 13px; color: var(--text-secondary); cursor: pointer;
  }
  .checkline input[type="checkbox"] { width: 18px; height: 18px; accent-color: var(--blue); cursor: pointer; }
  .notice {
    display: none; align-items: center; justify-content: space-between; gap: 10px;
    padding: 12px 14px; border-radius: var(--radius-sm);
    margin-bottom: 16px; font-size: 13px; font-weight: 500;
  }
  .notice.visible { display: flex; }
  .notice.ok { background: var(--green-soft); border: 1px solid var(--green); color: var(--green-text); }
  .notice.err { background: var(--red-soft); border: 1px solid var(--red); color: var(--red-text); }

  .log-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
  .log-filter {
    min-height: 38px !important; width: 210px !important; font-size: 13px !important; padding: 6px 10px !important;
  }
  .logs {
    background: var(--input); border: 1px solid var(--border); border-radius: var(--radius-sm);
    padding: 12px; height: 220px; overflow-y: auto;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 12px; line-height: 1.6; color: #CBD5E1; white-space: pre-wrap;
    font-variant-numeric: tabular-nums;
  }
  .lock-box { max-width: 420px; margin: 48px auto; }
  .hidden { display: none !important; }
  .sync-meta { font-size: 12px; color: var(--text-muted); font-variant-numeric: tabular-nums; }
  svg.ic {
    width: 17px; height: 17px; stroke: currentColor; stroke-width: 1.8;
    fill: none; stroke-linecap: round; stroke-linejoin: round; flex-shrink: 0;
  }

  @media (max-width: 820px) {
    .kpi-grid { grid-template-columns: repeat(2, 1fr); }
    .grid-2, .form-grid { grid-template-columns: 1fr; }
  }
  @media (max-width: 520px) {
    body { padding: 14px 12px 32px; }
    .kpi-grid { grid-template-columns: 1fr 1fr; gap: 8px; }
    .card { padding: 15px; }
    .kv { flex-direction: column; align-items: flex-start; gap: 3px; }
    .kv .v { text-align: left; }
    .btn-row > button { flex: 1 1 100%; }
    .log-filter { width: 100% !important; }
  }
  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after { transition: none !important; animation: none !important; }
  }
</style>
</head>
<body>
<div class="wrap">
  <header>
    <div class="brand-row">
      <span class="brand-mark" aria-hidden="true">
        <svg class="ic" viewBox="0 0 24 24"><rect x="2" y="4" width="20" height="14" rx="2"/><path d="M8 21h8M12 18v3"/></svg>
      </span>
      <div>
        <h1>Hệ Thống Điểm Danh: Điều Khiển Qua LAN</h1>
        <div class="sub-row">
          <span class="sub" id="hdrSub">Đang kết nối tới thiết bị cổng 8888...</span>
        </div>
      </div>
    </div>
    <div class="hdr-actions">
      <span class="pill" id="pillStatus">
        <span class="dot" aria-hidden="true"></span>
        <span id="pillText">Đang kết nối</span>
      </span>
      <button id="btnTogglePoll" class="btn-sm" type="button" onclick="togglePolling()">Tạm dừng đồng bộ</button>
      <button id="btnLogoutPin" class="btn-sm hidden" type="button" onclick="logoutPin()">Khóa PIN</button>
    </div>
  </header>

  <div id="toast" class="notice" role="status" aria-live="polite"></div>

  <main>
    <section id="lockScreen" class="card lock-box hidden" aria-labelledby="lockTitle">
      <div class="card-head">
        <div class="card-title">
          <svg class="ic" aria-hidden="true" viewBox="0 0 24 24"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          <h2 id="lockTitle">Xác thực PIN quản trị LAN</h2>
        </div>
      </div>
      <div class="field">
        <label for="pinInput">Mã PIN bảo vệ</label>
        <div class="secret-wrap">
          <input id="pinInput" type="password" autocomplete="current-password" placeholder="Nhập mã PIN quản trị..." onkeydown="if(event.key==='Enter')submitPin()" />
          <button class="btn-icon" type="button" aria-label="Hiện hoặc ẩn mã PIN" onclick="toggleSecret('pinInput', this)">
            <svg class="ic" viewBox="0 0 24 24"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>
          </button>
        </div>
      </div>
      <div class="btn-row">
        <button id="btnSubmitPin" class="btn-primary" style="width:100%" type="button" onclick="submitPin()">Mở khóa bảng điều khiển</button>
      </div>
    </section>

    <div id="mainApp" class="hidden">
      <!-- KPI Telemetry Strip -->
      <section class="kpi-grid" aria-label="Chỉ số thiết bị nhanh">
        <div class="kpi">
          <div class="kpi-label">Màn hình thiết bị</div>
          <div class="kpi-val" id="kpiScreen">Đang tải</div>
          <div class="kpi-sub" id="kpiCountdown">Đồng bộ trực tiếp</div>
        </div>
        <div class="kpi">
          <div class="kpi-label">Trạng thái đăng nhập</div>
          <div class="kpi-val" id="kpiLogin">Đang tải</div>
          <div class="kpi-sub" id="kpiUser">Chưa cấu hình</div>
        </div>
        <div class="kpi">
          <div class="kpi-label">Máy chủ đang kết nối</div>
          <div class="kpi-val" id="kpiHost">diemdanhhs.com</div>
          <div class="kpi-sub" id="kpiNet">Đang kiểm tra mạng</div>
        </div>
        <div class="kpi">
          <div class="kpi-label">Phiên bản ứng dụng</div>
          <div class="kpi-val" id="kpiVersion">v1.0.6</div>
          <div class="kpi-sub" id="syncTime">Đang đồng bộ</div>
        </div>
      </section>

      <div class="grid-2">
        <!-- Quick Actions Card -->
        <section class="card" aria-labelledby="cmdHeading">
          <div class="card-head">
            <div class="card-title">
              <svg class="ic" aria-hidden="true" viewBox="0 0 24 24"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
              <h2 id="cmdHeading">Điều khiển từ xa tức thì</h2>
            </div>
          </div>
          <div class="cmd-stack">
            <button class="btn-green btn-left" type="button" onclick="sendCmd('connect', this)">
              <svg class="ic" aria-hidden="true" viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"/></svg>
              <span>Kết nối / Mở màn hình Điểm danh</span>
            </button>
            <button class="btn-primary btn-left" type="button" onclick="sendCmd('reload_webview', this)">
              <svg class="ic" aria-hidden="true" viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
              <span>Tải lại trang Điểm danh hiện tại</span>
            </button>
            <button class="btn-amber btn-left" type="button" onclick="sendCmd('relogin', this)">
              <svg class="ic" aria-hidden="true" viewBox="0 0 24 24"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>
              <span>Đăng xuất &amp; Tự đăng nhập lại</span>
            </button>
            <button class="btn-left" type="button" onclick="sendCmd('open_settings', this)">
              <svg class="ic" aria-hidden="true" viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.26.6.77 1.02 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
              <span>Quay về màn hình Cài đặt (Screen 1)</span>
            </button>
            <button id="btnCountdownToggle" class="btn-left" type="button" data-action="stop_countdown" onclick="sendCmd(this.dataset.action, this)">
              <svg class="ic" aria-hidden="true" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
              <span id="lblCountdownToggle">Tạm dừng đếm ngược 30s</span>
            </button>
          </div>
        </section>

        <!-- GitHub OTA Update & Detailed Telemetry Card -->
        <section class="card" aria-labelledby="otaHeading">
          <div class="card-head">
            <div class="card-title">
              <svg class="ic" aria-hidden="true" viewBox="0 0 24 24"><path d="M4 14.9A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.24"/><path d="M12 12v9M8 17l4 4 4-4"/></svg>
              <h2 id="otaHeading">Cập nhật OTA qua GitHub &amp; Chi tiết</h2>
            </div>
          </div>
          <div class="ota-box">
            <div class="ota-status" id="stOtaMsg">Chưa kiểm tra bản cập nhật GitHub</div>
            <div id="otaProgressWrap" class="progress-track hidden">
              <div id="otaProgressBar" class="progress-bar"></div>
            </div>
            <div id="stOtaNotes" class="ota-notes hidden"></div>
          </div>
          <div class="btn-row" style="margin-top:0;margin-bottom:14px;">
            <button id="btnCheckOta" type="button" onclick="checkOta(this)">Kiểm tra bản mới</button>
            <button id="btnInstallOta" class="btn-primary" type="button" onclick="installOta(this)">Tải &amp; Cài đặt từ GitHub</button>
          </div>
          <div class="kv"><span class="k">URL đang mở</span><span class="v" id="stActiveUrl">Chưa mở</span></div>
          <div class="kv"><span class="k">Địa chỉ LAN mDNS</span><span class="v" id="stMdns">diemdanh.local:8888</span></div>
          <div class="kv"><span class="k">Kho GitHub OTA</span><span class="v" id="stRepo">danhhoa20005/cardreader</span></div>
          <div class="kv"><span class="k">Cảnh báo gần nhất</span><span class="v" id="stError">Không có</span></div>
        </section>
      </div>

      <!-- Configuration Form Card -->
      <section class="card" aria-labelledby="cfgHeading">
        <div class="card-head">
          <div class="card-title">
            <svg class="ic" aria-hidden="true" viewBox="0 0 24 24"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
            <h2 id="cfgHeading">Cấu hình Tài khoản, Máy chủ &amp; Bảo mật LAN</h2>
          </div>
        </div>
        <div class="form-grid">
          <div class="field">
            <label for="cfgUser">Tên đăng nhập điểm danh</label>
            <input id="cfgUser" type="text" autocomplete="username" placeholder="Nhập tên đăng nhập..." />
          </div>
          <div class="field">
            <label for="cfgPass">Mật khẩu (để trống nếu giữ nguyên)</label>
            <div class="secret-wrap">
              <input id="cfgPass" type="password" autocomplete="new-password" placeholder="Nhập mật khẩu mới..." />
              <button class="btn-icon" type="button" aria-label="Hiện hoặc ẩn mật khẩu" onclick="toggleSecret('cfgPass', this)">
                <svg class="ic" viewBox="0 0 24 24"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>
              </button>
            </div>
          </div>
          <div class="field">
            <label for="cfgTargetUrl">URL Hệ thống Điểm danh / Đón trả</label>
            <div class="preset-row">
              <button id="chipDiemDanh" class="chip-btn" type="button" onclick="pickPresetUrl('http://diemdanhhs.com')">diemdanhhs.com</button>
              <button id="chipDonTra" class="chip-btn" type="button" onclick="pickPresetUrl('https://dontrahs.com')">dontrahs.com</button>
            </div>
            <input id="cfgTargetUrl" type="text" oninput="syncPresetHighlight()" placeholder="http://diemdanhhs.com" />
          </div>
          <div class="field">
            <label for="cfgMdns">Tên miền nội bộ mDNS (cổng 8888)</label>
            <input id="cfgMdns" type="text" placeholder="diemdanh.local" />
          </div>
          <div class="field">
            <label for="cfgRepo">Kho GitHub nhận bản cập nhật OTA</label>
            <input id="cfgRepo" type="text" placeholder="danhhoa20005/cardreader" />
          </div>
          <div class="field">
            <label for="cfgPin">Mã PIN bảo vệ trang LAN (tùy chọn)</label>
            <div class="secret-wrap">
              <input id="cfgPin" type="password" autocomplete="new-password" placeholder="Để trống nếu không đổi" />
              <button class="btn-icon" type="button" aria-label="Hiện hoặc ẩn mã PIN mới" onclick="toggleSecret('cfgPin', this)">
                <svg class="ic" viewBox="0 0 24 24"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>
              </button>
            </div>
            <label class="checkline" for="cfgClearPin">
              <input id="cfgClearPin" type="checkbox" /> Xóa mã PIN (truy cập nhanh trong LAN không cần PIN)
            </label>
          </div>
        </div>
        <div class="btn-row">
          <button class="btn-primary" type="button" onclick="saveConfig(false, this)">Lưu cấu hình lên thiết bị</button>
          <button class="btn-green" type="button" onclick="saveConfig(true, this)">Lưu &amp; Mở màn hình Điểm danh ngay</button>
        </div>
      </section>

      <!-- Live Activity Console -->
      <section class="card" aria-labelledby="logHeading">
        <div class="card-head">
          <div class="card-title">
            <svg class="ic" aria-hidden="true" viewBox="0 0 24 24"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>
            <h2 id="logHeading">Nhật ký hoạt động thiết bị (Live)</h2>
          </div>
          <div class="log-toolbar">
            <input id="logFilter" class="log-filter" type="text" aria-label="Lọc nhật ký" placeholder="Lọc nhanh nhật ký..." oninput="renderFilteredLogs()" />
            <button class="btn-sm" type="button" onclick="copyLogs()">Sao chép</button>
          </div>
        </div>
        <div id="logBox" class="logs" tabindex="0" aria-label="Nhật ký hoạt động thiết bị">Đang tải nhật ký...</div>
      </section>
    </div>
  </main>
</div>

<script>
let formInitialized = false;
let logsInitialized = false;
let pollingPaused = false;
let cachedLogs = [];
let adminToken = localStorage.getItem('cr_admin_token') || '';

const SVG_EYE = '<svg class="ic" viewBox="0 0 24 24"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>';
const SVG_EYE_OFF = '<svg class="ic" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 19c-6.4 0-10-7-10-7a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c6.4 0 10 7 10 7a18.5 18.5 0 0 1-2.16 3.19"/><path d="M1 1l22 22"/></svg>';

function toggleSecret(inputId, btn) {
  const input = document.getElementById(inputId);
  if (!input) return;
  const reveal = input.type === 'password';
  input.type = reveal ? 'text' : 'password';
  if (btn) btn.innerHTML = reveal ? SVG_EYE_OFF : SVG_EYE;
}

function togglePolling() {
  pollingPaused = !pollingPaused;
  const btn = document.getElementById('btnTogglePoll');
  btn.textContent = pollingPaused ? 'Tiếp tục đồng bộ' : 'Tạm dừng đồng bộ';
  if (!pollingPaused) refreshStatus();
}

function pickPresetUrl(url) {
  const el = document.getElementById('cfgTargetUrl');
  el.value = url;
  syncPresetHighlight();
}

function syncPresetHighlight() {
  const v = (document.getElementById('cfgTargetUrl').value || '').toLowerCase();
  document.getElementById('chipDiemDanh').classList.toggle('active', v.indexOf('diemdanhhs.com') !== -1);
  document.getElementById('chipDonTra').classList.toggle('active', v.indexOf('dontrahs.com') !== -1);
}

function showNotice(msg, isError) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.className = 'notice visible ' + (isError ? 'err' : 'ok');
  clearTimeout(window.__toastTimer);
  window.__toastTimer = setTimeout(function() { el.classList.remove('visible'); }, 4500);
}

function renderFilteredLogs() {
  const logBox = document.getElementById('logBox');
  const nearBottom = !logsInitialized || (logBox.scrollHeight - logBox.scrollTop - logBox.clientHeight < 40);
  const kw = (document.getElementById('logFilter').value || '').trim().toLowerCase();
  const filtered = kw
    ? cachedLogs.filter(function(line) { return line.toLowerCase().indexOf(kw) !== -1; })
    : cachedLogs;
  logBox.textContent = filtered.length ? filtered.join('\n') : 'Không có dòng nhật ký phù hợp.';
  if (nearBottom) logBox.scrollTop = logBox.scrollHeight;
  logsInitialized = true;
}

async function copyLogs() {
  const text = document.getElementById('logBox').textContent || '';
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      const ta = document.createElement('textarea');
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
    }
    showNotice('Đã sao chép nhật ký vào bộ nhớ tạm', false);
  } catch (e) {
    showNotice('Trình duyệt chặn sao chép tự động', true);
  }
}

async function apiFetch(url, opts) {
  const options = opts || {};
  const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
  if (adminToken) headers['X-Admin-Token'] = adminToken;
  const res = await fetch(url, Object.assign({}, options, { headers: headers }));
  const data = await res.json().catch(function() { return {}; });
  return { status: res.status, data: data };
}

async function submitPin() {
  const pinInput = document.getElementById('pinInput');
  const btn = document.getElementById('btnSubmitPin');
  btn.disabled = true;
  try {
    const res = await apiFetch('/api/auth', {
      method: 'POST',
      body: JSON.stringify({ pin: pinInput.value })
    });
    if (res.status === 200 && res.data.ok) {
      adminToken = res.data.token || '';
      if (adminToken) localStorage.setItem('cr_admin_token', adminToken);
      pinInput.value = '';
      formInitialized = false;
      refreshStatus();
    } else {
      showNotice(res.data.message || 'Mã PIN không đúng', true);
    }
  } finally {
    btn.disabled = false;
  }
}

async function logoutPin() {
  await apiFetch('/api/logout', { method: 'POST', body: '{}' });
  adminToken = '';
  localStorage.removeItem('cr_admin_token');
  formInitialized = false;
  refreshStatus();
}

async function sendCmd(action, btn) {
  if (btn) btn.disabled = true;
  try {
    const res = await apiFetch('/api/command', {
      method: 'POST',
      body: JSON.stringify({ action: action })
    });
    if (res.status === 401) { refreshStatus(); return; }
    showNotice(res.data.message || 'Đã gửi lệnh điều khiển', !res.data.ok);
    setTimeout(refreshStatus, 350);
  } catch (e) {
    showNotice('Không kết nối được tới thiết bị qua LAN', true);
  } finally {
    if (btn) btn.disabled = false;
  }
}

async function checkOta(btn) {
  if (btn) btn.disabled = true;
  try {
    const res = await apiFetch('/api/update/check', { method: 'POST', body: '{}' });
    if (res.status === 401) { refreshStatus(); return; }
    showNotice(res.data.message || 'Đang kiểm tra bản mới trên GitHub...', !res.data.ok);
    setTimeout(refreshStatus, 900);
  } finally {
    if (btn) btn.disabled = false;
  }
}

async function installOta(btn) {
  if (btn) btn.disabled = true;
  try {
    const res = await apiFetch('/api/update/install', { method: 'POST', body: '{}' });
    if (res.status === 401) { refreshStatus(); return; }
    showNotice(res.data.message || 'Đang tải và cài đặt bản cập nhật...', !res.data.ok);
    setTimeout(refreshStatus, 900);
  } finally {
    if (btn) btn.disabled = false;
  }
}

async function saveConfig(applyAndConnect, btn) {
  const user = document.getElementById('cfgUser').value.trim();
  if (applyAndConnect && !user) {
    showNotice('Vui lòng nhập tên đăng nhập trước khi mở màn hình điểm danh.', true);
    document.getElementById('cfgUser').focus();
    return;
  }
  const prev = btn ? btn.textContent : '';
  if (btn) { btn.disabled = true; btn.textContent = 'Đang lưu...'; }
  try {
    const payload = {
      username: user,
      password: document.getElementById('cfgPass').value,
      targetUrl: document.getElementById('cfgTargetUrl').value.trim(),
      mdnsHostname: document.getElementById('cfgMdns').value.trim(),
      githubRepo: document.getElementById('cfgRepo').value.trim(),
      adminPin: document.getElementById('cfgPin').value.trim(),
      clearPin: document.getElementById('cfgClearPin').checked,
      applyAndConnect: applyAndConnect
    };
    const res = await apiFetch('/api/config', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    if (res.status === 401) { refreshStatus(); return; }
    if (res.data.ok) {
      document.getElementById('cfgPass').value = '';
      document.getElementById('cfgPin').value = '';
      document.getElementById('cfgClearPin').checked = false;
    }
    showNotice(res.data.message || 'Đã cập nhật cấu hình', !res.data.ok);
    setTimeout(refreshStatus, 350);
  } catch (e) {
    showNotice('Lỗi mạng khi lưu cấu hình lên thiết bị', true);
  } finally {
    if (btn) { btn.disabled = false; btn.textContent = prev; }
  }
}

function renderStatus(s) {
  if (s.locked) {
    document.getElementById('lockScreen').classList.remove('hidden');
    document.getElementById('mainApp').classList.add('hidden');
    document.getElementById('btnLogoutPin').classList.add('hidden');
    return;
  }
  document.getElementById('lockScreen').classList.add('hidden');
  document.getElementById('mainApp').classList.remove('hidden');
  document.getElementById('btnLogoutPin').classList.toggle('hidden', !s.hasAdminPin);

  const activePort = String(s.port || window.location.port || '8888');
  document.getElementById('hdrSub').textContent =
    (s.controlUrlLocal || '') + (s.lanIp ? ('  •  http://' + s.lanIp + ':' + activePort) : '');
  document.getElementById('syncTime').textContent = 'Cập nhật lúc ' + new Date().toLocaleTimeString('vi-VN');

  const isWeb = s.currentScreen === 'WEBVIEW';
  document.getElementById('kpiScreen').textContent = isWeb ? 'Màn hình 2 (WebView)' : 'Màn hình 1 (Cài đặt)';
  if (isWeb) {
    document.getElementById('kpiCountdown').textContent = 'Đang chạy Kiosk 24/7';
  } else if (s.countdownPaused) {
    document.getElementById('kpiCountdown').textContent = 'Đã tạm dừng đếm ngược';
  } else if (s.countdownSeconds >= 0) {
    document.getElementById('kpiCountdown').textContent = 'Tự mở sau ' + s.countdownSeconds + ' giây';
  } else {
    document.getElementById('kpiCountdown').textContent = 'Đang chờ cấu hình';
  }

  const cdBtn = document.getElementById('btnCountdownToggle');
  const cdLbl = document.getElementById('lblCountdownToggle');
  if (cdBtn && cdLbl && !cdBtn.disabled) {
    if (s.countdownPaused) {
      cdBtn.dataset.action = 'resume_countdown';
      cdLbl.textContent = 'Tiếp tục đếm ngược 30s';
    } else {
      cdBtn.dataset.action = 'stop_countdown';
      cdLbl.textContent = 'Tạm dừng đếm ngược 30s';
    }
  }

  const loginMap = {
    'LOGGED_IN': ['Đã đăng nhập', 'var(--green-text)'],
    'LOGGING_IN': ['Đang đăng nhập...', 'var(--amber-text)'],
    'LOADING': ['Đang tải trang...', 'var(--blue-text)'],
    'FAILED': ['Đăng nhập lỗi', 'var(--red-text)'],
    'IDLE': ['Chờ kết nối', 'var(--text-primary)']
  };
  const pair = loginMap[s.loginState] || [s.loginState || 'IDLE', 'var(--text-primary)'];
  const kpiLogin = document.getElementById('kpiLogin');
  kpiLogin.textContent = pair[0];
  kpiLogin.style.color = pair[1];
  document.getElementById('kpiUser').textContent = s.username
    ? ('Tài khoản: ' + s.username + (s.hasPassword ? '' : ' (thiếu mật khẩu)'))
    : 'Chưa lưu tài khoản';

  let hostOnly = s.targetUrl || 'diemdanhhs.com';
  try { hostOnly = new URL(s.targetUrl).host || hostOnly; } catch (e) {}
  document.getElementById('kpiHost').textContent = hostOnly;
  document.getElementById('kpiNet').textContent = s.internetConnected
    ? ('Internet Online' + (s.lanIp ? (' (' + s.lanIp + ')') : ''))
    : (s.networkConnected ? ('Mạng LAN (' + (s.lanIp || '') + ')') : 'Mất mạng');
  document.getElementById('kpiVersion').textContent = 'v' + (s.versionName || '1.0.6') + ' (Cổng ' + activePort + ')';

  // OTA section
  document.getElementById('stOtaMsg').textContent = s.updateMessage || 'Sẵn sàng kiểm tra bản mới từ GitHub';
  const progWrap = document.getElementById('otaProgressWrap');
  const progBar = document.getElementById('otaProgressBar');
  if (typeof s.updateProgress === 'number' && s.updateProgress >= 0) {
    progWrap.classList.remove('hidden');
    progBar.style.width = Math.min(100, Math.max(0, s.updateProgress)) + '%';
  } else {
    progWrap.classList.add('hidden');
  }
  const notesEl = document.getElementById('stOtaNotes');
  if (s.latestNotes) {
    notesEl.textContent = s.latestNotes;
    notesEl.classList.remove('hidden');
  } else {
    notesEl.classList.add('hidden');
  }

  document.getElementById('stActiveUrl').textContent = s.activeWebUrl || s.targetUrl || 'Chưa mở';
  document.getElementById('stMdns').textContent = (s.mdnsHostname || 'diemdanh.local') + ':' + activePort;
  document.getElementById('stRepo').textContent = s.githubRepo || 'danhhoa20005/cardreader';

  const errEl = document.getElementById('stError');
  errEl.textContent = s.lastError || 'Không có';
  errEl.className = s.lastError ? 'v err' : 'v';

  if (!formInitialized) {
    document.getElementById('cfgUser').value = s.username || '';
    document.getElementById('cfgTargetUrl').value = s.targetUrl || 'http://diemdanhhs.com';
    document.getElementById('cfgMdns').value = s.mdnsHostname || 'diemdanh.local';
    document.getElementById('cfgRepo').value = s.githubRepo || 'danhhoa20005/cardreader';
    syncPresetHighlight();
    formInitialized = true;
  }

  cachedLogs = s.logs || [];
  renderFilteredLogs();
}

async function refreshStatus() {
  if (document.hidden || pollingPaused) return;
  try {
    const res = await apiFetch('/api/status');
    const pill = document.getElementById('pillStatus');
    const pillText = document.getElementById('pillText');
    if (res.status === 200) {
      if (res.data.internetConnected) {
        pillText.textContent = 'Online LAN & Internet';
        pill.className = 'pill ok';
      } else if (res.data.networkConnected) {
        pillText.textContent = 'Chỉ kết nối LAN';
        pill.className = 'pill warn';
      } else {
        pillText.textContent = 'Thiết bị mất mạng';
        pill.className = 'pill err';
      }
      renderStatus(res.data);
    }
  } catch (e) {
    const pill = document.getElementById('pillStatus');
    document.getElementById('pillText').textContent = 'Mất kết nối tới Box';
    pill.className = 'pill err';
  }
}

document.addEventListener('visibilitychange', function() {
  if (!document.hidden && !pollingPaused) refreshStatus();
});
refreshStatus();
setInterval(refreshStatus, 2500);
</script>
</body>
</html>
""".trimIndent()
}
