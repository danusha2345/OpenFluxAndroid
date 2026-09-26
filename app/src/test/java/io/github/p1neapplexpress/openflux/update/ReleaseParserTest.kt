package io.github.p1neapplexpress.openflux.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseParserTest {
    private val digest = "a".repeat(64)

    private fun release(tag: String, url: String, hash: String = "sha256:$digest") = """
        {"tag_name":"$tag","assets":[{"name":"OpenFluxAndroid.apk",
        "browser_download_url":"$url","digest":"$hash","size":1234}]}
    """.trimIndent()

    private val allowedURL =
        "https://github.com/danusha2345/OpenFluxAndroid/releases/download/v1.1.12/OpenFluxAndroid.apk"

    @Test fun newerVerifiedRelease() {
        val update = ReleaseParser.findUpdate(release("v1.1.12", allowedURL), 10111)
        assertEquals(10112L, update?.versionCode)
        assertEquals("v1.1.12", update?.version)
    }

    @Test fun olderAndUntrustedReleasesAreIgnored() {
        assertNull(ReleaseParser.findUpdate(release("v1.1.11", allowedURL), 10111))
        assertNull(ReleaseParser.findUpdate(release("v1.1.12", "https://example.com/app.apk"), 10111))
        assertNull(ReleaseParser.findUpdate(release("v1.1.12", allowedURL, ""), 10111))
        assertNull(ReleaseParser.findUpdate(release("nightly", allowedURL), 10111))
    }
}
