package io.github.p1neapplexpress.openflux.service

import android.content.Context
import android.system.Os
import android.webkit.CookieManager
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Private handoff between the native Yandex transport and the in-app browser. */
object YandexCaptchaFiles {
    private const val CHALLENGE = "yandex-challenge.url"
    private const val COOKIES = "yandex-cookies.txt"
    private val cookieName = Regex("^[!#\$%&'*+.^_`|~0-9A-Za-z-]+$")

    fun challengeFile(context: Context) = File(context.filesDir, CHALLENGE)
    fun cookiesFile(context: Context) = File(context.filesDir, COOKIES)

    fun pendingURL(context: Context): String? {
        val challenge = challengeFile(context)
        if (!challenge.isFile || challenge.length() > 4096) return null
        val cookies = cookiesFile(context)
        if (cookies.isFile && cookies.lastModified() >= challenge.lastModified()) return null
        val url = runCatching { challenge.readText().trim() }.getOrNull() ?: return null
        return url.takeIf(::isAllowedChallenge)
    }

    fun isAllowedChallenge(raw: String): Boolean {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443) && allowedHost(uri.host) &&
            uri.path.orEmpty().lowercase().contains("showcaptcha")
    }

    private fun allowedHost(raw: String?): Boolean {
        val host = raw?.lowercase() ?: return false
        return listOf("yandex.ru", "yandex.kz", "yandex.net", "yandex.com")
            .any { host == it || host.endsWith(".$it") }
    }

    /** Build host-only session cookies in the format accepted by the Go core. */
    fun formatCookies(headersByURL: Map<String, String>): String? {
        val cookies = linkedMapOf<Pair<String, String>, String>()
        for ((url, header) in headersByURL) {
            val host = runCatching { URI(url).host?.lowercase() }.getOrNull()
            if (!allowedHost(host) || header.length > 64 * 1024) continue
            for (part in header.split(';')) {
                val pair = part.trim().split('=', limit = 2)
                if (pair.size != 2 || !cookieName.matches(pair[0]) ||
                    pair[1].any { it == '\t' || it == '\r' || it == '\n' }) continue
                cookies[host!! to pair[0]] = pair[1]
                if (cookies.size > 256) return null
            }
        }
        if (cookies.isEmpty()) return null
        return buildString {
            append("# Netscape HTTP Cookie File\n")
            for ((key, value) in cookies) {
                append(key.first).append("\tFALSE\t/\tTRUE\t0\t")
                    .append(key.second).append('\t').append(value).append('\n')
            }
        }
    }

    /** Called only after the person finishes the check in the app's WebView. */
    fun saveFromWebView(context: Context, manager: CookieManager, visitedURLs: Set<String>): Boolean {
        manager.flush()
        val urls = linkedSetOf(
            "https://yandex.ru/", "https://docs.yandex.ru/", "https://disk.yandex.ru/",
            "https://passport.yandex.ru/", "https://login.yandex.ru/",
        )
        urls.addAll(visitedURLs.filter(::isYandexURL))
        val headers = urls.associateWith { manager.getCookie(it).orEmpty() }
        val text = formatCookies(headers) ?: return false
        val target = cookiesFile(context)
        val temp = File.createTempFile(".yandex-cookies-", ".tmp", context.filesDir)
        try {
            Os.chmod(temp.absolutePath, 0x180) // 0600
            FileOutputStream(temp).use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
            return true
        } finally {
            temp.delete()
        }
    }

    fun isYandexURL(raw: String): Boolean {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        return uri.scheme == "https" && allowedHost(uri.host)
    }
}
