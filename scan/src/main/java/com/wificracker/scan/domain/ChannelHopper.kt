package com.wificracker.scan.domain

import com.wificracker.core.root.ShellExecutor
import com.wificracker.core.wifi.ChipsetMonitorHelper
import com.wificracker.core.wifi.WifiChipVendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelHopper @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val chipsetMonitorHelper: ChipsetMonitorHelper,
) {

    companion object {
        val CHANNELS_2_4GHZ = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
        val CHANNELS_5GHZ = listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
        val ALL_CHANNELS = CHANNELS_2_4GHZ + CHANNELS_5GHZ

        // Channel to frequency mapping
        private fun channelToFreq(ch: Int): Int = when {
            ch in 1..13 -> 2407 + ch * 5
            ch in 36..165 -> 5000 + ch * 5
            else -> 0
        }
    }

    fun startHopping(
        interfaceName: String,
        channels: List<Int> = CHANNELS_2_4GHZ,
        dwellTimeMs: Long = 500,
    ): Flow<Int> = flow {
        val chipInfo = chipsetMonitorHelper.detectChipVendor()
        val useMtk = chipInfo.vendor == WifiChipVendor.MEDIATEK && chipInfo.patchInstalled

        if (useMtk) {
            // MTK channel hopping via SET_TEST_CMD
            shellExecutor.executeAsRoot("/data/local/tmp/wpa_driver \"SET_TEST_MODE 2011\"")
        }

        var index = 0
        while (true) {
            val channel = channels[index % channels.size]

            val success = if (useMtk) {
                val freq = channelToFreq(channel)
                val result = shellExecutor.executeAsRoot("/data/local/tmp/wpa_driver \"SET_TEST_CMD 1 $freq\"")
                result.isSuccess
            } else {
                val result = shellExecutor.executeAsRoot("iw dev $interfaceName set channel $channel 2>/dev/null")
                result.isSuccess
            }

            if (success) emit(channel)
            delay(dwellTimeMs)
            index++
        }
    }.flowOn(Dispatchers.IO)

    fun stopHopping() {
        val chipInfo = chipsetMonitorHelper.detectChipVendor()
        if (chipInfo.vendor == WifiChipVendor.MEDIATEK && chipInfo.patchInstalled) {
            shellExecutor.executeAsRoot("/data/local/tmp/wpa_driver \"SET_TEST_MODE 0\"")
        }
    }

    fun setChannel(interfaceName: String, channel: Int): Boolean {
        val chipInfo = chipsetMonitorHelper.detectChipVendor()
        return if (chipInfo.vendor == WifiChipVendor.MEDIATEK && chipInfo.patchInstalled) {
            val freq = channelToFreq(channel)
            shellExecutor.executeAsRoot("/data/local/tmp/wpa_driver \"SET_TEST_CMD 1 $freq\"").isSuccess
        } else {
            shellExecutor.executeAsRoot("iw dev $interfaceName set channel $channel 2>/dev/null").isSuccess
        }
    }

    fun getCurrentChannel(interfaceName: String): Int {
        val result = shellExecutor.executeAsRoot("iw dev $interfaceName info 2>/dev/null")
        if (result.isSuccess) {
            val match = Regex("channel (\\d+)").find(result.stdout)
            return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        return 0
    }
}
