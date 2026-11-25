package com.example.cardreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdaterTest {

    @Test
    fun compareVersions_handlesPrefixesAndDifferentSegmentLengths() {
        assertTrue(GitHubUpdater.compareVersions("v1.0.6", "1.0.5") > 0)
        assertTrue(GitHubUpdater.compareVersions("v1.1", "1.0.9") > 0)
        assertEquals(0, GitHubUpdater.compareVersions("v1.2.0", "1.2"))
        assertTrue(GitHubUpdater.compareVersions("1.0.4", "1.0.5") < 0)
    }

    @Test
    fun parseLatestReleaseJson_detectsNewerApkRelease() {
        val json = """
            {
              "tag_name": "v1.2.0",
              "name": "Release 1.2.0",
              "draft": false,
              "body": "Ho tro cap nhat OTA qua GitHub va Web LAN cong 8888",
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "browser_download_url": "https://example.com/checksums.txt"
                },
                {
                  "name": "CardReader_v1.2.0_LAN_8888.apk",
                  "size": 4194304,
                  "browser_download_url": "https://example.com/CardReader_v1.2.0_LAN_8888.apk"
                }
              ]
            }
        """.trimIndent()

        val result = GitHubUpdater.parseLatestReleaseJson(json, "1.0.5")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val release = (result as UpdateCheckResult.UpdateAvailable).release
        assertEquals("v1.2.0", release.tagName)
        assertEquals("1.2.0", release.versionName)
        assertEquals("CardReader_v1.2.0_LAN_8888.apk", release.assetName)
    }

    @Test
    fun parseLatestReleaseJson_returnsUpToDateWhenVersionNotNewer() {
        val json = """
            {
              "tag_name": "v1.0.6",
              "draft": false,
              "assets": []
            }
        """.trimIndent()

        val result = GitHubUpdater.parseLatestReleaseJson(json, "1.0.6")
        assertTrue(result is UpdateCheckResult.UpToDate)
    }
}
