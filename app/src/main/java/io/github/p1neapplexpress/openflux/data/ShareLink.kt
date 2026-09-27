package io.github.p1neapplexpress.openflux.data

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** QR format emitted by this fork's `openflux --share` (Noise, not upstream AES). */
object ShareLink {
    const val PREFIX = "openflux://noise-v1/"
    private val json = Json { ignoreUnknownKeys = false }

    @Serializable
    private data class Payload(val transport: String, val url: String, val peerKey: String)

    fun toTunnel(raw: String, id: Long): Tunnel? = runCatching {
        if (!raw.startsWith(PREFIX) || raw.length > PREFIX.length + 4096) return null
        val decoded = Base64.getUrlDecoder().decode(raw.removePrefix(PREFIX))
        val payload = json.decodeFromString<Payload>(decoded.toString(Charsets.UTF_8))
        val transport = TransportType.entries.firstOrNull { it.cliName == payload.transport }
            ?: return null
        if (!transport.usesUrl || payload.url.isBlank() || payload.url != payload.url.trim() ||
            payload.url == "http://#" || payload.url.length > 2048 ||
            !EncryptionKey.isValid(payload.peerKey)) return null
        val args = TunnelPayload.build(TunnelPayload.Form(transport, url = payload.url))
            ?: return null
        Tunnel(id, "OpenFlux ${transport.cliName}", transport.name, args, payload.peerKey)
    }.getOrNull()
}
