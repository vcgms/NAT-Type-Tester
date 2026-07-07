package com.nattype.tester.stun

/**
 * STUN 消息类型 (RFC 5389 Section 6)
 *
 * STUN 消息类型由 14-bit 主类型和 2-bit 子类型组成:
 *   0b00_yyyyyyyyyyyy_yyMM
 *      M: 00=Request, 01=Indication, 10=Success Response, 11=Error Response
 *      C: Class bit (bit 8 for Request, bit 9 for Indication)
 */
enum class StunMessageType(val value: Int) {
    // RFC 5389
    BINDING_REQUEST(0x0001),
    BINDING_RESPONSE(0x0101),
    BINDING_ERROR_RESPONSE(0x0111),
    BINDING_INDICATION(0x0011),

    // RFC 3489 (Legacy)
    BINDING_REQUEST_3489(0x0001);

    companion object {
        fun fromValue(value: Int): StunMessageType? =
            entries.find { it.value == value }

        fun isRequest(value: Int): Boolean =
            (value and 0x0110) == 0x0000

        fun isIndication(value: Int): Boolean =
            (value and 0x0110) == 0x0010

        fun isSuccessResponse(value: Int): Boolean =
            (value and 0x0110) == 0x0100

        fun isErrorResponse(value: Int): Boolean =
            (value and 0x0110) == 0x0110
    }
}
