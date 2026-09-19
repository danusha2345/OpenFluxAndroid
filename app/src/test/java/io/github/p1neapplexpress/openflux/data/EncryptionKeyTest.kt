package io.github.p1neapplexpress.openflux.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptionKeyTest {

    @Test
    fun `accepts exactly one base64 encoded 32 byte public key`() {
        val key = "mEy4hq08BFW0mpjlznoF+kpkE+MgHoIg+GCYjYkPOSQ="
        assertTrue(EncryptionKey.isValid(key))
        assertTrue(EncryptionKey.isValid(" $key\n"))
        assertFalse(EncryptionKey.isValid("MTIzNDU2Nzg5MDEyMzQ1Ng=="))
        assertFalse(EncryptionKey.isValid("not-base64"))
        assertFalse(EncryptionKey.isValid(""))
    }

    @Test
    fun `normalize trims surrounding whitespace only`() {
        assertEquals("a b c", EncryptionKey.normalize("\t a b c \r\n"))
    }
}
