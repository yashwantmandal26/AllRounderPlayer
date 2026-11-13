package com.example.ymediaplayer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun parseLatestReleaseJson_selectsReleaseApkAndSkipsDrafts() {
        val json = """
            {
              "tag_name": "v2.8.3",
              "name": "AllRounder v2.8.3",
              "body": "- Smoother visuals\n- Better recovery",
              "draft": false,
              "prerelease": false,
              "published_at": "2026-11-12T10:00:00Z",
              "assets": [
                {
                  "name": "app-debug.apk",
                  "size": 1000,
                  "browser_download_url": "https://github.com/yashwantmandal26/AllRounderPlayer/releases/download/v2.8.3/app-debug.apk"
                },
                {
                  "name": "app-release.apk",
                  "size": 45123456,
                  "browser_download_url": "https://github.com/yashwantmandal26/AllRounderPlayer/releases/download/v2.8.3/app-release.apk"
                }
              ]
            }
        """.trimIndent()

        val release = UpdateManager.parseLatestReleaseJson(json)
        assertNotNull(release)
        assertEquals("v2.8.3", release!!.tagName)
        assertEquals("2.8.3", release.versionName)
        assertEquals("AllRounder v2.8.3", release.title)
        assertEquals(45123456L, release.fileSize)
        assertTrue(release.apkDownloadUrl.endsWith("/app-release.apk"))

        val draftJson = json.replace("\"draft\": false", "\"draft\": true")
        assertNull(UpdateManager.parseLatestReleaseJson(draftJson))
    }

    @Test
    fun parseReleasesAtom_parsesFallbackFeedWithoutApiQuota() {
        val atomXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>tag:github.com,2008:Repository/12345/v2.9.0</id>
                <updated>2026-11-13T01:00:00Z</updated>
                <link rel="alternate" type="text/html" href="https://github.com/yashwantmandal26/AllRounderPlayer/releases/tag/v2.9.0"/>
                <title>AllRounder 2.9.0</title>
                <content type="html">&lt;ul&gt;&lt;li&gt;Auto-update fix&lt;/li&gt;&lt;li&gt;Corrupted media support&lt;/li&gt;&lt;/ul&gt;</content>
              </entry>
            </feed>
        """.trimIndent()

        val list = UpdateManager.parseReleasesAtom(atomXml)
        assertEquals(1, list.size)
        val item = list[0]
        assertEquals("v2.9.0", item.tagName)
        assertEquals("2.9.0", item.versionName)
        assertEquals("AllRounder 2.9.0", item.title)
        assertTrue(item.changelog.contains("Auto-update fix"))
        assertTrue(item.changelog.contains("Corrupted media support"))
        assertEquals(
            "https://github.com/yashwantmandal26/AllRounderPlayer/releases/download/v2.9.0/app-release.apk",
            item.apkDownloadUrl
        )
    }

    @Test
    fun parseTagFromReleaseUrl_extractsCleanTag() {
        assertEquals(
            "v2.8.2",
            UpdateManager.parseTagFromReleaseUrl("https://github.com/yashwantmandal26/AllRounderPlayer/releases/tag/v2.8.2?foo=1#bar")
        )
        assertNull(UpdateManager.parseTagFromReleaseUrl("https://github.com/yashwantmandal26/AllRounderPlayer/releases"))
    }

    @Test
    fun evaluateAutoCheck_retriesAfterFailureAndRespectsCooldowns() {
        // Fresh launch -> fetch now
        assertEquals(
            UpdateManager.AutoCheckDecision.FETCH_NOW,
            UpdateManager.evaluateAutoCheck(true, false, nowElapsedMs = 5_000L, lastSuccessMs = 0L, lastFailureMs = 0L)
        )
        // Transient offline failure 5s ago -> skip within 20s cooldown
        assertEquals(
            UpdateManager.AutoCheckDecision.SKIP_COOLDOWN,
            UpdateManager.evaluateAutoCheck(true, false, nowElapsedMs = 10_000L, lastSuccessMs = 0L, lastFailureMs = 5_000L)
        )
        // Failure 25s ago -> automatic retry on next resume
        assertEquals(
            UpdateManager.AutoCheckDecision.FETCH_NOW,
            UpdateManager.evaluateAutoCheck(true, false, nowElapsedMs = 30_000L, lastSuccessMs = 0L, lastFailureMs = 5_000L)
        )
        // Success 60s ago -> use cached result so popup still shows cleanly
        assertEquals(
            UpdateManager.AutoCheckDecision.USE_CACHED_RESULT,
            UpdateManager.evaluateAutoCheck(true, false, nowElapsedMs = 65_000L, lastSuccessMs = 5_000L, lastFailureMs = 0L)
        )
        // Manual check overrides everything
        assertEquals(
            UpdateManager.AutoCheckDecision.FETCH_NOW,
            UpdateManager.evaluateAutoCheck(false, true, nowElapsedMs = 10_000L, lastSuccessMs = 5_000L, lastFailureMs = 9_000L)
        )
    }

    @Test
    fun isNewer_handlesSemanticVersions() {
        assertTrue(UpdateManager.isNewer("2.8.2", "2.8.1"))
        assertTrue(UpdateManager.isNewer("2.10.0", "2.9.9"))
        assertTrue(UpdateManager.isNewer("v3.0", "2.8.2"))
        assertFalse(UpdateManager.isNewer("2.8.2", "2.8.2"))
        assertFalse(UpdateManager.isNewer("2.8.1", "2.8.2"))
    }
}
