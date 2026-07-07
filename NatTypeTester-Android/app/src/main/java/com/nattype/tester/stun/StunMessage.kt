package com.nattype.tester.stun

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.security.SecureRandom

/**
 * STUN 消息 (RFC 5389 Section 6)
 *
 * 消息头结构 (20 字节):
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |0 0|     STUN Message Type     |         Message Length        |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                         Magic Cookie                          |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                                                               |
 *  |                     Transaction ID (96 bits)                  |
 *  |                                                               |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
class StunMessage private constructor(
    val messageType: Int,
    val transactionId: ByteArray,
    val attributes: List<StunAttribute>
) {
    companion object {
        const val HEADER_LENGTH = 20
        const val MAGIC_COOKIE = 0x2112A442
        const val FINGERPRINT_XOR = 0x5354554E

        private val RANDOM = SecureRandom()

        /**
         * 创建 STUN 绑定请求
         */
        fun createBindingRequest(attributes: List<StunAttribute> = emptyList()): StunMessage {
            val transactionId = ByteArray(12)
            RANDOM.nextBytes(transactionId)
            return StunMessage(StunMessageType.BINDING_REQUEST.value, transactionId, attributes)
        }

        /**
         * 编码 STUN 消息为字节数组
         */
        fun encode(msg: StunMessage): ByteArray {
            val output = ByteArrayOutputStream()
            val data = ByteArrayOutputStream()

            // 编码属性
            for (attr in msg.attributes) {
                // 16-bit type (Big-Endian)
                data.write(attr.type.value.ushr(8))
                data.write(attr.type.value.and(0xFF))
                // 16-bit length (Big-Endian)
                val len = attr.value.size
                data.write(len.ushr(8))
                data.write(len.and(0xFF))
                // Value
                data.write(attr.value)
                // Padding to 4-byte boundary
                val padding = (4 - (len % 4)) % 4
                if (padding > 0) {
                    data.write(ByteArray(padding))
                }
            }

            val attrBytes = data.toByteArray()

            // 消息头
            // 消息类型 (16-bit, Big-Endian)
            output.write(msg.messageType.ushr(8))
            output.write(msg.messageType.and(0xFF))
            // 消息长度 (16-bit, Big-Endian)
            output.write(attrBytes.size.ushr(8))
            output.write(attrBytes.size.and(0xFF))
            // Magic Cookie (32-bit)
            output.write(MAGIC_COOKIE.ushr(24))
            output.write((MAGIC_COOKIE.ushr(16) and 0xFF))
            output.write((MAGIC_COOKIE.ushr(8) and 0xFF))
            output.write((MAGIC_COOKIE and 0xFF))
            // Transaction ID (96-bit)
            output.write(msg.transactionId, 0, 12)
            // 属性
            output.write(attrBytes)

            return output.toByteArray()
        }

        /**
         * 解码字节数组为 STUN 消息
         */
        fun decode(data: ByteArray, offset: Int = 0, length: Int = data.size): StunMessage? {
            return try {
                val stream = ByteArrayInputStream(data, offset, length)

                // 读取消息头
                val buf = ByteArray(4)
                if (stream.read(buf) < 4) return null

                // 消息类型
                val msgType = ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
                // 消息长度
                val msgLength = ((buf[2].toInt() and 0xFF) shl 8) or (buf[3].toInt() and 0xFF)

                // Magic Cookie
                if (stream.read(buf) < 4) return null
                val magicCookie = ((buf[0].toInt() and 0xFF) shl 24) or
                    ((buf[1].toInt() and 0xFF) shl 16) or
                    ((buf[2].toInt() and 0xFF) shl 8) or
                    (buf[3].toInt() and 0xFF)

                // 如果 Magic Cookie 不匹配，可能是 RFC 3489 消息
                if (magicCookie != MAGIC_COOKIE) {
                    return decode3489(data, offset, length, msgType, msgLength)
                }

                // 事务 ID
                val transactionId = ByteArray(12)
                if (stream.read(transactionId) < 12) return null

                // 读取属性
                val attrs = mutableListOf<StunAttribute>()
                var remaining = msgLength
                val attrBuf = ByteArray(4)

                while (remaining >= 4) {
                    if (stream.read(attrBuf) < 4) break
                    val attrType = ((attrBuf[0].toInt() and 0xFF) shl 8) or (attrBuf[1].toInt() and 0xFF)
                    val attrLen = ((attrBuf[2].toInt() and 0xFF) shl 8) or (attrBuf[3].toInt() and 0xFF)
                    remaining -= 4

                    if (remaining < attrLen) break

                    val typeEnum = StunAttributeType.fromValue(attrType)
                    val attrValue = ByteArray(attrLen)
                    if (stream.read(attrValue) < attrLen) break
                    remaining -= attrLen

                    // 跳过 Padding
                    val padding = (4 - (attrLen % 4)) % 4
                    if (padding > 0) {
                        stream.skip(padding.toLong())
                        remaining -= padding
                    }

                    if (typeEnum != null) {
                        attrs.add(StunAttribute(typeEnum, attrValue))
                    }
                }

                StunMessage(msgType, transactionId, attrs)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 解码 RFC 3489 格式消息 (无 Magic Cookie)
         */
        private fun decode3489(
            data: ByteArray,
            offset: Int,
            length: Int,
            msgType: Int,
            msgLength: Int
        ): StunMessage? {
            try {
                val stream = ByteArrayInputStream(data, offset, length)
                stream.skip(8) // Skip type + length + first 4 bytes of what would be magic cookie

                val transactionId = ByteArray(16)
                if (stream.read(transactionId) < 16) return null

                val attrs = mutableListOf<StunAttribute>()
                var remaining = msgLength
                val attrBuf = ByteArray(4)

                while (remaining >= 4) {
                    if (stream.read(attrBuf) < 4) break
                    val attrType = ((attrBuf[0].toInt() and 0xFF) shl 8) or (attrBuf[1].toInt() and 0xFF)
                    val attrLen = ((attrBuf[2].toInt() and 0xFF) shl 8) or (attrBuf[3].toInt() and 0xFF)
                    remaining -= 4

                    if (remaining < attrLen) break

                    val typeEnum = StunAttributeType.fromValue(attrType)
                    val attrValue = ByteArray(attrLen)
                    if (stream.read(attrValue) < attrLen) break
                    remaining -= attrLen

                    // Skip padding
                    val padding = (4 - (attrLen % 4)) % 4
                    if (padding > 0) {
                        stream.skip(padding.toLong())
                        remaining -= padding
                    }

                    if (typeEnum != null) {
                        attrs.add(StunAttribute(typeEnum, attrValue))
                    }
                }

                return StunMessage(msgType, transactionId.copyOf(12), attrs)
            } catch (e: Exception) {
                return null
            }
        }
    }

    /**
     * 获取指定类型的属性
     */
    fun getAttribute(type: StunAttributeType): StunAttribute? =
        attributes.find { it.type == type }

    /**
     * 获取映射地址 (优先 XOR-MAPPED-ADDRESS，其次 MAPPED-ADDRESS)
     */
    fun getMappedAddress(): InetSocketAddress? {
        val xorAddr = getAttribute(StunAttributeType.XOR_MAPPED_ADDRESS)
        if (xorAddr != null) {
            return StunAttributeParser.parseXorMappedAddress(xorAddr.value, transactionId)
        }
        val mappedAddr = getAttribute(StunAttributeType.MAPPED_ADDRESS)
        if (mappedAddr != null) {
            return StunAttributeParser.parseMappedAddress(mappedAddr.value)
        }
        return null
    }

    /**
     * 获取响应来源地址
     */
    fun getResponseOrigin(): InetSocketAddress? {
        val attr = getAttribute(StunAttributeType.RESPONSE_ORIGIN) ?: return null
        return StunAttributeParser.parseMappedAddress(attr.value)
    }

    /**
     * 获取其他地址
     */
    fun getOtherAddress(): InetSocketAddress? {
        val attr = getAttribute(StunAttributeType.OTHER_ADDRESS) ?: return null
        return StunAttributeParser.parseMappedAddress(attr.value)
    }

    fun isSuccessResponse(): Boolean = StunMessageType.isSuccessResponse(messageType)
    fun isErrorResponse(): Boolean = StunMessageType.isErrorResponse(messageType)
}
