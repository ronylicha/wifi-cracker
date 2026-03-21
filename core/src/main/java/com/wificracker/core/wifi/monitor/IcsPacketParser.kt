package com.wificracker.core.wifi.monitor

import java.nio.ByteBuffer
import java.nio.ByteOrder

const val ICS_MAGIC = 0x44d9c99aL
const val TIMESYNC_INFO = 0x0008011000000000L
const val MTK_RX_DESC_SIZE = 120
const val ICS_FIXED_PKT_SIZE = 320

data class IcsPacket(
    val sequence: Int,
    val frameLength: Int,
    val rawFrame: ByteArray,
    val isTimesync: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IcsPacket

        if (sequence != other.sequence) return false
        if (frameLength != other.frameLength) return false
        if (!rawFrame.contentEquals(other.rawFrame)) return false
        if (isTimesync != other.isTimesync) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sequence
        result = 31 * result + frameLength
        result = 31 * result + rawFrame.contentHashCode()
        result = 31 * result + isTimesync.hashCode()
        return result
    }
}

object IcsPacketParser {

    fun parsePacket(buffer: ByteArray, offset: Int): IcsPacket? {
        if (offset + 16 > buffer.size) return null

        val magic = ByteBuffer.wrap(buffer, offset, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
            .toLong() and 0xFFFFFFFFL

        if (magic != ICS_MAGIC) return null

        val type = ByteBuffer.wrap(buffer, offset + 4, 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short
            .toInt()

        val seq = ByteBuffer.wrap(buffer, offset + 6, 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short
            .toInt()

        val info = ByteBuffer.wrap(buffer, offset + 8, 8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .long

        val isTimesync = info == TIMESYNC_INFO

        if (isTimesync) {
            if (offset + 24 > buffer.size) return null
            return IcsPacket(
                sequence = seq,
                frameLength = 0,
                rawFrame = byteArrayOf(),
                isTimesync = true,
            )
        }

        val frameLength = ByteBuffer.wrap(buffer, offset + 14, 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short
            .toInt() and 0xFFFF

        if (frameLength == 0 || frameLength > 4096) return null

        val headerSize = 16
        val frameDataStart = offset + headerSize + MTK_RX_DESC_SIZE
        // frameLength in ICS header INCLUDES the RX descriptor — real frame is smaller
        val realFrameLength = minOf(frameLength - MTK_RX_DESC_SIZE, ICS_FIXED_PKT_SIZE - headerSize - MTK_RX_DESC_SIZE)

        if (realFrameLength <= 0 || frameDataStart + realFrameLength > buffer.size) return null

        val rawFrame = ByteArray(realFrameLength)
        System.arraycopy(buffer, frameDataStart, rawFrame, 0, realFrameLength)

        return IcsPacket(
            sequence = seq,
            frameLength = frameLength,
            rawFrame = rawFrame,
            isTimesync = false,
        )
    }

    fun findNextPacketOffset(buffer: ByteArray, offset: Int): Int? {
        // ICS packets are fixed 320 bytes — try aligned first, then scan
        var current = offset
        while (current + ICS_FIXED_PKT_SIZE <= buffer.size) {
            val magic = ByteBuffer.wrap(buffer, current, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
                .toLong() and 0xFFFFFFFFL

            if (magic == ICS_MAGIC) return current
            // If not aligned, scan byte-by-byte until we find the next magic
            current++
        }
        return null
    }

    /** Advance to next packet (fixed 320-byte size). */
    fun nextPacketOffset(currentOffset: Int): Int = currentOffset + ICS_FIXED_PKT_SIZE
}
