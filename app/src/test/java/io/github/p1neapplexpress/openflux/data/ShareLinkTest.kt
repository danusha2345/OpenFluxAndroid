package io.github.p1neapplexpress.openflux.data

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkTest {
    private val key = Base64.getEncoder().encodeToString(ByteArray(32))

    private fun link(transport: String = "mailru", url: String = "https://cloud.mail.ru/public/a/b",
                     peerKey: String = key): String {
        val json = """{"transport":"$transport","url":"$url","peerKey":"$peerKey"}"""
        return ShareLink.PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray())
    }

    @Test fun importsNoiseLink() {
        val tunnel = ShareLink.toTunnel(link(), 42)!!
        assertEquals(42, tunnel.id)
        assertEquals("mailru", tunnel.transportType)
        assertEquals(key, tunnel.encryptionKey)
        assertEquals("https://cloud.mail.ru/public/a/b",
            TunnelPayload.value(tunnel.transportConnPayload, "url"))
    }

    @Test fun rejectsIncompatibleOrIncompleteLink() {
        assertNull(ShareLink.toTunnel("openflux://v1/abc", 1))
        assertNull(ShareLink.toTunnel(link(transport = "oneme"), 1))
        assertNull(ShareLink.toTunnel(link(url = "http://#"), 1))
        assertNull(ShareLink.toTunnel(link(peerKey = "bad"), 1))
        assertNull(ShareLink.toTunnel(ShareLink.PREFIX + "A".repeat(4097), 1))
    }
}
