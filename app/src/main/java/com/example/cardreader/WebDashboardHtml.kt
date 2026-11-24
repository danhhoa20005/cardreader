package com.example.cardreader

/**
 * Single-page responsive HTML/CSS/JS control panel served on port 8080 for LAN administration.
 */
object WebDashboardHtml {
    val HTML: String = """
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>CardReader — Điều Khiển TV Box (LAN)</title>
<style>
  :root {
    --bg: #0F172A;
    --surface: #1B2336;
    --surface-elevated: #243047;
    --input: #0B1120;
    --border: #334155;
    --border-strong: #475569;
    --text-primary: #F8FAFC;
    --text-secondary: #CBD5E1;
    --text-muted: #94A3B8;
    --blue: #2563EB;
    --blue-hover: #3B82F6;
    --green: #16A34A;
    --green-soft: #052E16;
    --green-text: #86EFAC;
    --amber: #D97706;
    --amber-soft: #451A03;
    --amber-text: #FDE68A;
    --red: #DC2626;
    --red-soft: #450A0A;
    --red-text: #FECACA;
    --focus-ring: #60A5FA;
    --radius-lg: 14px;
    --radius-md: 10px;
  }
  * { box-sizing: border-box; }
  html { color-scheme: dark; }
  body {
    margin: 0;
    padding: 20px 16px 32px;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    font-size: 14px;
    line-height: 1.5;
    background: var(--bg);
    color: var(--text-primary);
  }
  .wrap { max-width: 1040px; margin: 0 auto; }

  /* Accessible Focus Appearance (WCAG 2.2) */
  :focus-visible {
    outline: 2px solid var(--focus-ring);
    outline-offset: 2px;
  }

  /* Header */
  header {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding-bottom: 16px;
    margin-bottom: 18px;
    border-bottom: 1px solid var(--border);
  }
  .brand { display: flex; align-items: center; gap: 12px; }
  .brand-icon {
    width: 40px; height: 40px; border-radius: 10px;
    background: #172554; border: 1px solid #3B82F6;
    color: #93C5FD; display: grid; place-items: center; flex-shrink: 0;
  }
  h1 { margin: 0; font-size: 19px; font-weight: 700; letter-spacing: -0.01em; }
  .sub { font-size: 13px; color: var(--text-muted); margin-top: 2px; }
  .hdr-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }

  .pill {
    display: inline-flex; align-items: center; gap: 8px;
    min-height: 36px; padding: 6px 12px; border-radius: 999px;
    font-size: 12px; font-weight: 600;
    background: var(--surface); border: 1px solid var(--border);
    color: var(--text-secondary);
  }
  .pill.ok { background: var(--green-soft); border-color: var(--green); color: var(--green-text); }
  .pill.err { background: var(--red-soft); border-color: var(--red); color: var(--red-text); }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: currentColor; }

  /* Layout Grid */
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(310px, 1fr)); gap: 16px; }
  .card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
    padding: 18px;
    margin-bottom: 16px;
  }
  .card-head {
    display: flex; align-items: center; justify-content: space-between; gap: 8px;
    margin-bottom: 14px;
  }
  .card h2 {
    margin: 0; font-size: 15px; font-weight: 600; color: var(--text-primary);
    display: inline-flex; align-items: center; gap: 8px;
  }
  .card h2 svg { color: var(--text-muted); flex-shrink: 0; }

  /* Key-Value Telemetry */
  .kv {
    display: flex; justify-content: space-between; align-items: baseline;
    gap: 12px; padding: 8px 0; border-bottom: 1px solid #26334D; font-size: 13px;
  }
  .kv:last-child { border-bottom: none; }
  .kv .k { color: var(--text-muted); }
  .kv .v { font-weight: 600; text-align: right; word-break: break-word; font-variant-numeric: tabular-nums; }
  .v.ok { color: var(--green-text); }
  .v.warn { color: var(--amber-text); }
  .v.err { color: var(--red-text); }

  /* Forms */
  .field { margin-bottom: 12px; }
  label {
    display: block; font-size: 13px; font-weight: 500;
    color: var(--text-secondary); margin-bottom: 6px;
  }
  .input-wrap { position: relative; display: flex; align-items: center; }
  input[type="text"], input[type="password"] {
    width: 100%;
    min-height: 44px;
    padding: 10px 12px;
    border-radius: var(--radius-md);
    border: 1px solid var(--border);
    background: var(--input);
    color: var(--text-primary);
    font-size: 16px; /* >= 16px prevents mobile auto-zoom */
    transition: border-color .15s ease;
  }
  input.has-toggle { padding-right: 48px; }
  input:hover { border-color: var(--border-strong); }
  input.invalid { border-color: var(--red); }
  .field-err {
    margin-top: 4px; font-size: 12px; color: var(--red-text); display: none;
  }
  .field-err.visible { display: block; }

  .eye-btn {
    position: absolute; right: 4px;
    width: 40px; height: 40px; min-width: 40px; min-height: 40px;
    padding: 0; border-radius: 8px;
    background: transparent; border: none; color: var(--text-muted);
    display: grid; place-items: center; cursor: pointer;
  }
  .eye-btn:hover { background: var(--surface-elevated); color: var(--text-primary); }

  /* Buttons (min-height 44px touch target) */
  .btn-row { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 12px; }
  button {
    display: inline-flex; align-items: center; justify-content: center; gap: 8px;
    min-height: 44px; padding: 10px 16px; border-radius: var(--radius-md);
    border: 1px solid var(--border); background: var(--surface-elevated);
    color: var(--text-primary); font-size: 13px; font-weight: 600;
    cursor: pointer; touch-action: manipulation;
    transition: background-color .15s ease, opacity .15s ease;
  }
  button:hover:not(:disabled) { background: #334155; }
  button:disabled { opacity: 0.5; cursor: not-allowed; }
  .btn-sm { min-height: 36px; padding: 6px 12px; font-size: 12px; }
  .btn-primary { background: var(--blue); border-color: var(--blue); color: #FFFFFF; }
  .btn-primary:hover:not(:disabled) { background: var(--blue-hover); border-color: var(--blue-hover); }
  .btn-green { background: var(--green); border-color: var(--green); color: #FFFFFF; }
  .btn-green:hover:not(:disabled) { background: #15803D; border-color: #15803D; }
  .btn-amber { background: var(--amber-soft); border-color: var(--amber); color: var(--amber-text); }
  .btn-amber:hover:not(:disabled) { background: #78350F; }

  .checkline {
    display: inline-flex; align-items: center; gap: 10px;
    min-height: 44px; margin-top: 6px; font-size: 13px; color: var(--text-secondary);
    cursor: pointer;
  }
  .checkline input[type="checkbox"] { width: 18px; height: 18px; accent-color: var(--blue-hover); }

  /* Alert / Toast Banner */
  .notice {
    display: none; align-items: center; gap: 10px;
    padding: 12px 14px; border-radius: var(--radius-md);
    margin-bottom: 16px; font-size: 13px; font-weight: 500;
    background: var(--surface); border: 1px solid var(--blue);
  }
  .notice.visible { display: flex; }
  .notice.ok { background: var(--green-soft); border-color: var(--green); color: var(--green-text); }
  .notice.err { background: var(--red-soft); border-color: var(--red); color: var(--red-text); }

  /* Logs */
  .logs {
    background: #070B14; border: 1px solid var(--border); border-radius: var(--radius-md);
    padding: 12px; height: 210px; overflow-y: auto;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 12px; line-height: 1.55; color: #CBD5E1; white-space: pre-wrap;
  }

  .lock-box { max-width: 420px; margin: 48px auto; }
  .hidden { display: none !important; }
  .divider { border: none; border-top: 1px solid var(--border); margin: 16px 0; }
  .sync-meta { font-size: 12px; color: var(--text-muted); font-variant-numeric: tabular-nums; }

  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after { transition: none !important; animation: none !important; }
  }
</style>
</head>
<body>
<div class="wrap">
  <header>
    <div class="brand">
      <div class="brand-icon" aria-hidden="true">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/></svg>
      </div>
      <div>
        <h1>CardReader — Điều Khiển TV Box</h1>
        <div class="sub" id="hdrSub">Đang kết nối tới TV Box...</div>
      </div>
    </div>

    <div class="hdr-actions">
      <span class="pill" id="pillStatus" role="status" aria-live="polite">
        <span class="dot" aria-hidden="true"></span>
        <span id="pillText">Đang kết nối</span>
      </span>
      <button id="btnTogglePoll" class="btn-sm" type="button" onclick="togglePolling()">Tạm dừng tự làm mới</button>
      <button id="btnLogoutPin" class="btn-sm hidden" type="button" onclick="logoutPin()">Khóa PIN</button>
    </div>
  </header>

  <div id="toast" class="notice" role="status" aria-live="polite"></div>

  <main>
    <!-- Lock Screen -->
    <section id="lockScreen" class="card lock-box hidden" aria-label="Xác thực mã PIN">
      <div class="card-head">
        <h2>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          Nhập mã PIN quản trị TV Box
        </h2>
      </div>
      <p style="font-size:13px;color:var(--text-muted);margin:0 0 14px">
        Trang điều khiển LAN đang được bảo vệ bằng mã PIN.
      </p>
      <div class="field">
        <label for="pinInput">Mã PIN</label>
        <div class="input-wrap">
          <input id="pinInput" class="has-toggle" type="password" autocomplete="current-password" placeholder="Nhập mã PIN..." onkeydown="if(event.key==='Enter')submitPin()" />
          <button type="button" class="eye-btn" aria-label="Hiện mã PIN" aria-pressed="false" onclick="toggleVis('pinInput', this)">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
          </button>
        </div>
      </div>
      <div class="btn-row">
        <button id="btnSubmitPin" class="btn-primary" style="width:100%" type="button" onclick="submitPin()">Mở khóa</button>
      </div>
    </section>

    <!-- Main Dashboard -->
    <div id="mainApp" class="hidden">
      <div class="grid">
        <section class="card" aria-label="Trạng thái TV Box">
          <div class="card-head">
            <h2>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
              Trạng thái TV Box
            </h2>
            <span class="sync-meta" id="syncTime">—</span>
          </div>
          <div class="kv"><span class="k">Màn hình hiện tại</span><span class="v" id="stScreen">—</span></div>
          <div class="kv"><span class="k">Trạng thái đăng nhập</span><span class="v" id="stLogin">—</span></div>
          <div class="kv"><span class="k">Tài khoản cấu hình</span><span class="v" id="stUser">—</span></div>
          <div class="kv"><span class="k">URL WebView đang mở</span><span class="v" id="stActiveUrl">—</span></div>
          <div class="kv"><span class="k">Mạng / IP LAN</span><span class="v" id="stNet">—</span></div>
          <div class="kv"><span class="k">Tên miền mDNS</span><span class="v" id="stMdns">—</span></div>
          <div class="kv"><span class="k">Phiên bản App</span><span class="v" id="stVersion">—</span></div>
          <div class="kv"><span class="k">Cảnh báo gần nhất</span><span class="v" id="stError">—</span></div>
        </section>

        <section class="card" aria-label="Điều khiển từ xa và Cập nhật">
          <div class="card-head">
            <h2>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
              Điều khiển từ xa tức thì
            </h2>
          </div>
          <div class="btn-row" style="margin-top:0">
            <button class="btn-green" type="button" onclick="sendCmd('connect', this)">Kết nối / Mở WebView</button>
            <button class="btn-primary" type="button" onclick="sendCmd('reload_webview', this)">Tải lại trang Web</button>
            <button class="btn-amber" type="button" onclick="sendCmd('relogin', this)">Đăng nhập lại</button>
            <button type="button" onclick="sendCmd('open_settings', this)">Về màn hình Cài đặt</button>
            <button type="button" onclick="sendCmd('stop_countdown', this)">Dừng đếm ngược</button>
          </div>

          <hr class="divider" />
          <div class="card-head" style="margin-bottom:8px">
            <h2>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              Nâng cấp OTA qua GitHub Releases
            </h2>
          </div>
          <div style="font-size:13px;color:var(--text-secondary);margin-bottom:10px" id="stUpdate">Chưa kiểm tra cập nhật.</div>
          <div class="btn-row" style="margin-top:0">
            <button class="btn-primary" type="button" onclick="sendCmd('check_update', this)">Kiểm tra bản mới</button>
            <button id="btnInstallUpdate" class="btn-green hidden" type="button" onclick="sendCmd('install_update', this)">Tải &amp; Cài bản mới lên Box</button>
          </div>
        </section>
      </div>

      <section class="card" aria-label="Cấu hình tài khoản và hệ thống">
        <div class="card-head">
          <h2>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
            Cấu hình tài khoản &amp; hệ thống trên TV Box
          </h2>
        </div>

        <div class="grid">
          <div class="field">
            <label for="cfgUser">Tên đăng nhập điểm danh</label>
            <input id="cfgUser" type="text" autocomplete="username" placeholder="Nhập tài khoản..." onblur="validateUserField()" />
            <div id="errUser" class="field-err">Vui lòng nhập tên đăng nhập.</div>
          </div>

          <div class="field">
            <label for="cfgPass">Mật khẩu (để trống nếu giữ nguyên)</label>
            <div class="input-wrap">
              <input id="cfgPass" class="has-toggle" type="password" autocomplete="new-password" placeholder="••••••" />
              <button type="button" class="eye-btn" aria-label="Hiện mật khẩu" aria-pressed="false" onclick="toggleVis('cfgPass', this)">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              </button>
            </div>
          </div>

          <div class="field">
            <label for="cfgTargetUrl">URL Trang điểm danh (WebView)</label>
            <input id="cfgTargetUrl" type="text" placeholder="http://diemdanhhs.com" />
          </div>

          <div class="field">
            <label for="cfgMdns">Tên miền LAN mDNS (cổng 8080)</label>
            <input id="cfgMdns" type="text" placeholder="diemdanh.local" />
          </div>

          <div class="field">
            <label for="cfgRepo">GitHub Repository (owner/repo)</label>
            <input id="cfgRepo" type="text" placeholder="vd: username/cardreader" onblur="validateRepoField()" />
            <div id="errRepo" class="field-err">Định dạng đúng phải là owner/repo (vd: acme/cardreader).</div>
          </div>

          <div class="field">
            <label for="cfgPin">Đặt mã PIN quản trị LAN mới (tùy chọn)</label>
            <div class="input-wrap">
              <input id="cfgPin" class="has-toggle" type="password" autocomplete="new-password" placeholder="Để trống nếu giữ nguyên" />
              <button type="button" class="eye-btn" aria-label="Hiện mã PIN mới" aria-pressed="false" onclick="toggleVis('cfgPin', this)">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              </button>
            </div>
            <label class="checkline" for="cfgClearPin">
              <input id="cfgClearPin" type="checkbox" /> Xóa mã PIN (truy cập nhanh không cần PIN)
            </label>
          </div>
        </div>

        <div class="btn-row">
          <button id="btnSaveOnly" class="btn-primary" type="button" onclick="saveConfig(false, this)">Lưu cấu hình</button>
          <button id="btnSaveApply" class="btn-green" type="button" onclick="saveConfig(true, this)">Lưu &amp; Áp dụng vào WebView ngay</button>
        </div>
      </section>

      <section class="card" aria-label="Nhật ký hoạt động">
        <div class="card-head">
          <h2>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>
            Nhật ký hoạt động TV Box (Live)
          </h2>
          <button class="btn-sm" type="button" onclick="copyLogs()">Sao chép nhật ký</button>
        </div>
        <div id="logBox" class="logs">Đang tải nhật ký...</div>
      </section>
    </div>
  </main>
</div>

<script>
let formInitialized = false;
let adminToken = localStorage.getItem('cr_admin_token') || '';
let pollingPaused = false;
let pollTimer = null;

function showNotice(msg, isError) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.className = 'notice visible ' + (isError ? 'err' : 'ok');
  clearTimeout(window.__toastTimer);
  window.__toastTimer = setTimeout(() => { el.classList.remove('visible'); }, 5000);
}

function toggleVis(inputId, btn) {
  const input = document.getElementById(inputId);
  const showing = input.type === 'text';
  input.type = showing ? 'password' : 'text';
  btn.setAttribute('aria-pressed', (!showing).toString());
}

function validateUserField() {
  const input = document.getElementById('cfgUser');
  const err = document.getElementById('errUser');
  const ok = input.value.trim().length > 0;
  input.classList.toggle('invalid', !ok);
  err.classList.toggle('visible', !ok);
  return ok;
}

function validateRepoField() {
  const input = document.getElementById('cfgRepo');
  const err = document.getElementById('errRepo');
  const v = input.value.trim();
  const ok = !v || /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(v) || v.includes('github.com/');
  input.classList.toggle('invalid', !ok);
  err.classList.toggle('visible', !ok);
  return ok;
}

async function apiFetch(url, opts = {}) {
  const headers = Object.assign({ 'Content-Type': 'application/json' }, opts.headers || {});
  if (adminToken) headers['X-Admin-Token'] = adminToken;
  const res = await fetch(url, Object.assign({}, opts, { headers }));
  const data = await res.json().catch(() => ({}));
  return { status: res.status, data };
}

async function submitPin() {
  const btn = document.getElementById('btnSubmitPin');
  const pin = document.getElementById('pinInput').value;
  btn.disabled = true;
  try {
    const { status, data } = await apiFetch('/api/auth', {
      method: 'POST',
      body: JSON.stringify({ pin })
    });
    if (status === 200 && data.ok) {
      adminToken = data.token || '';
      if (adminToken) localStorage.setItem('cr_admin_token', adminToken);
      formInitialized = false;
      refreshStatus();
    } else {
      showNotice(data.message || 'Mã PIN không đúng', true);
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
  const prevText = btn ? btn.textContent : '';
  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Đang gửi...';
  }
  try {
    const { status, data } = await apiFetch('/api/command', {
      method: 'POST',
      body: JSON.stringify({ action })
    });
    if (status === 401) {
      refreshStatus();
      return;
    }
    showNotice(data.message || 'Đã gửi lệnh', !data.ok);
    setTimeout(refreshStatus, 400);
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.textContent = prevText;
    }
  }
}

async function saveConfig(applyAndConnect, btn) {
  if (applyAndConnect && !validateUserField()) {
    document.getElementById('cfgUser').focus();
    return;
  }
  if (!validateRepoField()) {
    document.getElementById('cfgRepo').focus();
    return;
  }

  const prevText = btn ? btn.textContent : '';
  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Đang lưu...';
  }
  try {
    const payload = {
      username: document.getElementById('cfgUser').value.trim(),
      password: document.getElementById('cfgPass').value,
      targetUrl: document.getElementById('cfgTargetUrl').value.trim(),
      mdnsHostname: document.getElementById('cfgMdns').value.trim(),
      githubRepo: document.getElementById('cfgRepo').value.trim(),
      adminPin: document.getElementById('cfgPin').value.trim(),
      clearPin: document.getElementById('cfgClearPin').checked,
      applyAndConnect
    };
    const { status, data } = await apiFetch('/api/config', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    if (status === 401) {
      refreshStatus();
      return;
    }
    if (data.ok) {
      document.getElementById('cfgPass').value = '';
      document.getElementById('cfgPin').value = '';
      document.getElementById('cfgClearPin').checked = false;
    }
    showNotice(data.message || 'Đã cập nhật cấu hình', !data.ok);
    setTimeout(refreshStatus, 400);
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.textContent = prevText;
    }
  }
}

function copyLogs() {
  const txt = document.getElementById('logBox').textContent || '';
  if (navigator.clipboard) {
    navigator.clipboard.writeText(txt).then(() => showNotice('Đã sao chép nhật ký', false));
  }
}

function togglePolling() {
  pollingPaused = !pollingPaused;
  document.getElementById('btnTogglePoll').textContent = pollingPaused
    ? 'Tiếp tục tự làm mới'
    : 'Tạm dừng tự làm mới';
  if (!pollingPaused) refreshStatus();
}

function renderStatus(s) {
  if (s.locked) {
    document.getElementById('lockScreen').classList.remove('hidden');
    document.getElementById('mainApp').classList.add('hidden');
    document.getElementById('btnLogoutPin').classList.add('hidden');
    document.getElementById('hdrSub').textContent = 'Phiên bản v' + (s.versionName || '');
    return;
  }

  document.getElementById('lockScreen').classList.add('hidden');
  document.getElementById('mainApp').classList.remove('hidden');
  document.getElementById('btnLogoutPin').classList.toggle('hidden', !s.hasAdminPin);

  document.getElementById('hdrSub').textContent =
    (s.controlUrlLocal || '') + (s.lanIp ? ('  •  http://' + s.lanIp + ':8080') : '');
  document.getElementById('syncTime').textContent = 'Cập nhật ' + new Date().toLocaleTimeString('vi-VN');

  const screenLabel = s.currentScreen === 'WEBVIEW'
    ? 'Đang chạy WebView Điểm danh'
    : ('Màn hình Cài đặt' + (s.countdownSeconds >= 0 ? (' (' + s.countdownSeconds + 's)') : ''));
  document.getElementById('stScreen').textContent = screenLabel;

  const loginEl = document.getElementById('stLogin');
  const loginMap = {
    'LOGGED_IN': ['Đã đăng nhập', 'v ok'],
    'LOGGING_IN': ['Đang đăng nhập...', 'v warn'],
    'LOADING': ['Đang tải trang...', 'v warn'],
    'FAILED': ['Đăng nhập lỗi', 'v err'],
    'IDLE': ['Chờ kết nối', 'v']
  };
  const [loginLabel, loginCls] = loginMap[s.loginState] || [s.loginState || 'IDLE', 'v'];
  loginEl.textContent = loginLabel;
  loginEl.className = loginCls;

  document.getElementById('stUser').textContent = s.username
    ? (s.username + (s.hasPassword ? ' (đã lưu mật khẩu)' : ' (chưa có mật khẩu)'))
    : 'Chưa nhập';
  document.getElementById('stActiveUrl').textContent = s.activeWebUrl || s.targetUrl || '—';
  document.getElementById('stNet').textContent =
    (s.networkConnected ? 'Có mạng' : 'Mất mạng') + (s.lanIp ? (' (' + s.lanIp + ')') : '');
  document.getElementById('stMdns').textContent = (s.mdnsHostname || 'diemdanh.local') + ':8080';
  document.getElementById('stVersion').textContent = 'v' + (s.versionName || '1.0.0');

  const errEl = document.getElementById('stError');
  errEl.textContent = s.lastError || 'Không có';
  errEl.className = s.lastError ? 'v err' : 'v';

  const updText = s.updateMessage || ('Trạng thái: ' + (s.updateState || 'IDLE'));
  document.getElementById('stUpdate').textContent =
    s.updateState === 'DOWNLOADING' ? (updText + ' (' + (s.downloadProgress || 0) + '%)') : updText;
  document.getElementById('btnInstallUpdate').classList.toggle('hidden', !s.canInstallUpdate);

  if (!formInitialized) {
    document.getElementById('cfgUser').value = s.username || '';
    document.getElementById('cfgTargetUrl').value = s.targetUrl || 'http://diemdanhhs.com';
    document.getElementById('cfgMdns').value = s.mdnsHostname || 'diemdanh.local';
    document.getElementById('cfgRepo').value = s.githubRepo || '';
    formInitialized = true;
  }

  const logBox = document.getElementById('logBox');
  const logs = s.logs || [];
  logBox.textContent = logs.length ? logs.join('\n') : 'Chưa có nhật ký.';
  logBox.scrollTop = logBox.scrollHeight;
}

async function refreshStatus() {
  if (document.hidden || pollingPaused) return;
  try {
    const { status, data } = await apiFetch('/api/status');
    const pill = document.getElementById('pillStatus');
    const pillText = document.getElementById('pillText');
    if (status === 200) {
      pillText.textContent = data.networkConnected ? 'TV Box Online' : 'Box Offline';
      pill.className = 'pill ' + (data.networkConnected ? 'ok' : 'err');
      renderStatus(data);
    }
  } catch (e) {
    const pill = document.getElementById('pillStatus');
    document.getElementById('pillText').textContent = 'Mất kết nối tới Box';
    pill.className = 'pill err';
  }
}

document.addEventListener('visibilitychange', () => {
  if (!document.hidden && !pollingPaused) refreshStatus();
});

refreshStatus();
pollTimer = setInterval(refreshStatus, 2500);
</script>
</body>
</html>
""".trimIndent()
}
