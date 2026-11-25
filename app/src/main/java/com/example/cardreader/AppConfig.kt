package com.example.cardreader

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val username: String,
    val password: String,
    val targetUrl: String,
    val mdnsHostname: String,
    val githubRepo: String,
    val adminPin: String
) {
    fun hasCredentials(): Boolean = username.isNotEmpty() && password.isNotEmpty()
    fun hasAdminPin(): Boolean = adminPin.isNotEmpty()
}

object AppConfig {
    private const val PREFS_NAME = "AppPrefs"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_TARGET_URL = "target_url"
    private const val KEY_MDNS_HOSTNAME = "mdns_hostname"
    private const val KEY_GITHUB_REPO = "github_repo"
    private const val KEY_ADMIN_PIN = "admin_pin"
    private const val KEY_OVERLAY_PROMPTED = "overlay_prompted"
    private const val KEY_LAST_WEB_USER = "last_web_user"

    private val HOSTNAME_REGEX =
        Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$")
    private val GITHUB_REPO_REGEX =
        Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): AppSettings {
        val p = prefs(context)
        val targetRaw = p.getString(KEY_TARGET_URL, null)
        val mdnsRaw = p.getString(KEY_MDNS_HOSTNAME, null)
        val repoRaw = p.getString(KEY_GITHUB_REPO, null)

        return AppSettings(
            username = p.getString(KEY_USERNAME, "") ?: "",
            password = p.getString(KEY_PASSWORD, "") ?: "",
            targetUrl = normalizeUrl(targetRaw ?: BuildConfig.DEFAULT_TARGET_URL)
                .ifEmpty { BuildConfig.DEFAULT_TARGET_URL },
            mdnsHostname = normalizeMdnsHostname(mdnsRaw ?: BuildConfig.DEFAULT_MDNS_HOSTNAME),
            githubRepo = normalizeGithubRepo(repoRaw ?: BuildConfig.DEFAULT_GITHUB_REPO)
                .ifEmpty { BuildConfig.DEFAULT_GITHUB_REPO },
            adminPin = (p.getString(KEY_ADMIN_PIN, "") ?: "").trim()
        )
    }

    fun saveCredentials(
        context: Context,
        username: String,
        password: String,
        targetUrl: String? = null
    ): AppSettings {
        val editor = prefs(context).edit()
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password.trim())
        if (!targetUrl.isNullOrBlank()) {
            val normalized = normalizeUrl(targetUrl)
            if (normalized.isNotEmpty()) {
                editor.putString(KEY_TARGET_URL, normalized)
            }
        }
        editor.apply()
        return load(context)
    }

    fun saveTargetUrl(context: Context, targetUrl: String): AppSettings {
        val finalTarget = normalizeUrl(targetUrl).ifEmpty { BuildConfig.DEFAULT_TARGET_URL }
        prefs(context).edit().putString(KEY_TARGET_URL, finalTarget).apply()
        return load(context)
    }

    fun getLastWebUser(context: Context): String =
        (prefs(context).getString(KEY_LAST_WEB_USER, "") ?: "").trim()

    fun setLastWebUser(context: Context, username: String) {
        prefs(context).edit().putString(KEY_LAST_WEB_USER, username.trim()).apply()
    }

    fun isOverlayPrompted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_PROMPTED, false)

    fun markOverlayPrompted(context: Context) {
        prefs(context).edit().putBoolean(KEY_OVERLAY_PROMPTED, true).apply()
    }

    fun saveSettings(
        context: Context,
        username: String,
        password: String?,
        targetUrl: String,
        mdnsHostname: String,
        githubRepo: String? = null,
        adminPin: String? = null,
        clearAdminPin: Boolean = false
    ): AppSettings {
        val current = load(context)
        val finalUser = username.trim()
        val finalPass = when {
            password == null -> current.password
            password.trim().isEmpty() -> current.password
            else -> password.trim()
        }
        val finalTarget = normalizeUrl(targetUrl).ifEmpty { BuildConfig.DEFAULT_TARGET_URL }
        val finalMdns = normalizeMdnsHostname(mdnsHostname)
        val finalRepo = if (githubRepo != null) {
            normalizeGithubRepo(githubRepo).ifEmpty { current.githubRepo }
        } else {
            current.githubRepo
        }
        val finalPin = when {
            clearAdminPin -> ""
            adminPin == null -> current.adminPin
            else -> adminPin.trim()
        }

        prefs(context).edit()
            .putString(KEY_USERNAME, finalUser)
            .putString(KEY_PASSWORD, finalPass)
            .putString(KEY_TARGET_URL, finalTarget)
            .putString(KEY_MDNS_HOSTNAME, finalMdns)
            .putString(KEY_GITHUB_REPO, finalRepo)
            .putString(KEY_ADMIN_PIN, finalPin)
            .apply()

        return load(context)
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return withScheme.trimEnd('/')
    }

    fun normalizeMdnsHostname(raw: String): String {
        var host = raw.trim().lowercase()
        host = host.removePrefix("http://").removePrefix("https://")
        host = host.substringBefore('/').substringBefore(':').trimEnd('.')
        if (host.isEmpty()) return BuildConfig.DEFAULT_MDNS_HOSTNAME
        if (!host.endsWith(".local")) {
            host = "$host.local"
        }
        return if (HOSTNAME_REGEX.matches(host)) host else BuildConfig.DEFAULT_MDNS_HOSTNAME
    }

    fun normalizeGithubRepo(raw: String): String {
        var s = raw.trim()
        s = s.removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = s.split('/').filter { it.isNotBlank() }
        if (parts.size >= 2) {
            val candidate = "${parts[0]}/${parts[1]}"
            if (GITHUB_REPO_REGEX.matches(candidate)) return candidate
        }
        return ""
    }

    fun isValidGithubRepo(repo: String): Boolean =
        GITHUB_REPO_REGEX.matches(repo.trim())
}
