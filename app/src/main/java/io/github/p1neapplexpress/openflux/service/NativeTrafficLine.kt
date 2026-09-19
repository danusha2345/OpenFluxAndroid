package io.github.p1neapplexpress.openflux.service

data class NativeTrafficTotals(val connected: Boolean, val txBytes: Long, val rxBytes: Long)

/** Parser for the machine-readable traffic line emitted by the Go core. */
object NativeTrafficLine {
    private val pattern = Regex("""\[TRAFFIC] connected=(true|false) tx=(\d+) rx=(\d+)""")

    fun parse(line: String): NativeTrafficTotals? {
        val match = pattern.find(line) ?: return null
        return NativeTrafficTotals(
            connected = match.groupValues[1].toBooleanStrictOrNull() ?: return null,
            txBytes = match.groupValues[2].toLongOrNull() ?: return null,
            rxBytes = match.groupValues[3].toLongOrNull() ?: return null,
        )
    }
}
