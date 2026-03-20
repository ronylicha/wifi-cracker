package com.wificracker.core.wifi.monitor

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Ieee80211Frame(
    val frameType: Int,
    val frameSubtype: Int,
    val addr1: String,
    val addr2: String,
    val addr3: String,
    val ssid: String?,
    val channel: Int?,
    val rssi: Int?,
    val isBeacon: Boolean,
    val isProbeReq: Boolean,
    val isProbeResp: Boolean,
    val isData: Boolean,
    val isAuth: Boolean,
    val isDeauth: Boolean,
)

object Ieee80211Parser {

    private const val FRAME_TYPE_MGMT = 0
    private const val FRAME_TYPE_CTRL = 1
    private const val FRAME_TYPE_DATA = 2

    private const val SUBTYPE_BEACON = 8
    private const val SUBTYPE_PROBE_REQ = 4
    private const val SUBTYPE_PROBE_RESP = 5
    private const val SUBTYPE_AUTH = 11
    private const val SUBTYPE_DEAUTH = 12
    private const val SUBTYPE_DATA = 0
    private const val SUBTYPE_QOS_DATA = 8

    private const val TAG_SSID = 0
    private const val TAG_CHANNEL = 3

    fun parseFrame(raw: ByteArray): Ieee80211Frame? {
        if (raw.size < 24) return null

        val fc = ByteBuffer.wrap(raw, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        val frameType = (fc and 0x000C) shr 2
        val frameSubtype = (fc and 0x00F0) shr 4

        val addr1 = extractMac(raw, 4)
        val addr2 = extractMac(raw, 10)
        val addr3 = extractMac(raw, 16)

        val isBeacon = frameType == FRAME_TYPE_MGMT && frameSubtype == SUBTYPE_BEACON
        val isProbeReq = frameType == FRAME_TYPE_MGMT && frameSubtype == SUBTYPE_PROBE_REQ
        val isProbeResp = frameType == FRAME_TYPE_MGMT && frameSubtype == SUBTYPE_PROBE_RESP
        val isAuth = frameType == FRAME_TYPE_MGMT && frameSubtype == SUBTYPE_AUTH
        val isDeauth = frameType == FRAME_TYPE_MGMT && frameSubtype == SUBTYPE_DEAUTH
        val isData = frameType == FRAME_TYPE_DATA && (frameSubtype == SUBTYPE_DATA || frameSubtype == SUBTYPE_QOS_DATA)

        var ssid: String? = null
        var channel: Int? = null
        var rssi: Int? = null

        if (isBeacon || isProbeResp || isProbeReq) {
            val bodyStart = if (frameType == FRAME_TYPE_MGMT && isBeacon) 36 else 24
            if (raw.size > bodyStart) {
                val tags = extractTags(raw, bodyStart)
                ssid = tags[TAG_SSID]?.let { tryParseSsid(it) }
                channel = tags[TAG_CHANNEL]?.let { tryParseChannel(it) }
            }
        }

        return Ieee80211Frame(
            frameType = frameType,
            frameSubtype = frameSubtype,
            addr1 = addr1,
            addr2 = addr2,
            addr3 = addr3,
            ssid = ssid,
            channel = channel,
            rssi = rssi,
            isBeacon = isBeacon,
            isProbeReq = isProbeReq,
            isProbeResp = isProbeResp,
            isData = isData,
            isAuth = isAuth,
            isDeauth = isDeauth,
        )
    }

    private fun extractMac(data: ByteArray, offset: Int): String {
        if (offset + 6 > data.size) return "00:00:00:00:00:00"
        return String.format(
            "%02X:%02X:%02X:%02X:%02X:%02X",
            data[offset].toInt() and 0xFF,
            data[offset + 1].toInt() and 0xFF,
            data[offset + 2].toInt() and 0xFF,
            data[offset + 3].toInt() and 0xFF,
            data[offset + 4].toInt() and 0xFF,
            data[offset + 5].toInt() and 0xFF,
        )
    }

    private fun extractTags(raw: ByteArray, offset: Int): Map<Int, ByteArray> {
        val tags = mutableMapOf<Int, ByteArray>()
        var pos = offset
        while (pos + 2 <= raw.size) {
            val tagType = raw[pos].toInt() and 0xFF
            val tagLen = raw[pos + 1].toInt() and 0xFF
            pos += 2

            if (pos + tagLen > raw.size) break

            val tagData = ByteArray(tagLen)
            System.arraycopy(raw, pos, tagData, 0, tagLen)
            tags[tagType] = tagData
            pos += tagLen
        }
        return tags
    }

    private fun tryParseSsid(data: ByteArray): String? {
        if (data.isEmpty()) return null
        return try {
            String(data, Charsets.UTF_8)
                .filter { it.isPrintable() || it.isWhitespace() }
                .ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun tryParseChannel(data: ByteArray): Int? {
        return if (data.isNotEmpty()) {
            (data[0].toInt() and 0xFF)
        } else {
            null
        }
    }

    private fun Char.isPrintable(): Boolean {
        return this.code in 32..126 || this.code in 160..255
    }
}
