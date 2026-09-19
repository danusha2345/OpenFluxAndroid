package io.github.p1neapplexpress.openflux.data

import java.util.Base64

/** Exit node X25519 public key passed to OpenFlux via `--peer-key`. */
object EncryptionKey {

    private const val PUBLIC_KEY_BYTES = 32

    fun normalize(raw: String): String = raw.trim()

    fun isValid(raw: String): Boolean = runCatching {
        Base64.getDecoder().decode(normalize(raw)).size == PUBLIC_KEY_BYTES
    }.getOrDefault(false)
}
