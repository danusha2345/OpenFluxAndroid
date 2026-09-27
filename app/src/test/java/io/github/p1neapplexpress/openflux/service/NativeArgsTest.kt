package io.github.p1neapplexpress.openflux.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeArgsTest {

    private val socks = "127.0.0.1:4000"

    @Test
    fun `old payload gets client role loopback socks5 and keeps the rest`() {
        val payload = listOf("--client", "--transport", "yandex", "--url", "https://d", "--debug")
        assertEquals(
            listOf(
                "--role", "client", "--inbound", "socks5", "--socks5", socks,
                "--traffic-stats", "1s",
                "--transport", "yandex", "--url", "https://d", "--debug",
            ),
            NativeArgs.build(payload, socks, peerKey = null),
        )
    }

    @Test
    fun `flags owned by the app are replaced`() {
        val payload = listOf(
            "--role=exit", "--socks5", ":1080", "--encryption-key-file=/sdcard/key",
            "-i", "tun", "--transport", "mailru", "--url", "a/b",
        )
        assertEquals(
            listOf(
                "--role", "client", "--inbound", "socks5", "--socks5", socks,
                "--traffic-stats", "1s",
                "--peer-key", "server-public-key", "--transport", "mailru", "--url", "a/b",
            ),
            NativeArgs.build(payload, socks, peerKey = "server-public-key"),
        )
    }

    @Test
    fun `url values containing equals signs survive`() {
        val payload = listOf("--transport", "cupsonline", "--url=https://x/?rooms=abc==")
        assertEquals(
            listOf(
                "--role", "client", "--inbound", "socks5", "--socks5", socks,
                "--traffic-stats", "1s",
                "--transport", "cupsonline", "--url=https://x/?rooms=abc==",
            ),
            NativeArgs.build(payload, socks, peerKey = null),
        )
    }

    @Test
    fun `redact hides the max token`() {
        assertEquals(
            listOf("--maxToken", "***", "--maxUid", "1"),
            NativeArgs.redact(listOf("--maxToken", "secret", "--maxUid", "1")),
        )
        assertEquals(listOf("--maxToken=***"), NativeArgs.redact(listOf("--maxToken=secret")))
        assertEquals(listOf("--url", "***"), NativeArgs.redact(listOf("--url", "https://disk.yandex.ru/i/secret")))
    }

    @Test
    fun `Yandex handoff paths are owned by the app`() {
        val payload = listOf("--transport", "yandex", "--url", "https://disk.yandex.ru/i/x",
            "--yandex-cookies-file", "/sdcard/untrusted", "--yandex-challenge-file=/sdcard/other")
        val args = NativeArgs.build(payload, socks, "public-key", "/private/challenge", "/private/cookies")
        assertEquals(1, args.count { it == "--yandex-challenge-file" })
        assertEquals(1, args.count { it == "--yandex-cookies-file" })
        assertEquals("/private/challenge", args[args.indexOf("--yandex-challenge-file") + 1])
        assertEquals("/private/cookies", args[args.indexOf("--yandex-cookies-file") + 1])
        assertEquals(false, args.any { it.contains("/sdcard") })
    }
}
