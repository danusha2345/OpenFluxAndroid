package io.github.p1neapplexpress.openflux.data

import kotlinx.serialization.Serializable

@Serializable
data class Tunnel(
    val id: Long,
    val name: String,
    val transportType: String,
    val transportConnPayload: List<String>,
    /** Exit node X25519 public key for Noise encryption; null means invalid for current builds. */
    val encryptionKey: String? = null,
)
