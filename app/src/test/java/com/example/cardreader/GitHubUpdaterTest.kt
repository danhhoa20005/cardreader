package com.example.cardreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdaterTest {

    @Test
    fun compareVersions_handlesPrefixesAndDifferentSegmentLengths() {
        assertTrue(GitHubUpdater.compareVersions("v1.0.1", "1.0.0") > 0)
        assertTrue(GitHubUpdater.compareVersions("v1.1", "1.0.9") > 0)
        assertEquals(0, GitHubUpdater.compareVersions("v1.2.0", "1.2"))
        assertTrue(GitHubUpdater.compareVersions("1.0.0", "1.0.1") < 0)
    }

    @Test
    fun parseLatestReleaseJson_detectsNewerApkRelease() {
        val json = """
            {
              "tag_name": "v1.2.0",
              "name": "Release 1.2.0",
              "draft": false,
              "body": "Ho tro local host va toi uu WebView",
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "browser_download_url": "https://example.com/checksums.txt"
                },
                {
                  "name": "CardReader-release.apk",
                  "size": 4194304,
                  "browser_download_url": "https://example.com/CardReader-release.apk"
                }
              ]
            }
        """.trimIndent()

        val result = GitHubUpdater.parseLatestReleaseJson(json, "1.0.0")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val release = (result as UpdateCheckResult.UpdateAvailable).release
        assertEquals("v1.2.0", release.tagName)
        assertEquals("1.2.0", release.versionName)
        assertEquals("CardReader-release.apk", release.assetName)
        assertEquals("https://example.com/CardReader-release.apk", release.apkUrl)
    }

    @Test
    fun parseLatestReleaseJson_returnsUpToDateWhenVersionNotNewer() {
        val json = """
            {
              "tag_name": "v1.0.0",
              "draft": false,
              "assets": []
            }
        """.trimIndent()

        val result = GitHubUpdater.parseLatestReleaseJson(json, "1.0.0")
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun parseLatestReleaseJson_returnsErrorWhenNewerReleaseHasNoApk() {
        val json = """
            {
              "tag_name": "v2.0.0",
              "draft": false,
              "assets": [
                {
                  "name": "source.zip",
                  "size": 1024,
                  "browser_download_url": "https://example.com/source.zip"
                }
              ]
            }
        """.trimIndent()

        val result = GitHubUpdater.parseLatestReleaseJson(json, "1.0.0")
        assertTrue(result is UpdateCheckResult.Error)
    }
}
