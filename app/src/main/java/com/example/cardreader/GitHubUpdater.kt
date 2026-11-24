package com.example.cardreader

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val assetName: String,
    val fileSizeBytes: Long
)

sealed class UpdateCheckResult {
    data object NotConfigured : UpdateCheckResult()
    data class UpToDate(val currentVersion: String, val latestTag: String) : UpdateCheckResult()
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Checks GitHub Releases API for newer APK builds, downloads the APK into app-private storage,
 * and creates a FileProvider installation Intent without requiring root access.
 */
object GitHubUpdater {

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 12000

    fun checkLatestRelease(repo: String, currentVersion: String): UpdateCheckResult {
        val cleanRepo = AppConfig.normalizeGithubRepo(repo)
        if (!AppConfig.isValidGithubRepo(cleanRepo)) {
            return UpdateCheckResult.NotConfigured
        }

        val apiUrl = "https://api.github.com/repos/$cleanRepo/releases/latest"
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "CardReader-Android/$currentVersion")
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            when (code) {
                200 -> parseLatestReleaseJson(body, currentVersion)
                404 -> UpdateCheckResult.Error("Không tìm thấy repo hoặc chưa có Release công khai ($cleanRepo).")
                403 -> UpdateCheckResult.Error("GitHub API tạm giới hạn lượt truy cập. Thử lại sau.")
                else -> UpdateCheckResult.Error("Lỗi GitHub API (HTTP $code).")
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error("Không kiểm tra được cập nhật: ${e.message ?: "Lỗi mạng"}")
        } finally {
            conn?.disconnect()
        }
    }

    internal fun parseLatestReleaseJson(jsonBody: String, currentVersion: String): UpdateCheckResult {
        val json = JSONObject(jsonBody)
        if (json.optBoolean("draft", false)) {
            return UpdateCheckResult.UpToDate(currentVersion, currentVersion)
        }

        val tagName = json.optString("tag_name", "").trim()
        val releaseName = json.optString("name", tagName).trim()
        val versionCandidate = tagName.ifEmpty { releaseName }
        val normalizedRemote = normalizeVersion(versionCandidate)

        val assets = json.optJSONArray("assets")
        var selectedUrl = ""
        var selectedName = ""
        var selectedSize = 0L

        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    selectedUrl = url
                    selectedName = name
                    selectedSize = asset.optLong("size", 0L)
                    if (name.contains("release", ignoreCase = true) ||
                        name.contains("universal", ignoreCase = true)
                    ) {
                        break
                    }
                }
            }
        }

        if (compareVersions(normalizedRemote, currentVersion) <= 0) {
            return UpdateCheckResult.UpToDate(currentVersion, tagName.ifEmpty { currentVersion })
        }

        if (selectedUrl.isBlank()) {
            return UpdateCheckResult.Error("Phiên bản mới ($tagName) chưa đính kèm file .apk.")
        }

        val notes = json.optString("body", "").trim()
        return UpdateCheckResult.UpdateAvailable(
            ReleaseInfo(
                tagName = tagName,
                versionName = normalizedRemote,
                releaseNotes = notes,
                apkUrl = selectedUrl,
                assetName = selectedName,
                fileSizeBytes = selectedSize
            )
        )
    }

    fun downloadApk(
        context: Context,
        release: ReleaseInfo,
        onProgress: (percent: Int) -> Unit
    ): File {
        val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
        updatesDir.listFiles()?.forEach { runCatching { it.delete() } }

        val targetFile = File(updatesDir, "CardReader-${release.versionName}.apk")
        var conn: HttpURLConnection? = null
        try {
            conn = openWithRedirects(release.apkUrl)
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Tải APK thất bại (HTTP $code)")
            }

            val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: release.fileSizeBytes
            val buffer = ByteArray(16 * 1024)
            var downloaded = 0L
            var lastPercent = -1

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0L) {
                            val percent = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (downloaded <= 0L || (totalBytes > 0L && downloaded < totalBytes)) {
                runCatching { targetFile.delete() }
                throw IllegalStateException("Tệp APK tải về chưa hoàn chỉnh.")
            }

            onProgress(100)
            return targetFile
        } finally {
            conn?.disconnect()
        }
    }

    fun buildInstallIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun openWithRedirects(startUrl: String, maxRedirects: Int = 5): HttpURLConnection {
        var currentUrl = startUrl
        repeat(maxRedirects) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "CardReader-Android")
            }
            val code = conn.responseCode
            if (code in listOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308
                )
            ) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next.isNullOrBlank()) throw IllegalStateException("Redirect URL trống.")
                currentUrl = URL(URL(currentUrl), next).toString()
            } else {
                return conn
            }
        }
        throw IllegalStateException("Quá nhiều lần chuyển hướng khi tải APK.")
    }

    internal fun normalizeVersion(version: String): String {
        return version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()
    }

    internal fun compareVersions(v1: String, v2: String): Int {
        val parts1 = normalizeVersion(v1).split('.').map { it.toIntOrNull() ?: 0 }
        val parts2 = normalizeVersion(v2).split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}
