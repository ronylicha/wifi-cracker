package com.wificracker.scan.domain

import com.wificracker.core.root.ShellExecutor
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
) {

    companion object {
        val CHANNELS_2_4GHZ = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
        val CHANNELS_5GHZ = listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
        val ALL_CHANNELS = CHANNELS_2_4GHZ + CHANNELS_5GHZ
    }

    fun startHopping(
        interfaceName: String,
        channels: List<Int> = CHANNELS_2_4GHZ,
        dwellTimeMs: Long = 250,
    ): Flow<Int> = flow {
        var index = 0
        while (true) {
            val channel = channels[index % channels.size]
            val result = shellExecutor.executeAsRoot("iw dev $interfaceName set channel $channel 2>/dev/null || iwconfig $interfaceName channel $channel 2>/dev/null")
            if (result.isSuccess) {
                emit(channel)
            }
            delay(dwellTimeMs)
            index++
        }
    }.flowOn(Dispatchers.IO)

    fun setChannel(interfaceName: String, channel: Int): Boolean {
        val result = shellExecutor.executeAsRoot("iw dev $interfaceName set channel $channel 2>/dev/null || iwconfig $interfaceName channel $channel 2>/dev/null")
        return result.isSuccess
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
