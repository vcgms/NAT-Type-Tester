package com.nattype.tester.stun

/**
 * STUN 属性类型 (RFC 5389 Section 18, RFC 3489)
 */
enum class StunAttributeType(val value: Int) {
    // RFC 5389 Comprehension-required range (0x0000 - 0x7FFF)
    MAPPED_ADDRESS(0x0001),
    CHANGE_REQUEST(0x0003),         // RFC 3489
    USERNAME(0x0006),
    MESSAGE_INTEGRITY(0x0008),
    ERROR_CODE(0x0009),
    UNKNOWN_ATTRIBUTES(0x000A),
    REALM(0x0014),
    NONCE(0x0015),
    XOR_MAPPED_ADDRESS(0x0020),

    // RFC 5389 Comprehension-optional range (0x8000 - 0xFFFF)
    SOFTWARE(0x8022),
    ALTERNATE_SERVER(0x8023),
    FINGERPRINT(0x8028),

    // RFC 5780
    RESPONSE_ORIGIN(0x802B),
    OTHER_ADDRESS(0x802C),
    CHANGE_REQUEST_5780(0x8030),    // RFC 5780 CHANGE-REQUEST
    RESPONSE_PORT(0x8032);          // RFC 5780 NAT Behavior Discovery

    companion object {
        fun fromValue(value: Int): StunAttributeType? =
            entries.find { it.value == value }
    }
}
