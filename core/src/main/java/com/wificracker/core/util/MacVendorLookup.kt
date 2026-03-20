package com.wificracker.core.util

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacVendorLookup @Inject constructor(private val ouiMap: Map<String, String>) {
    fun resolve(macAddress: String): String { val prefix = macAddress.uppercase().replace("-", ":").take(8); return ouiMap[prefix] ?: "Unknown" }
}
