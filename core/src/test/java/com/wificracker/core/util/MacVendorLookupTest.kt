package com.wificracker.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MacVendorLookupTest {
    private val lookup = MacVendorLookup(mapOf("00:14:22" to "Dell Inc.", "00:50:56" to "VMware, Inc.", "DC:A6:32" to "Raspberry Pi Trading Ltd"))
    @Test fun `resolves known MAC`() { assertEquals("Dell Inc.", lookup.resolve("00:14:22:AB:CD:EF")) }
    @Test fun `returns Unknown for unrecognized`() { assertEquals("Unknown", lookup.resolve("FF:FF:FF:00:00:00")) }
    @Test fun `handles lowercase`() { assertEquals("VMware, Inc.", lookup.resolve("00:50:56:aa:bb:cc")) }
    @Test fun `handles dash separator`() { assertEquals("Raspberry Pi Trading Ltd", lookup.resolve("DC-A6-32-11-22-33")) }
}
