package com.nattype.tester.stun

import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * STUN 属性 (TLV 格式)
 *
 * 结构:
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |         Type                  |            Length             |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                         Value (variable)                ....
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
data class StunAttribute(
    val type: StunAttributeType,
    val value: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StunAttribute) return false
        return type == other.type && value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}

/**
 * STUN 属性构建器
 */
object StunAttributeBuilder {
    private const val MAGIC_COOKIE = 0x2112A442

    /**
     * 构建 MAPPED-ADDRESS 属性 (RFC 5389 Section 15.1)
     */
    fun buildMappedAddress(address: InetSocketAddress): StunAttribute {
        val output = ByteArrayOutputStream()
        output.write(0)     // Reserved
        output.write(0x01)  // Family: IPv4
        output.write(address.port.ushr(8))
        output.write(address.port.and(0xFF))
        output.write(address.address.address)
        return StunAttribute(StunAttributeType.MAPPED_ADDRESS, output.toByteArray())
    }

    /**
     * 构建 XOR-MAPPED-ADDRESS 属性 (RFC 5389 Section 15.2)
     */
    fun buildXorMappedAddress(address: InetSocketAddress, transactionId: ByteArray): StunAttribute {
        val output = ByteArrayOutputStream()
        output.write(0)
        val family: Int = when (address.address) {
            is Inet4Address -> 0x01
            is Inet6Address -> 0x02
            else -> 0x01
        }
        output.write(family)
        // XOR Port with first 2 bytes of Magic Cookie
        val xPort = address.port.xor(MAGIC_COOKIE.ushr(16))
        output.write(xPort.ushr(8))
        output.write(xPort.and(0xFF))
        // XOR Address with Magic Cookie for IPv4, or Magic Cookie + Transaction ID for IPv6
        when (address.address) {
            is Inet4Address -> {
                val ipBytes = address.address.address
                val cookie = byteArrayOf(
                    (MAGIC_COOKIE.ushr(24) and 0xFF).toByte(),
                    (MAGIC_COOKIE.ushr(16) and 0xFF).toByte(),
                    (MAGIC_COOKIE.ushr(8) and 0xFF).toByte(),
                    (MAGIC_COOKIE and 0xFF).toByte()
                )
                for (i in ipBytes.indices) {
                    output.write(ipBytes[i].toInt().xor(cookie[i].toInt()))
                }
            }
            is Inet6Address -> {
                val ipBytes = address.address.address
                val xorBytes = ByteArray(16)
                val cookie = byteArrayOf(
                    (MAGIC_COOKIE.ushr(24) and 0xFF).toByte(),
                    (MAGIC_COOKIE.ushr(16) and 0xFF).toByte(),
                    (MAGIC_COOKIE.ushr(8) and 0xFF).toByte(),
                    (MAGIC_COOKIE and 0xFF).toByte()
                )
                for (i in 0..3) xorBytes[i] = cookie[i]
                for (i in 4..15) xorBytes[i] = transactionId[i - 4]
                for (i in ipBytes.indices) {
                    output.write(ipBytes[i].toInt().xor(xorBytes[i].toInt()))
                }
            }
        }
        return StunAttribute(StunAttributeType.XOR_MAPPED_ADDRESS, output.toByteArray())
    }

    /**
     * 构建 CHANGE-REQUEST 属性 (RFC 5780, 类型 0x8030)
     * 使用 RFC 5780 的 comprehension-optional 范围属性，兼容 RFC 5389 服务器
     */
    fun buildChangeRequest(changeIp: Boolean, changePort: Boolean): StunAttribute {
        val value = ByteArray(4)
        if (changeIp) value[3] = (value[3].toInt() or 0x04).toByte()
        if (changePort) value[3] = (value[3].toInt() or 0x02).toByte()
        return StunAttribute(StunAttributeType.CHANGE_REQUEST_5780, value)
    }

    /**
     * 构建 RESPONSE-ORIGIN 属性
     */
    fun buildResponseOrigin(address: InetSocketAddress): StunAttribute {
        return buildMappedAddress(address).copy(type = StunAttributeType.RESPONSE_ORIGIN)
    }

