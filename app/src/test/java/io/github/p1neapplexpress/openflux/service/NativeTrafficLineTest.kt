package io.github.p1neapplexpress.openflux.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeTrafficLineTest {
    @Test
    fun `parses traffic totals behind Go log prefix`() {
        assertEquals(
            NativeTrafficTotals(true, 1234, 5678),
            NativeTrafficLine.parse(
                "2026/09/19 12:00:00 main.go:1: [TRAFFIC] connected=true tx=1234 rx=5678"
            ),
        )
    }

    @Test
    fun `ignores ordinary log lines and malformed totals`() {
        assertNull(NativeTrafficLine.parse("[M-DOCS] connected"))
        assertNull(NativeTrafficLine.parse("[TRAFFIC] tx=x rx=2"))
    }
}
