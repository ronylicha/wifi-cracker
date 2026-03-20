package com.wificracker.core.wifi

data class WifiInterface(val name: String, val macAddress: String, val chipset: String, val driver: String, val supportsMonitor: Boolean, val isMonitorMode: Boolean = false)
data class ChipsetInfo(val chipset: String, val driver: String, val supportsMonitor: Boolean)
