package io.github.p1neapplexpress.openflux.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YandexCaptchaFilesTest {
    @Test fun acceptsOnlyYandexHTTPSChallenge() {
        assertTrue(YandexCaptchaFiles.isAllowedChallenge("https://yandex.ru/showcaptchafast?x=1"))
        assertFalse(YandexCaptchaFiles.isAllowedChallenge("http://yandex.ru/showcaptcha"))
        assertFalse(YandexCaptchaFiles.isAllowedChallenge("https://yandex.ru.evil.test/showcaptcha"))
        assertFalse(YandexCaptchaFiles.isAllowedChallenge("https://example.com/showcaptcha"))
    }

    @Test fun exportsOnlyScopedCookieRows() {
        val text = YandexCaptchaFiles.formatCookies(mapOf(
            "https://docs.yandex.ru/edit/d/abc" to "Session_id=abc=123; yandexuid=42",
            "https://evil.test/" to "secret=leak",
        ))
        assertNotNull(text)
        assertTrue(text!!.contains("docs.yandex.ru\tFALSE\t/\tTRUE\t0\tSession_id\tabc=123"))
        assertFalse(text.contains("evil.test"))
        assertNull(YandexCaptchaFiles.formatCookies(mapOf("https://evil.test/" to "a=b")))
    }
}
