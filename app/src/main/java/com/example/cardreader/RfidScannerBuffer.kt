package com.example.cardreader

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Bản ghi lịch sử mỗi lượt quẹt thẻ RFID (từ đầu đọc USB/HID phần cứng, API localhost hoặc Web Console).
 */
data class RfidScanRecord(
    val id: Long,
    val timestamp: String,
    val cardId: String,
    val source: String,
    val httpStatus: Int,
    val ok: Boolean,
    val message: String,
    val route: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("cardId", cardId)
        put("source", source)
        put("httpStatus", httpStatus)
        put("ok", ok)
        put("message", message)
        put("route", route)
    }
}

/**
 * Bộ đệm giải mã luồng phím từ máy đọc thẻ RFID USB/OTG/Bluetooth HID (chế độ giả lập bàn phím)
 * và quản lý lịch sử quẹt thẻ an toàn đa luồng.
 *
 * Đầu đọc RFID thông dụng gửi chuỗi ký tự `[0-9A-Za-z]` liên tiếp với khoảng cách < 120ms giữa các phím
 * và kết thúc bằng phím `Enter`.
 */
class RfidScannerBuffer(
    private val maxGapMs: Long = 300L,
    private val minLength: Int = 4,
    private val maxHistory: Int = 100
) {
    private val seq = AtomicLong(0L)
    private val buffer = StringBuilder()
    private var lastKeyTimeMs: Long = -1L

    private val history = ArrayDeque<RfidScanRecord>(maxHistory)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Volatile
    var totalScans: Int = 0
        private set

    @Volatile
    var successCount: Int = 0
        private set

    @Volatile
    var errorCount: Int = 0
        private set

    /**
     * Nhận từng phím từ sự kiện phần cứng. Trả về mã thẻ hoàn chỉnh khi gặp `Enter`,
     * hoặc `null` nếu đang tích lũy ký tự.
     */
    @Synchronized
    fun onKeyChar(keyChar: Char?, isEnter: Boolean, isBackspace: Boolean = false, eventTimeMs: Long): String? {
        if (lastKeyTimeMs > 0L && (eventTimeMs - lastKeyTimeMs) > maxGapMs) {
            buffer.setLength(0)
        }
        lastKeyTimeMs = eventTimeMs

        if (isEnter) {
            val candidate = normalizeCardId(buffer.toString())
            buffer.setLength(0)
            return if (candidate.length >= minLength) candidate else null
        }

        if (isBackspace) {
            if (buffer.isNotEmpty()) {
                buffer.deleteCharAt(buffer.length - 1)
            }
            return null
        }

        if (keyChar != null && isAllowedCardChar(keyChar) && buffer.length < MAX_CARD_LENGTH) {
            buffer.append(keyChar)
        }
        return null
    }

    @Synchronized
    fun resetBuffer() {
        buffer.setLength(0)
        lastKeyTimeMs = -1L
    }

    fun recordScan(
        cardId: String,
        source: String,
        httpStatus: Int,
        ok: Boolean,
        message: String,
        route: String = ""
    ): RfidScanRecord {
        val stamp = synchronized(timeFormat) { timeFormat.format(Date()) }
        val record = RfidScanRecord(
            id = seq.incrementAndGet(),
            timestamp = stamp,
            cardId = normalizeCardId(cardId),
            source = source,
            httpStatus = httpStatus,
            ok = ok,
            message = message,
            route = route
        )
        synchronized(history) {
            totalScans += 1
            if (ok) successCount += 1 else errorCount += 1
            if (history.size >= maxHistory) {
                history.removeLast()
            }
            history.addFirst(record)
        }
        return record
    }

    fun getHistory(limit: Int = maxHistory): List<RfidScanRecord> {
        synchronized(history) {
            return history.take(limit.coerceIn(1, maxHistory))
        }
    }

    fun getLastScan(): RfidScanRecord? {
        synchronized(history) {
            return history.firstOrNull()
        }
    }

    fun historyJsonArray(limit: Int = 30): JSONArray {
        val arr = JSONArray()
        for (item in getHistory(limit)) {
            arr.put(item.toJson())
        }
        return arr
    }

    fun clearHistory() {
        synchronized(history) {
            history.clear()
            totalScans = 0
            successCount = 0
            errorCount = 0
        }
    }

    companion object {
        const val MAX_CARD_LENGTH = 64
        private val CARD_ID_REGEX = Regex("^[0-9A-Za-z_-]{1,64}$")

        fun isAllowedCardChar(ch: Char): Boolean =
            (ch in '0'..'9') || (ch in 'A'..'Z') || (ch in 'a'..'z') || ch == '_' || ch == '-'

        fun normalizeCardId(raw: String): String {
            val cleaned = raw.trim().replace(Regex("[\\r\\n\\t ]+"), "")
            return if (CARD_ID_REGEX.matches(cleaned)) cleaned else ""
        }

        fun resolveApiBaseUrl(targetOrActiveUrl: String): String {
            return if (targetOrActiveUrl.contains("dontrahs.com", ignoreCase = true)) {
                "https://api.dontrahs.com/api"
            } else {
                "https://api.diemdanhhs.com/api"
            }
        }
    }
}