    /**
     * 构建 OTHER-ADDRESS 属性
     */
    fun buildOtherAddress(address: InetSocketAddress): StunAttribute {
        return buildMappedAddress(address).copy(type = StunAttributeType.OTHER_ADDRESS)
    }
}

/**
 * STUN 属性解析器
 */
object StunAttributeParser {

    /**
     * 从属性值解析 MAPPED-ADDRESS (RFC 5389 Section 15.1)
     */
    fun parseMappedAddress(value: ByteArray): InetSocketAddress? {
        if (value.size < 6) return null
        val family = value[1].toInt() and 0xFF
        val port = ((value[2].toInt() and 0xFF) shl 8) or (value[3].toInt() and 0xFF)
        return when (family) {
            0x01 -> parseIPv4(value, 4, port)
            0x02 -> parseIPv6(value, 4, port)
            else -> null
        }
    }

    /**
     * 从属性值解析 XOR-MAPPED-ADDRESS (RFC 5389 Section 15.2)
     */
    fun parseXorMappedAddress(value: ByteArray, transactionId: ByteArray): InetSocketAddress? {
        if (value.size < 6) return null
        val family = value[1].toInt() and 0xFF
        val xPort = ((value[2].toInt() and 0xFF) shl 8) or (value[3].toInt() and 0xFF)
        val port = xPort.xor(MAGIC_COOKIE.ushr(16))
        return when (family) {
            0x01 -> parseXorIPv4(value, 4, port)
            0x02 -> parseXorIPv6(value, 4, port, transactionId)
            else -> null
        }
    }

    private const val MAGIC_COOKIE = 0x2112A442

    private fun parseIPv4(value: ByteArray, offset: Int, port: Int): InetSocketAddress? {
        if (value.size < offset + 4) return null
        val ipBytes = ByteArray(4)
        System.arraycopy(value, offset, ipBytes, 0, 4)
        return InetSocketAddress(InetAddress.getByAddress(ipBytes), port)
    }

    private fun parseIPv6(value: ByteArray, offset: Int, port: Int): InetSocketAddress? {
        if (value.size < offset + 16) return null
        val ipBytes = ByteArray(16)
        System.arraycopy(value, offset, ipBytes, 0, 16)
        return InetSocketAddress(InetAddress.getByAddress(ipBytes), port)
    }

    private fun parseXorIPv4(value: ByteArray, offset: Int, port: Int): InetSocketAddress? {
        if (value.size < offset + 4) return null
        val cookie = byteArrayOf(
            (MAGIC_COOKIE.ushr(24) and 0xFF).toByte(),
            (MAGIC_COOKIE.ushr(16) and 0xFF).toByte(),
            (MAGIC_COOKIE.ushr(8) and 0xFF).toByte(),
            (MAGIC_COOKIE and 0xFF).toByte()
        )
        val ipBytes = ByteArray(4)
        for (i in 0..3) {
            ipBytes[i] = (value[offset + i].toInt().xor(cookie[i].toInt())).toByte()
        }
        return InetSocketAddress(InetAddress.getByAddress(ipBytes), port)
    }

    private fun parseXorIPv6(value: ByteArray, offset: Int, port: Int, transactionId: ByteArray): InetSocketAddress? {
        if (value.size < offset + 16) return null
        val xorBytes = ByteArray(16)
        val cookie = byteArrayOf(
            (MAGIC_COOKIE.ushr(24) and 0xFF).toByte(),
            (MAGIC_COOKIE.ushr(16) and 0xFF).toByte(),
            (MAGIC_COOKIE.ushr(8) and 0xFF).toByte(),
            (MAGIC_COOKIE and 0xFF).toByte()
        )
        for (i in 0..3) xorBytes[i] = cookie[i]
        for (i in 4..15) xorBytes[i] = transactionId[i - 4]
        val ipBytes = ByteArray(16)
        for (i in ipBytes.indices) {
            ipBytes[i] = (value[offset + i].toInt().xor(xorBytes[i].toInt())).toByte()
        }
        return InetSocketAddress(InetAddress.getByAddress(ipBytes), port)
    }
}
