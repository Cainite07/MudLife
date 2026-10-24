package cn.mudlife.app.utils

import org.junit.Assert.*
import org.junit.Test

class UpdateManagerTest {
    @Test fun manifestAllowsMissingOptionalFieldsWithoutNullCrashes() {
        val info = UpdateManager.parseManifest("""{"versionCode":34,"versionName":"1.4.5","downloadUrl":"https://example.com/app.apk","mirrors":null,"title":null,"changelog":null}""")!!
        assertEquals(emptyList<String>(), info.mirrors)
        assertEquals("", info.title)
        assertEquals(listOf("https://example.com/app.apk"), UpdateManager.downloadUrls(info))
    }

    @Test fun invalidOrInsecureFeedsAreRejected() {
        assertNull(UpdateManager.parseManifest("<html>failure</html>"))
        assertNull(UpdateManager.parseManifest("{}"))
        assertNull(UpdateManager.parseManifest("""{"versionCode":34,"versionName":"1.4.5","downloadUrl":"http://example.com/app.apk"}"""))
    }

    @Test fun githubDownloadsHaveDirectFallbacksEvenWithoutManifestMirrors() {
        val url = "https://github.com/Cainite07/MudLife/releases/download/v1.4.5/MudLife-1.4.5.apk"
        val urls = UpdateManager.downloadUrls(UpdateInfo(versionCode = 34, downloadUrl = url, mirrors = listOf("https://ghproxy.net/$url")))
        assertEquals("https://gitee.com/cainite/MudLife/releases/download/v1.4.5/MudLife-1.4.5.apk", urls.first())
        assertEquals(url, urls[1])
        assertEquals(4, urls.size)
        assertEquals("https://ghproxy.net/$url", urls[2])
    }

    @Test fun mirrorOnlyManifestAndDirectHostsRemainUsable() {
        val info = UpdateManager.parseManifest("""{"versionCode":34,"versionName":"1.4.5","mirrors":[null,"http://bad.example/app.apk","https://download.example/app.apk"]}""")!!
        assertEquals(listOf("https://download.example/app.apk"), UpdateManager.downloadUrls(info))
        assertTrue(UpdateManager.CHECK_URLS.none { it.contains("?") })
    }

    @Test fun giteeDownloadsPrecedeThirdPartyGithubProxies() {
        val direct = "https://gitee.com/cainite/MudLife/releases/download/v1.4.5/MudLife-1.4.5.apk"
        val github = "https://github.com/Cainite07/MudLife/releases/download/v1.4.5/MudLife-1.4.5.apk"
        val urls = UpdateManager.downloadUrls(UpdateInfo(versionCode = 34, downloadUrl = github, mirrors = listOf(direct)))
        assertEquals(direct, urls.first())
        assertEquals(github, urls[1])
        assertEquals("https://gitee.com/cainite/MudLife/raw/main/version.json", UpdateManager.CHECK_URLS.first())
    }
}
