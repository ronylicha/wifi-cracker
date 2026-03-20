package com.wificracker.core.wifi

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.root.ShellResult
import javax.inject.Inject
import javax.inject.Singleton

enum class WifiChipVendor(val label: String) {
    QUALCOMM("Qualcomm (Snapdragon)"),
    BROADCOM("Broadcom (Exynos/Pixel)"),
    MEDIATEK("MediaTek"),
    UNKNOWN("Unknown"),
}

data class ChipsetMonitorCapability(
    val vendor: WifiChipVendor,
    val chipName: String,
    val supportsInternalMonitor: Boolean,
    val monitorMethod: String,
)

@Singleton
class ChipsetMonitorHelper @Inject constructor(
    private val shellExecutor: ShellExecutor,
) {

    fun detectChipVendor(): ChipsetMonitorCapability {
        // Check Qualcomm QCACLD
        val qcacld = shellExecutor.executeAsRoot("ls /sys/module/wlan/parameters/con_mode 2>/dev/null")
        if (qcacld.isSuccess) {
            val chipName = shellExecutor.executeAsRoot("getprop ro.board.platform").stdout.trim()
            return ChipsetMonitorCapability(
                vendor = WifiChipVendor.QUALCOMM,
                chipName = "Qualcomm $chipName",
                supportsInternalMonitor = true,
                monitorMethod = "QCACLD con_mode",
            )
        }

        // Check Broadcom (Nexmon compatible)
        val bcm = shellExecutor.executeAsRoot("ls /sys/module/bcmdhd*/parameters/ 2>/dev/null || ls /sys/module/dhd/parameters/ 2>/dev/null")
        if (bcm.isSuccess) {
            val chipId = shellExecutor.executeAsRoot("cat /sys/module/bcmdhd*/parameters/info_string 2>/dev/null || cat /sys/module/dhd/parameters/info_string 2>/dev/null").stdout.trim()
            return ChipsetMonitorCapability(
                vendor = WifiChipVendor.BROADCOM,
                chipName = if (chipId.isNotBlank()) "Broadcom $chipId" else "Broadcom BCM",
                supportsInternalMonitor = true,
                monitorMethod = "Nexmon firmware patch",
            )
        }

        // Check MediaTek
        val mtk = shellExecutor.executeAsRoot("getprop ro.vendor.wlan.gen").stdout.trim()
        if (mtk.isNotBlank()) {
            return ChipsetMonitorCapability(
                vendor = WifiChipVendor.MEDIATEK,
                chipName = "MediaTek $mtk",
                supportsInternalMonitor = false,
                monitorMethod = "Not supported (firmware locked)",
            )
        }

        return ChipsetMonitorCapability(WifiChipVendor.UNKNOWN, "Unknown", false, "Unknown")
    }

    fun enableMonitorMode(interfaceName: String): ShellResult {
        val capability = detectChipVendor()

        return when (capability.vendor) {
            WifiChipVendor.QUALCOMM -> enableQualcommMonitor(interfaceName)
            WifiChipVendor.BROADCOM -> enableBroadcomMonitor(interfaceName)
            WifiChipVendor.MEDIATEK -> ShellResult(-1, "", "MediaTek internal WiFi does not support monitor mode. Use an external USB WiFi adapter.")
            WifiChipVendor.UNKNOWN -> enableGenericMonitor(interfaceName)
        }
    }

    fun disableMonitorMode(interfaceName: String): ShellResult {
        val capability = detectChipVendor()

        return when (capability.vendor) {
            WifiChipVendor.QUALCOMM -> disableQualcommMonitor(interfaceName)
            WifiChipVendor.BROADCOM -> disableBroadcomMonitor(interfaceName)
            else -> disableGenericMonitor(interfaceName)
        }
    }

    private fun enableQualcommMonitor(interfaceName: String): ShellResult {
        // Qualcomm QCACLD method: con_mode=4 enables monitor
        val steps = listOf(
            "ip link set $interfaceName down",
            "echo 4 > /sys/module/wlan/parameters/con_mode",
            "ip link set $interfaceName up",
        )
        return executeSteps(steps)
    }

    private fun disableQualcommMonitor(interfaceName: String): ShellResult {
        val steps = listOf(
            "ip link set $interfaceName down",
            "echo 0 > /sys/module/wlan/parameters/con_mode",
            "ip link set $interfaceName up",
        )
        return executeSteps(steps)
    }

    private fun enableBroadcomMonitor(interfaceName: String): ShellResult {
        // Broadcom/Nexmon method: use nexutil or ip link
        val nexutil = shellExecutor.executeAsRoot("which nexutil 2>/dev/null")
        if (nexutil.isSuccess) {
            // Nexmon is installed - use nexutil
            val steps = listOf(
                "ip link set $interfaceName down",
                "nexutil -m2",
                "ip link set $interfaceName up",
            )
            return executeSteps(steps)
        }
        // Fallback to ip link method
        return enableGenericMonitor(interfaceName)
    }

    private fun disableBroadcomMonitor(interfaceName: String): ShellResult {
        val nexutil = shellExecutor.executeAsRoot("which nexutil 2>/dev/null")
        if (nexutil.isSuccess) {
            val steps = listOf(
                "ip link set $interfaceName down",
                "nexutil -m0",
                "ip link set $interfaceName up",
            )
            return executeSteps(steps)
        }
        return disableGenericMonitor(interfaceName)
    }

    private fun enableGenericMonitor(interfaceName: String): ShellResult {
        val steps = listOf(
            "ip link set $interfaceName down",
            "iw dev $interfaceName set type monitor",
            "ip link set $interfaceName up",
        )
        return executeSteps(steps)
    }

    private fun disableGenericMonitor(interfaceName: String): ShellResult {
        val steps = listOf(
            "ip link set $interfaceName down",
            "iw dev $interfaceName set type managed",
            "ip link set $interfaceName up",
        )
        return executeSteps(steps)
    }

    private fun executeSteps(steps: List<String>): ShellResult {
        for (step in steps) {
            val result = shellExecutor.executeAsRoot(step)
            if (!result.isSuccess) return result
        }
        return ShellResult(0, "OK", "")
    }
}
