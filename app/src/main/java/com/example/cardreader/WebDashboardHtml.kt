package com.example.cardreader

/**
 * Trung tâm Điều hành Toàn diện & Tích hợp Đầu đọc RFID trên cổng 8888
 * (`http://127.0.0.1:8888` & `http://diemdanh.local:8888`), thiết kế theo chuẩn UI/UX Pro Max (WCAG AA).
 */
object WebDashboardHtml {
    val HTML: String = """
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>CardReader: Trung Tâm Điều Khiển Localhost & Đầu Đọc RFID (8888)</title>
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
  ::selection { background: rgba(37, 99, 235, 0.38); color: #F8FAFC; }
  body {
    margin: 0;
    padding: 18px 16px 44px;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    font-size: 14px;
    line-height: 1.5;
    background: var(--bg);
    color: var(--text-primary);
    -webkit-font-smoothing: antialiased;
  }
  :focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
  .wrap { max-width: 1140px; margin: 0 auto; }

  header {
    display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
    gap: 12px; padding-bottom: 14px; margin-bottom: 16px; border-bottom: 1px solid var(--border);
  }
  .brand-row { display: flex; align-items: center; gap: 12px; }
  .brand-mark {
    display: inline-flex; align-items: center; justify-content: center;
    width: 42px; height: 42px; border-radius: var(--radius-md);
    background: var(--blue-soft); border: 1px solid #1E40AF; color: var(--blue-text);
    flex-shrink: 0;
  }
  h1 { margin: 0; font-size: 18px; font-weight: 700; letter-spacing: -0.01em; }
  .sub { font-size: 12.5px; color: var(--text-muted); font-variant-numeric: tabular-nums; margin-top: 2px; }
  .hdr-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
  .pill {
    display: inline-flex; align-items: center; gap: 8px;
    min-height: 36px; padding: 5px 12px; border-radius: 999px;
    font-size: 12px; font-weight: 600;
    background: var(--surface); border: 1px solid var(--border); color: var(--text-secondary);
  }
  .pill.ok { background: var(--green-soft); border-color: var(--green); color: var(--green-text); }
  .pill.warn { background: var(--amber-soft); border-color: var(--amber); color: var(--amber-text); }
  .pill.err { background: var(--red-soft); border-color: var(--red); color: var(--red-text); }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: currentColor; flex-shrink: 0; }
  .pill.ok .dot { box-shadow: 0 0 0 3px rgba(134, 239, 172, 0.2); }

  .kpi-grid {
    display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px;
  }
  .kpi {
    background: var(--surface); border: 1px solid var(--border);
    border-radius: var(--radius-md); padding: 12px 14px;
  }
  .kpi-label { font-size: 12px; color: var(--text-muted); margin-bottom: 4px; }
  .kpi-val {
    font-size: 15px; font-weight: 700; color: var(--text-primary);
    font-variant-numeric: tabular-nums; word-break: break-word;
  }
  .kpi-sub { font-size: 12px; color: var(--text-secondary); margin-top: 3px; font-variant-numeric: tabular-nums; }

  .tabs {
    display: flex; flex-wrap: wrap; gap: 8px;
    margin-bottom: 16px; padding: 6px;
    background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md);
  }
  .tab-btn {
    flex: 1 1 auto; min-height: 40px; padding: 8px 14px; border-radius: var(--radius-sm);
    border: 1px solid transparent; background: transparent; color: var(--text-secondary);
    font-size: 13.5px; font-weight: 600; cursor: pointer; transition: all 140ms ease;
  }
  .tab-btn:hover { background: var(--surface-elevated); color: var(--text-primary); }
  .tab-btn.active {
    background: var(--blue); border-color: #3B82F6; color: #FFFFFF;
  }
  .tab-panel { display: none; }
  .tab-panel.active { display: block; }

  .grid-2 { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
  .form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
  .card {
    background: var(--surface); border: 1px solid var(--border);
    border-radius: var(--radius-lg); padding: 18px; margin-bottom: 16px;
  }
  .card-head {
    display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
    gap: 8px; margin-bottom: 14px;
  }
  .card h2 { margin: 0; font-size: 15px; font-weight: 600; color: var(--text-primary); }
  .card-desc { margin: -6px 0 14px; font-size: 12.5px; color: var(--text-muted); }

  .field { margin-bottom: 12px; }
  label { display: block; font-size: 13px; font-weight: 500; color: var(--text-secondary); margin-bottom: 6px; }
  .row-inline { display: flex; align-items: stretch; gap: 8px; flex-wrap: wrap; }
  .row-inline > input, .row-inline > select { flex: 1 1 200px; min-width: 0; }

  input[type="text"], input[type="password"], select {
    width: 100%; min-height: 44px; padding: 9px 13px;
    border-radius: var(--radius-sm); border: 1px solid var(--border);
    background: var(--input); color: var(--text-primary); font-size: 15px;
    caret-color: var(--focus-ring); transition: border-color 140ms ease;
  }
  input::placeholder { color: var(--text-muted); }
  input:focus, select:focus { border-color: var(--focus-ring); }

  .check-row {
    display: flex; align-items: center; gap: 10px;
    padding: 10px 12px; border-radius: var(--radius-sm);
    background: var(--input); border: 1px solid var(--border-subtle);
    margin-bottom: 8px; cursor: pointer; font-size: 13px; color: var(--text-secondary);
  }
  .check-row input[type="checkbox"] { width: 18px; height: 18px; accent-color: var(--blue); flex-shrink: 0; }

  button {
    display: inline-flex; align-items: center; justify-content: center; gap: 8px;
    min-height: 42px; padding: 9px 15px; border-radius: var(--radius-sm);
    border: 1px solid transparent; font-size: 13.5px; font-weight: 600;
    cursor: pointer; color: #FFFFFF; background: var(--blue);
    transition: background-color 140ms ease, transform 80ms ease;
    user-select: none;
  }
  button:hover { background: var(--blue-hover); }
  button:active { transform: scale(0.99); }
  button:disabled { opacity: 0.55; cursor: not-allowed; transform: none; }
  button.sec { background: var(--surface-elevated); border-color: var(--border); color: var(--text-primary); }
  button.sec:hover { background: var(--surface-hover); }
  button.ok { background: var(--green); color: #FFFFFF; }
  button.ok:hover { background: var(--green-hover); }
  button.danger { background: var(--red-soft); border-color: var(--red); color: var(--red-text); }
  button.sm { min-height: 36px; padding: 6px 11px; font-size: 12px; }

  .preset-row { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
  .chip-btn {
    min-height: 36px; padding: 6px 12px; font-size: 12.5px; border-radius: 999px;
    background: var(--input); border: 1px solid var(--border); color: var(--text-secondary);
  }
  .chip-btn:hover { background: var(--surface-elevated); color: var(--text-primary); }
  .chip-btn.active { background: var(--blue-soft); border-color: var(--focus-ring); color: #FFFFFF; }

  .route-group-title {
    font-size: 11.5px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em;
    color: var(--text-muted); margin: 12px 0 6px;
  }
  .route-grid {
    display: grid; grid-template-columns: repeat(auto-fill, minmax(185px, 1fr)); gap: 8px;
  }
  .route-btn {
    justify-content: flex-start; text-align: left; min-height: 42px; padding: 8px 12px;
    background: var(--input); border: 1px solid var(--border); color: var(--text-secondary);
    font-size: 13px; font-weight: 600;
  }
  .route-btn:hover { background: var(--surface-elevated); color: var(--text-primary); border-color: #475569; }
  .route-btn.active {
    background: var(--blue-soft); border-color: var(--focus-ring); color: #FFFFFF;
  }

  .btn-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }

  table { width: 100%; border-collapse: collapse; font-size: 13px; }
  th, td {
    padding: 9px 10px; text-align: left; border-bottom: 1px solid var(--border-subtle);
    font-variant-numeric: tabular-nums;
  }
  th { color: var(--text-muted); font-weight: 600; font-size: 12px; background: var(--input); }
  .table-wrap {
    max-height: 290px; overflow-y: auto; border: 1px solid var(--border); border-radius: var(--radius-sm);
  }
  .badge {
    display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 11.5px; font-weight: 700;
  }
  .badge.ok { background: var(--green-soft); color: var(--green-text); border: 1px solid var(--green); }
  .badge.err { background: var(--red-soft); color: var(--red-text); border: 1px solid var(--red); }

  .screen-stage {
    position: relative; background: #050811; border: 1px solid var(--border);
    border-radius: var(--radius-md); overflow: hidden; text-align: center; min-height: 280px;
    display: flex; align-items: center; justify-content: center;
  }
  .screen-stage img {
    max-width: 100%; max-height: 620px; display: block; cursor: crosshair; user-select: none;
  }

  .msg {
    margin-bottom: 14px; padding: 11px 14px; border-radius: var(--radius-sm); font-size: 13px;
    display: none; font-weight: 500;
  }
  .msg.show { display: block; }
  .msg.ok { background: var(--green-soft); border: 1px solid var(--green); color: var(--green-text); }
  .msg.err { background: var(--red-soft); border: 1px solid var(--red); color: var(--red-text); }

  .progress-track {
    width: 100%; height: 10px; border-radius: 999px; background: var(--input);
    border: 1px solid var(--border); overflow: hidden; margin: 10px 0; display: none;
  }
  .progress-fill { height: 100%; width: 0%; background: var(--blue); transition: width 180ms ease; }

  pre.code-box, .logs {
    background: var(--input); border: 1px solid var(--border); border-radius: var(--radius-sm);
    padding: 12px; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 12.5px; line-height: 1.55; color: var(--text-secondary);
    overflow-x: auto; white-space: pre-wrap; word-break: break-word; margin: 8px 0 14px;
  }
  .logs { max-height: 280px; overflow-y: auto; }
  .hidden { display: none !important; }

  @media (max-width: 860px) {
    .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .grid-2, .form-grid { grid-template-columns: 1fr; }
  }
  @media (max-width: 520px) {
    .btn-grid { grid-template-columns: 1fr; }
  }
</style>
</head>
<body>
<div class="wrap">
  <header>
    <div class="brand-row">
      <div class="brand-mark" aria-hidden="true">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="2" y="4" width="20" height="16" rx="2"/><path d="M7 15h0M2 9.5h20"/>
        </svg>
      </div>
      <div>
        <h1>Trung Tâm Điều Khiển Localhost & Đầu Đọc RFID</h1>
        <div class="sub" id="subTitle">Localhost: http://127.0.0.1:8888 • Đang đồng bộ trạng thái...</div>
      </div>
    </div>
    <div class="hdr-actions">
      <div id="connPill" class="pill"><span class="dot"></span><span id="connText">Đang kết nối...</span></div>
      <button type="button" id="btnListenerMode" class="sec sm" onclick="toggleKeyboardRfidCapture()">Bắt phím RFID trên trình duyệt: TẮT</button>
      <button type="button" id="btnLockSession" class="sec sm hidden" onclick="logoutPin()">Khóa phiên</button>
    </div>
  </header>

  <div id="globalToast" class="msg" role="status" aria-live="polite"></div>

  <!-- Khung xác thực mã PIN (chỉ hiện trên LAN khi cài PIN; localhost tự động mở khóa) -->
  <div id="authCard" class="card hidden" style="max-width: 460px; margin: 32px auto;">
    <div class="card-head"><h2>Mở khóa quản trị mạng LAN</h2></div>
    <p class="card-desc">Thiết bị đang bật bảo vệ mã PIN cho truy cập từ mạng LAN.</p>
    <div class="field">
      <label for="pinInput">Mã PIN quản trị</label>
      <input type="password" id="pinInput" placeholder="Nhập mã PIN..." onkeydown="if(event.key==='Enter') submitPin()" />
    </div>
    <button type="button" onclick="submitPin()" style="width: 100%;">Mở khóa điều khiển</button>
  </div>

  <div id="mainContent" class="hidden">
    <!-- 4 Thẻ KPI Thời Gian Thực -->
    <div class="kpi-grid">
      <div class="kpi">
        <div class="kpi-label">Địa chỉ Localhost & LAN</div>
        <div class="kpi-val" id="kpiHost">127.0.0.1:8888</div>
        <div class="kpi-sub" id="kpiLanSub">Đang kiểm tra IP...</div>
      </div>
      <div class="kpi">
        <div class="kpi-label">Phiên Đăng Nhập Trường Học</div>
        <div class="kpi-val" id="kpiSchool">Chưa đăng nhập</div>
        <div class="kpi-sub" id="kpiAccount">--</div>
      </div>
      <div class="kpi">
        <div class="kpi-label">Đầu Đọc Thẻ RFID</div>
        <div class="kpi-val" id="kpiRfidCount">0 lượt quẹt</div>
        <div class="kpi-sub" id="kpiRfidLast">Chưa có thẻ quẹt</div>
      </div>
      <div class="kpi">
        <div class="kpi-label">Màn Hình Đang Mở & Bản Build</div>
        <div class="kpi-val" id="kpiRoute">Màn hình Cài đặt</div>
        <div class="kpi-sub" id="kpiVersion">--</div>
      </div>
    </div>

    <!-- Thanh chuyển Tab điều hành -->
    <nav class="tabs" aria-label="Chuyên mục điều khiển">
      <button type="button" class="tab-btn active" data-tab="tabRfid" onclick="switchTab('tabRfid')">1. Đầu Đọc RFID & Mọi Chức Năng</button>
      <button type="button" class="tab-btn" data-tab="tabLive" onclick="switchTab('tabLive')">2. Màn Hình Trực Tiếp (Live View)</button>
      <button type="button" class="tab-btn" data-tab="tabAuth" onclick="switchTab('tabAuth')">3. Đăng Nhập, Cấu Hình & OTA</button>
      <button type="button" class="tab-btn" data-tab="tabApi" onclick="switchTab('tabApi')">4. API Localhost RFID & Nhật Ký</button>
    </nav>

    <!-- TAB 1: TRUNG TÂM RFID & TOÀN BỘ CHỨC NĂNG -->
    <section id="tabRfid" class="tab-panel active">
      <div class="grid-2">
        <div class="card">
          <div class="card-head">
            <h2>Bàn Quẹt Thẻ RFID & Kiểm Thử Tức Thì</h2>
            <button type="button" class="ok sm" onclick="navigateRoute('/check-in-out')">Vào Trang Quẹt Thẻ</button>
          </div>
          <p class="card-desc">Nhập hoặc quẹt thẻ từ đầu đọc USB RFID để ghi nhận điểm danh/đón trả ngay lập tức qua <code>/api/rfid/scan</code>.</p>

          <div class="field">
            <label for="rfidInput">Mã thẻ RFID (UID / Mã học sinh)</label>
            <div class="row-inline">
              <input type="text" id="rfidInput" autocomplete="off" placeholder="Quẹt thẻ hoặc nhập mã (VD: 0012345678)..." onkeydown="if(event.key==='Enter') sendRfidCard()" />
              <button type="button" id="btnSendRfid" class="ok" onclick="sendRfidCard()">Quẹt Thẻ Ngay</button>
            </div>
          </div>

          <div class="form-grid">
            <div class="field">
              <label for="rfidMode">Chế độ gửi thẻ RFID</label>
              <select id="rfidMode">
                <option value="auto">Tự động (API + Thông báo trên TV)</option>
                <option value="keyboard">Giả lập bàn phím (useCardScanner)</option>
                <option value="backend_only">Gửi thẳng API Máy chủ (Ngầm)</option>
              </select>
            </div>
            <div class="field">
              <label>Danh sách thẻ đã đăng ký của trường</label>
              <button type="button" class="sec" style="width:100%;" onclick="loadSchoolCards()">Tải Danh Sách Mã Thẻ</button>
            </div>
          </div>

          <div id="knownCardsBox" class="hidden" style="margin-top: 6px;">
            <label id="knownCardsLabel">Danh sách mã thẻ (bấm để quẹt thử):</label>
            <div id="knownCardsList" class="preset-row" style="max-height: 110px; overflow-y: auto; padding: 6px; background: var(--input); border-radius: 8px; border: 1px solid var(--border-subtle);"></div>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <h2>Điều Khiển Nhanh Phiên Kiosk</h2>
          </div>
          <div class="btn-grid" style="margin-bottom: 12px;">
            <button type="button" class="ok" onclick="sendCommand('connect')">Mở Điểm Danh Ngay</button>
            <button type="button" class="sec" onclick="navigateRoute('/check-in-out')">Mở Khung Quẹt Thẻ</button>
            <button type="button" class="sec" onclick="sendCommand('reload_webview')">Tải Lại Trang Web</button>
            <button type="button" class="sec" onclick="sendCommand('open_settings')">Về Màn Hình Cài Đặt</button>
            <button type="button" class="sec" onclick="sendCommand('relogin')">Đăng Nhập Lại Tự Động</button>
            <button type="button" class="danger" onclick="logoutAttendanceWeb()">Đăng Xuất Trường Học</button>
          </div>

          <div class="field" style="margin-top: 10px; margin-bottom: 0;">
            <label for="customRouteInput">Mở nhanh đường dẫn hoặc URL bất kỳ trên Kiosk</label>
            <div class="row-inline">
              <input type="text" id="customRouteInput" placeholder="/danh-sach-diem-danh hoặc https://..." onkeydown="if(event.key==='Enter') openCustomRoute()" />
              <button type="button" class="sec" onclick="openCustomRoute()">Chuyển Trang</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Bảng Điều Hướng Toàn Bộ 17 Chức Năng Của Hệ Thống -->
      <div class="card">
        <div class="card-head">
          <h2>Điều Khiển Toàn Bộ Phân Hệ Chức Năng Trên Web Điểm Danh</h2>
          <span class="sub" id="activeRouteBadge">Đang mở: /</span>
        </div>
        <div id="routesContainer"></div>
      </div>

      <!-- Lịch Sử Quẹt Thẻ RFID Thời Gian Thực -->
      <div class="card">
        <div class="card-head">
          <h2>Lịch Sử Quẹt Thẻ RFID Thời Gian Thực</h2>
          <button type="button" class="sec sm" onclick="clearRfidHistory()">Xóa lịch sử thẻ</button>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Thời gian</th>
                <th>Mã thẻ RFID</th>
                <th>Nguồn đọc</th>
                <th>Trạng thái</th>
                <th>Phản hồi từ hệ thống điểm danh</th>
              </tr>
            </thead>
            <tbody id="rfidHistoryBody">
              <tr><td colspan="5" style="color: var(--text-muted); text-align: center;">Chưa có dữ liệu quẹt thẻ RFID trong phiên này.</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- TAB 2: MÀN HÌNH TRỰC TIẾP & CHẠM TỪ XA -->
    <section id="tabLive" class="tab-panel">
      <div class="card">
        <div class="card-head">
          <h2>Màn Hình Kiosk Trực Tiếp (Bấm vào ảnh để chạm điều khiển từ xa)</h2>
          <div class="hdr-actions">
            <button type="button" id="btnAutoLive" class="sec sm" onclick="toggleAutoLive()">Tự làm mới 1.5s: TẮT</button>
            <button type="button" class="sm" onclick="refreshScreenshot()">Chụp Màn Hình Ngay</button>
          </div>
        </div>

        <div class="preset-row" style="margin-bottom: 12px;">
          <button type="button" class="sec sm" onclick="webAction({action:'back'})">◀ Quay Lại</button>
          <button type="button" class="sec sm" onclick="webAction({action:'forward'})">Tiếp Theo ▶</button>
          <button type="button" class="sec sm" onclick="webAction({action:'scroll', dy:-380})">▲ Cuộn Lên</button>
          <button type="button" class="sec sm" onclick="webAction({action:'scroll', dy:380})">▼ Cuộn Xuống</button>
          <button type="button" class="sec sm" onclick="webAction({action:'reload'})">↻ Tải Lại</button>
          <button type="button" class="ok sm" onclick="navigateRoute('/check-in-out')">Vào Quẹt Thẻ RFID</button>
        </div>

        <div class="screen-stage" id="screenStage">
          <div id="screenEmptyMsg" style="padding: 36px 18px; color: var(--text-muted); font-size: 13.5px;">Đang tải khung hình Kiosk trực tiếp...</div>
          <img id="liveScreenImg" src="/api/web/screenshot" class="hidden" alt="Ảnh chụp trực tiếp màn hình Kiosk" onload="this.classList.remove('hidden'); document.getElementById('screenEmptyMsg').classList.add('hidden');" onerror="this.classList.add('hidden'); document.getElementById('screenEmptyMsg').classList.remove('hidden');" onclick="handleScreenClick(event)" />
        </div>

        <div class="grid-2" style="margin-top: 14px;">
          <div class="field" style="margin-bottom:0;">
            <label for="clickTextInput">Bấm nút hoặc mục menu theo nội dung chữ</label>
            <div class="row-inline">
              <input type="text" id="clickTextInput" placeholder="VD: Đăng nhập, Quy định điểm danh..." onkeydown="if(event.key==='Enter') triggerClickByText()" />
              <button type="button" class="sec" onclick="triggerClickByText()">Bấm Nút</button>
            </div>
          </div>
          <div class="field" style="margin-bottom:0;">
            <label for="remoteInputVal">Điền văn bản vào ô nhập đang chọn trên WebView</label>
            <div class="row-inline">
              <input type="text" id="remoteInputVal" placeholder="Nội dung cần nhập..." onkeydown="if(event.key==='Enter') triggerRemoteInput()" />
              <button type="button" class="sec" onclick="triggerRemoteInput()">Điền Chữ</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- TAB 3: ĐĂNG NHẬP, CẤU HÌNH & CẬP NHẬT GITHUB OTA -->
    <section id="tabAuth" class="tab-panel">
      <div class="grid-2">
        <div class="card">
          <div class="card-head"><h2>Đăng Nhập Tài Khoản Trường Học Từ Localhost</h2></div>
          <p class="card-desc">Đăng nhập trực tiếp qua API hoặc đổi hệ thống mà không cần chạm vào màn hình TV Box.</p>

          <div class="field">
            <label>Chọn nhanh máy chủ hệ thống</label>
            <div class="preset-row">
              <button type="button" id="chipDiemDanh" class="chip-btn" onclick="selectPresetUrl('http://diemdanhhs.com')">diemdanhhs.com (Điểm danh)</button>
              <button type="button" id="chipDonTra" class="chip-btn" onclick="selectPresetUrl('https://dontrahs.com')">dontrahs.com (Đón trả)</button>
            </div>
            <input type="text" id="cfgTargetUrl" placeholder="http://diemdanhhs.com hoặc https://dontrahs.com" />
          </div>

          <div class="form-grid">
            <div class="field">
              <label for="cfgUsername">Tài khoản đăng nhập</label>
              <input type="text" id="cfgUsername" autocomplete="username" placeholder="Nhập tên đăng nhập..." />
            </div>
            <div class="field">
              <label for="cfgPassword">Mật khẩu mới (để trống nếu giữ nguyên)</label>
              <input type="password" id="cfgPassword" autocomplete="current-password" placeholder="Nhập mật khẩu..." />
            </div>
          </div>

          <label class="check-row">
            <input type="checkbox" id="cfgAutoCheckIn" />
            <span>Tự động mở thẳng <b>Trang Quẹt Thẻ (/check-in-out)</b> sau khi đăng nhập</span>
          </label>
          <label class="check-row">
            <input type="checkbox" id="cfgHwRfid" />
            <span>Bật bộ nhận đầu đọc thẻ <b>RFID USB / OTG / Bluetooth HID</b> toàn cục</span>
          </label>
          <label class="check-row">
            <input type="checkbox" id="cfgLocalhostBypass" />
            <span>Cho phép <b>localhost (127.0.0.1)</b> điều khiển toàn bộ API không cần mã PIN</span>
          </label>

          <div class="btn-grid" style="margin-top: 12px;">
            <button type="button" class="ok" onclick="loginAttendanceNow()">Đăng Nhập Ngay</button>
            <button type="button" onclick="saveFullConfig(false)">Chỉ Lưu Cấu Hình</button>
          </div>
        </div>

        <div>
          <div class="card">
            <div class="card-head"><h2>Mạng Nội Bộ mDNS & Bảo Mật PIN</h2></div>
            <div class="form-grid">
              <div class="field">
                <label for="cfgMdns">Tên miền mạng LAN (.local)</label>
                <input type="text" id="cfgMdns" placeholder="diemdanh.local" />
              </div>
              <div class="field">
                <label for="cfgAdminPin">Đặt / Đổi mã PIN LAN</label>
                <input type="password" id="cfgAdminPin" placeholder="Để trống nếu không đổi" />
              </div>
            </div>
            <div class="field">
              <label for="cfgGithubRepo">Kho GitHub cập nhật OTA (owner/repo)</label>
              <input type="text" id="cfgGithubRepo" placeholder="danhhoa20005/cardreader" />
            </div>
            <div class="btn-grid">
              <button type="button" class="sec" onclick="saveFullConfig(false)">Lưu Mạng & Bảo Mật</button>
              <button type="button" class="danger" onclick="clearAdminPin()">Xóa Mã PIN LAN</button>
            </div>
          </div>

          <div class="card">
            <div class="card-head">
              <h2>Cập Nhật Phần Mềm OTA Qua GitHub</h2>
              <span class="sub" id="otaBadge">--</span>
            </div>
            <div id="otaStatusText" style="font-size: 13px; color: var(--text-secondary); margin-bottom: 10px;">Chưa kiểm tra cập nhật.</div>
            <div id="otaProgressTrack" class="progress-track"><div id="otaProgressFill" class="progress-fill"></div></div>
            <pre id="otaNotesBox" class="code-box hidden"></pre>
            <div class="btn-grid">
              <button type="button" class="sec" id="btnOtaCheck" onclick="triggerOtaCheck()">Kiểm Tra Bản Mới</button>
              <button type="button" class="ok" id="btnOtaInstall" onclick="triggerOtaInstall()">Tải & Cài Đặt OTA</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- TAB 4: TÀI LIỆU TÍCH HỢP ĐẦU ĐỌC RFID QUA LOCALHOST & NHẬT KÝ -->
    <section id="tabApi" class="tab-panel">
      <div class="card">
        <div class="card-head">
          <h2>Chuẩn Giao Tiếp HTTP API Cho Đầu Đọc RFID Trên Localhost (Cổng 8888)</h2>
          <button type="button" class="sec sm" onclick="copyText(document.getElementById('apiSnippet').innerText)">Sao Chép Ví Dụ API</button>
        </div>
        <p class="card-desc">Mọi phần mềm đọc thẻ RFID (USB Serial, COM, Python, C#, Node.js, dịch vụ nền trên Android/Linux) chạy trên cùng thiết bị có thể gọi thẳng vào <code>http://127.0.0.1:8888</code> không cần mã PIN:</p>
        <pre id="apiSnippet" class="code-box"># 1. Quẹt thẻ RFID (POST JSON hoặc GET query đơn giản - trả kết quả đồng bộ từ máy chủ):
curl -X POST http://127.0.0.1:8888/api/rfid/scan \
  -H "Content-Type: application/json" \
  -d '{"cardId":"0012345678","mode":"auto"}'

# Hoặc gọi GET nhanh cho vi điều khiển / script đơn giản:
curl "http://127.0.0.1:8888/api/rfid/scan?cardId=0012345678"

# 2. Đăng nhập tài khoản trường học và mở thẳng trang Quẹt thẻ (/check-in-out):
curl -X POST http://127.0.0.1:8888/api/web/login \
  -H "Content-Type: application/json" \
  -d '{"username":"ten_tai_khoan","password":"mat_khau","targetUrl":"http://diemdanhhs.com","route":"/check-in-out"}'

# 3. Chuyển tới bất kỳ chức năng nào trên màn hình Kiosk:
curl -X POST http://127.0.0.1:8888/api/web/navigate \
  -H "Content-Type: application/json" \
  -d '{"route":"/danh-sach-diem-danh"}'

# 4. Lấy danh sách mã thẻ RFID của trường & kiểm tra trạng thái tổng thể:
curl http://127.0.0.1:8888/api/rfid/cards
curl http://127.0.0.1:8888/api/status</pre>
      </div>

      <div class="card">
        <div class="card-head">
          <h2>Nhật Ký Hoạt Động Hệ Thống & RFID</h2>
          <div class="hdr-actions">
            <input type="text" id="logSearch" placeholder="Lọc từ khóa..." style="min-height:36px; width:180px; font-size:13px;" oninput="renderLogs()" />
            <button type="button" id="btnPauseLogs" class="sec sm" onclick="togglePauseLogs()">Tạm dừng</button>
            <button type="button" class="sec sm" onclick="copyLogs()">Sao chép log</button>
          </div>
        </div>
        <div id="logContainer" class="logs">Đang tải nhật ký...</div>
      </div>
    </section>
  </div>
</div>

<script>
  let adminToken = localStorage.getItem('cr_admin_token') || '';
  let cachedLogs = [];
  let logsPaused = false;
  let formHydrated = false;
  let browserRfidCapture = false;
  let browserRfidBuf = '';
  let browserRfidLastTs = 0;
  let autoLiveTimer = null;

  function apiHeaders(extra) {
    const h = Object.assign({ 'Content-Type': 'application/json' }, extra || {});
    if (adminToken) h['X-Admin-Token'] = adminToken;
    return h;
  }

  function showToast(text, isErr) {
    const box = document.getElementById('globalToast');
    box.textContent = text;
    box.className = 'msg show ' + (isErr ? 'err' : 'ok');
    clearTimeout(window.__toastTimer);
    window.__toastTimer = setTimeout(() => { box.className = 'msg'; }, 4500);
  }

  function switchTab(tabId) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.toggle('active', b.dataset.tab === tabId));
    document.querySelectorAll('.tab-panel').forEach(p => p.classList.toggle('active', p.id === tabId));
    if (tabId === 'tabLive') {
      refreshScreenshot();
    }
  }

  async function submitPin() {
    const pin = document.getElementById('pinInput').value.trim();
    try {
      const res = await fetch('/api/auth', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pin })
      });
      const data = await res.json();
      if (res.ok && data.ok) {
        adminToken = data.token || '';
        localStorage.setItem('cr_admin_token', adminToken);
        document.getElementById('pinInput').value = '';
        formHydrated = false;
        showToast('Đã mở khóa bảng điều khiển', false);
        pollStatus();
      } else {
        showToast(data.message || 'Mã PIN không đúng', true);
      }
    } catch (e) {
      showToast('Không kết nối được tới máy chủ cổng 8888', true);
    }
  }

  async function logoutPin() {
    await fetch('/api/logout', { method: 'POST', headers: apiHeaders() }).catch(() => {});
    adminToken = '';
    localStorage.removeItem('cr_admin_token');
    formHydrated = false;
    pollStatus();
  }

  function updateDomainChips(url) {
    const u = (url || '').toLowerCase();
    document.getElementById('chipDiemDanh').classList.toggle('active', u.includes('diemdanhhs.com'));
    document.getElementById('chipDonTra').classList.toggle('active', u.includes('dontrahs.com'));
  }

  function selectPresetUrl(url) {
    document.getElementById('cfgTargetUrl').value = url;
    updateDomainChips(url);
  }

  async function sendRfidCard(explicitCard) {
    const input = document.getElementById('rfidInput');
    const cardId = (explicitCard || input.value || '').trim();
    if (!cardId) {
      showToast('Vui lòng nhập hoặc quẹt mã thẻ RFID', true);
      input.focus();
      return;
    }
    const mode = document.getElementById('rfidMode').value;
    const btn = document.getElementById('btnSendRfid');
    btn.disabled = true;
    try {
      const res = await fetch('/api/rfid/scan', {
        method: 'POST',
        headers: apiHeaders(),
        body: JSON.stringify({ cardId, mode, source: 'WEB_CONSOLE' })
      });
      const data = await res.json();
      if (data.ok) {
        showToast('Thẻ ' + data.cardId + ': ' + data.message, false);
        if (!explicitCard) input.value = '';
      } else {
        showToast('Thẻ ' + cardId + ' lỗi: ' + (data.message || 'Không hợp lệ'), true);
      }
      pollStatus();
    } catch (e) {
      showToast('Lỗi kết nối khi gửi thẻ RFID', true);
    } finally {
      btn.disabled = false;
    }
  }

  function toggleKeyboardRfidCapture() {
    browserRfidCapture = !browserRfidCapture;
    const btn = document.getElementById('btnListenerMode');
    btn.textContent = 'Bắt phím RFID trên trình duyệt: ' + (browserRfidCapture ? 'BẬT' : 'TẮT');
    btn.className = browserRfidCapture ? 'ok sm' : 'sec sm';
    showToast(
      browserRfidCapture
        ? 'Đã bật nhận đầu đọc RFID trực tiếp trên trình duyệt (quẹt thẻ bất kỳ lúc nào)'
        : 'Đã tắt bắt phím RFID tự động trên trình duyệt',
      false
    );
  }

  window.addEventListener('keydown', function(ev) {
    if (!browserRfidCapture) return;
    const tag = ev.target && ev.target.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
    if (ev.ctrlKey || ev.altKey || ev.metaKey) return;
    const now = Date.now();
    if (now - browserRfidLastTs > 300) browserRfidBuf = '';
    browserRfidLastTs = now;
    if (ev.key === 'Enter') {
      const code = browserRfidBuf.trim();
      browserRfidBuf = '';
      if (code.length >= 4) {
        ev.preventDefault();
        sendRfidCard(code);
      }
    } else if (ev.key && ev.key.length === 1 && /^[0-9A-Za-z_-]$/.test(ev.key)) {
      browserRfidBuf += ev.key;
    }
  });

  async function loadSchoolCards() {
    showToast('Đang tải danh sách mã thẻ từ máy chủ...', false);
    try {
      const res = await fetch('/api/rfid/cards', { headers: apiHeaders() });
      const data = await res.json();
      if (!data.ok) {
        showToast(data.message || 'Không tải được danh sách mã thẻ', true);
        return;
      }
      const box = document.getElementById('knownCardsBox');
      const list = document.getElementById('knownCardsList');
      const lbl = document.getElementById('knownCardsLabel');
      box.classList.remove('hidden');
      lbl.textContent = 'Tổng cộng ' + data.totalCards + ' mã thẻ (bấm vào thẻ bất kỳ để quẹt thử):';
      list.innerHTML = '';
      const cards = data.cards || [];
      if (!cards.length) {
        list.innerHTML = '<span style="color:var(--text-muted);font-size:12.5px;">Trường chưa khai báo mã thẻ nào.</span>';
      } else {
        cards.forEach(c => {
          const b = document.createElement('button');
          b.type = 'button';
          b.className = 'chip-btn';
          b.textContent = c;
          b.onclick = () => sendRfidCard(c);
          list.appendChild(b);
        });
      }
      showToast(data.message, false);
    } catch (e) {
      showToast('Lỗi khi tải danh sách mã thẻ', true);
    }
  }

  async function clearRfidHistory() {
    await fetch('/api/rfid/clear', { method: 'POST', headers: apiHeaders() }).catch(() => {});
    pollStatus();
  }

  async function navigateRoute(route) {
    try {
      const res = await fetch('/api/web/navigate', {
        method: 'POST',
        headers: apiHeaders(),
        body: JSON.stringify({ route })
      });
      const data = await res.json();
      showToast(data.message || ('Đang mở ' + route), !data.ok);
      setTimeout(pollStatus, 400);
    } catch (e) {
      showToast('Không gửi được lệnh chuyển trang', true);
    }
  }

  function openCustomRoute() {
    const val = document.getElementById('customRouteInput').value.trim();
    if (!val) return;
    navigateRoute(val);
  }

  async function sendCommand(action) {
    try {
      const res = await fetch('/api/command', {
        method: 'POST',
        headers: apiHeaders(),
        body: JSON.stringify({ action })
      });
      const data = await res.json();
      showToast(data.message || 'Đã gửi lệnh', !data.ok);
      setTimeout(pollStatus, 400);
    } catch (e) {
      showToast('Lỗi kết nối khi gửi lệnh', true);
    }
  }

  async function loginAttendanceNow() {
    const username = document.getElementById('cfgUsername').value.trim();
    const password = document.getElementById('cfgPassword').value.trim();
    const targetUrl = document.getElementById('cfgTargetUrl').value.trim();
    const autoEnterCheckInPage = document.getElementById('cfgAutoCheckIn').checked;
    try {
      const payload = {
        username,
        targetUrl,
        autoEnterCheckInPage,
        route: autoEnterCheckInPage ? '/check-in-out' : '/'
      };
      if (password) payload.password = password;
      const res = await fetch('/api/web/login', {
        method: 'POST',
        headers: apiHeaders(),
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      showToast(data.message || 'Đã gửi lệnh đăng nhập', !data.ok);
      if (data.ok) document.getElementById('cfgPassword').value = '';
      setTimeout(pollStatus, 600);
    } catch (e) {
      showToast('Lỗi khi thực hiện đăng nhập từ localhost', true);
    }
  }

  async function logoutAttendanceWeb() {
    try {
      const res = await fetch('/api/web/logout', { method: 'POST', headers: apiHeaders() });
      const data = await res.json();
      showToast(data.message || 'Đã đăng xuất', !data.ok);
      pollStatus();
    } catch (e) {
      showToast('Lỗi khi đăng xuất phiên điểm danh', true);
    }
  }

  async function saveFullConfig(applyAndConnect) {
    const payload = {
      username: document.getElementById('cfgUsername').value.trim(),
      targetUrl: document.getElementById('cfgTargetUrl').value.trim(),
      mdnsHostname: document.getElementById('cfgMdns').value.trim(),
      githubRepo: document.getElementById('cfgGithubRepo').value.trim(),
      autoEnterCheckInPage: document.getElementById('cfgAutoCheckIn').checked,
      hardwareRfidEnabled: document.getElementById('cfgHwRfid').checked,
      allowLocalhostBypass: document.getElementById('cfgLocalhostBypass').checked,
      applyAndConnect: !!applyAndConnect
    };
    const pass = document.getElementById('cfgPassword').value.trim();
    if (pass) payload.password = pass;
    const pin = document.getElementById('cfgAdminPin').value.trim();
    if (pin) payload.adminPin = pin;

    try {
      const res = await fetch('/api/config', {
        method: 'POST',
        headers: apiHeaders(),
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      showToast(data.message || 'Đã lưu cấu hình', !data.ok);
      document.getElementById('cfgPassword').value = '';
      document.getElementById('cfgAdminPin').value = '';
      pollStatus();
    } catch (e) {
      showToast('Không lưu được cấu hình', true);
    }
  }

  async function clearAdminPin() {
    const res = await fetch('/api/config', {
      method: 'POST',
      headers: apiHeaders(),
      body: JSON.stringify({ clearPin: true })
    });
    const data = await res.json();
    showToast(data.message || 'Đã xóa mã PIN LAN', !data.ok);
    pollStatus();
  }

  function refreshScreenshot() {
    const img = document.getElementById('liveScreenImg');
    const qToken = adminToken ? ('&token=' + encodeURIComponent(adminToken)) : '';
    img.src = '/api/web/screenshot?t=' + Date.now() + qToken;
  }

  function toggleAutoLive() {
    const btn = document.getElementById('btnAutoLive');
    if (autoLiveTimer) {
      clearInterval(autoLiveTimer);
      autoLiveTimer = null;
      btn.textContent = 'Tự làm mới 1.5s: TẮT';
      btn.className = 'sec sm';
    } else {
      refreshScreenshot();
      autoLiveTimer = setInterval(refreshScreenshot, 1500);
      btn.textContent = 'Tự làm mới 1.5s: BẬT';
      btn.className = 'ok sm';
    }
  }

  async function webAction(payload) {
    try {
      const res = await fetch('/api/web/action', {
        method: 'POST',
        headers: apiHeaders(),
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!data.ok) showToast(data.message || 'Không thực hiện được thao tác', true);
      setTimeout(refreshScreenshot, 380);
    } catch (e) {
      showToast('Lỗi gửi thao tác màn hình', true);
    }
  }

  function handleScreenClick(ev) {
    const img = ev.currentTarget;
    const rect = img.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;
    const xRatio = (ev.clientX - rect.left) / rect.width;
    const yRatio = (ev.clientY - rect.top) / rect.height;
    webAction({ action: 'tap', xRatio, yRatio });
  }

  function triggerClickByText() {
    const text = document.getElementById('clickTextInput').value.trim();
    if (!text) return;
    webAction({ action: 'click_text', text });
  }

  function triggerRemoteInput() {
    const value = document.getElementById('remoteInputVal').value;
    webAction({ action: 'input_text', value });
  }

  async function triggerOtaCheck() {
    const res = await fetch('/api/update/check', { method: 'POST', headers: apiHeaders() });
    const data = await res.json();
    showToast(data.message || 'Đang kiểm tra GitHub Releases...', !data.ok);
    setTimeout(pollStatus, 800);
  }

  async function triggerOtaInstall() {
    const res = await fetch('/api/update/install', { method: 'POST', headers: apiHeaders() });
    const data = await res.json();
    showToast(data.message || 'Đang tải gói APK cập nhật...', !data.ok);
    setTimeout(pollStatus, 800);
  }

  function renderRoutesCatalog(routes, activeRoute) {
    const wrap = document.getElementById('routesContainer');
    if (!Array.isArray(routes) || !routes.length) return;
    const groups = {};
    routes.forEach(r => {
      if (!groups[r.group]) groups[r.group] = [];
      groups[r.group].push(r);
    });
    let html = '';
    Object.keys(groups).forEach(gName => {
      html += '<div class="route-group-title">' + escapeHtml(gName) + '</div><div class="route-grid">';
      groups[gName].forEach(item => {
        const isAct = (activeRoute || '/') === item.route;
        html += '<button type="button" class="route-btn ' + (isAct ? 'active' : '') + '" onclick="navigateRoute(\'' + item.route + '\')">' +
                escapeHtml(item.label) + '</button>';
      });
      html += '</div>';
    });
    wrap.innerHTML = html;
  }

  function renderRfidHistory(scans) {
    const tbody = document.getElementById('rfidHistoryBody');
    if (!Array.isArray(scans) || !scans.length) {
      tbody.innerHTML = '<tr><td colspan="5" style="color: var(--text-muted); text-align: center;">Chưa có dữ liệu quẹt thẻ RFID trong phiên này.</td></tr>';
      return;
    }
    tbody.innerHTML = scans.map(s => {
      const cls = s.ok ? 'ok' : 'err';
      const stLabel = s.ok ? 'THÀNH CÔNG' : ('LỖI ' + (s.httpStatus || ''));
      return '<tr>' +
        '<td>' + escapeHtml(s.timestamp || '') + '</td>' +
        '<td><b>' + escapeHtml(s.cardId || '') + '</b></td>' +
        '<td>' + escapeHtml(s.source || '') + '</td>' +
        '<td><span class="badge ' + cls + '">' + escapeHtml(stLabel) + '</span></td>' +
        '<td>' + escapeHtml(s.message || '') + '</td>' +
      '</tr>';
    }).join('');
  }

  function togglePauseLogs() {
    logsPaused = !logsPaused;
    document.getElementById('btnPauseLogs').textContent = logsPaused ? 'Tiếp tục' : 'Tạm dừng';
    if (!logsPaused) renderLogs();
  }

  function renderLogs() {
    const q = (document.getElementById('logSearch').value || '').trim().toLowerCase();
    const box = document.getElementById('logContainer');
    const filtered = q ? cachedLogs.filter(l => l.toLowerCase().includes(q)) : cachedLogs;
    box.textContent = filtered.length ? filtered.slice().reverse().join('\n') : 'Không có nhật ký khớp bộ lọc.';
  }

  function copyText(txt) {
    navigator.clipboard.writeText(txt).then(() => showToast('Đã sao chép vào bộ nhớ tạm', false));
  }

  function copyLogs() {
    copyText(cachedLogs.join('\n'));
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c]));
  }

  async function pollStatus() {
    try {
      const res = await fetch('/api/status', { headers: apiHeaders() });
      const d = await res.json();

      const pill = document.getElementById('connPill');
      const connText = document.getElementById('connText');

      if (d.locked) {
        document.getElementById('authCard').classList.remove('hidden');
        document.getElementById('mainContent').classList.add('hidden');
        document.getElementById('btnLockSession').classList.add('hidden');
        pill.className = 'pill warn';
        connText.textContent = 'Đang khóa mã PIN';
        return;
      }

      document.getElementById('authCard').classList.add('hidden');
      document.getElementById('mainContent').classList.remove('hidden');
      document.getElementById('btnLockSession').classList.toggle('hidden', !d.hasAdminPin);

      pill.className = d.internetConnected ? 'pill ok' : 'pill warn';
      connText.textContent = d.internetConnected ? 'Trực tuyến 24/7' : 'Chỉ mạng nội bộ LAN';

      document.getElementById('subTitle').textContent =
        'Localhost: ' + (d.controlUrlLocalhost || 'http://127.0.0.1:8888') +
        ' • LAN: ' + (d.controlUrlLocal || '') +
        (d.lanIp ? (' (' + d.lanIp + ':' + d.port + ')') : '');

      document.getElementById('kpiHost').textContent = d.controlUrlLocalhost || 'http://127.0.0.1:8888';
      document.getElementById('kpiLanSub').textContent = d.controlUrlIp || d.controlUrlLocal || 'Chưa có IP LAN';

      const ws = d.webSession || {};
      document.getElementById('kpiSchool').textContent = ws.loggedIn
        ? (ws.schoolName || 'Đã đăng nhập hệ thống')
        : 'Chưa đăng nhập';
      document.getElementById('kpiAccount').textContent =
        (ws.accountName ? ('TK: ' + ws.accountName) : 'Chưa lưu TK') +
        (ws.accountRole ? (' • Quyền: ' + ws.accountRole) : '');

      const rf = d.rfid || {};
      document.getElementById('kpiRfidCount').textContent =
        (rf.totalScans || 0) + ' lượt (' + (rf.successCount || 0) + ' đạt / ' + (rf.errorCount || 0) + ' lỗi)';
      document.getElementById('kpiRfidLast').textContent = rf.lastScan
        ? ('Gần nhất: ' + rf.lastScan.cardId + ' lúc ' + rf.lastScan.timestamp)
        : 'Sẵn sàng nhận thẻ RFID';

      const screenText = d.currentScreen === 'WEBVIEW'
        ? ('WebView: ' + (d.activeRoute || '/'))
        : ('Cài đặt' + (d.countdownSeconds >= 0 ? (' (tự mở ' + d.countdownSeconds + 's)') : ''));
      document.getElementById('kpiRoute').textContent = screenText;
      document.getElementById('kpiVersion').textContent = 'Phiên bản v' + d.versionName + ' • ' + (d.targetUrl || '');
      document.getElementById('activeRouteBadge').textContent = 'Đang mở: ' + (d.activeRoute || '/');

      renderRoutesCatalog(d.routes || [], d.activeRoute || '/');
      renderRfidHistory(rf.recentScans || []);

      if (!formHydrated) {
        document.getElementById('cfgUsername').value = d.username || '';
        document.getElementById('cfgTargetUrl').value = d.targetUrl || '';
        document.getElementById('cfgMdns').value = d.mdnsHostname || '';
        document.getElementById('cfgGithubRepo').value = d.githubRepo || '';
        document.getElementById('cfgAutoCheckIn').checked = !!d.autoEnterCheckInPage;
        document.getElementById('cfgHwRfid').checked = d.hardwareRfidEnabled !== false;
        document.getElementById('cfgLocalhostBypass').checked = d.allowLocalhostBypass !== false;
        updateDomainChips(d.targetUrl || '');
        formHydrated = true;
      }

      document.getElementById('otaBadge').textContent = 'v' + d.versionName;
      document.getElementById('otaStatusText').textContent = d.updateMessage || 'Sẵn sàng kiểm tra';
      const track = document.getElementById('otaProgressTrack');
      const fill = document.getElementById('otaProgressFill');
      if (d.updateState === 'DOWNLOADING' && d.updateProgress >= 0) {
        track.style.display = 'block';
        fill.style.width = d.updateProgress + '%';
      } else {
        track.style.display = 'none';
      }
      const notesBox = document.getElementById('otaNotesBox');
      if (d.latestNotes) {
        notesBox.classList.remove('hidden');
        notesBox.textContent = d.latestNotes;
      } else {
        notesBox.classList.add('hidden');
      }

      if (!logsPaused && Array.isArray(d.logs)) {
        cachedLogs = d.logs;
        renderLogs();
      }
    } catch (e) {
      const pill = document.getElementById('connPill');
      pill.className = 'pill err';
      document.getElementById('connText').textContent = 'Mất kết nối cổng 8888';
    }
  }

  pollStatus();
  setInterval(pollStatus, 2000);
</script>
</body>
</html>
""".trimIndent()
}
