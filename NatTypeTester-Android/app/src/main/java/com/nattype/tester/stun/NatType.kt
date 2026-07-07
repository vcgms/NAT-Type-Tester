package com.nattype.tester.stun

/**
 * NAT 映射行为 (RFC 5389 / RFC 5780)
 */
enum class MappingBehavior(val displayName: String, val englishName: String, val abbreviation: String, val description: String) {
    ENDPOINT_INDEPENDENT("端点无关映射", "Endpoint-Independent Mapping", "EIM", "无论目标地址如何变化，相同内网地址和端口始终映射到相同的公网地址和端口"),
    ADDRESS_DEPENDENT("地址相关映射", "Address-Dependent Mapping", "ADM", "相同内网地址发往不同目标IP地址时，会映射到不同的公网端口"),
    ADDRESS_AND_PORT_DEPENDENT("地址和端口相关映射", "Address and Port-Dependent Mapping", "APDM", "相同内网地址发往不同目标IP或端口时，都会映射到不同的公网端口"),
    UNKNOWN("未知", "Unknown", "N/A", "无法判断映射行为");

    override fun toString(): String = displayName
}

/**
 * NAT 过滤行为 (RFC 5389 / RFC 5780)
 */
enum class FilteringBehavior(val displayName: String, val englishName: String, val abbreviation: String, val description: String) {
    ENDPOINT_INDEPENDENT("端点无关过滤", "Endpoint-Independent Filtering", "EIF", "不限制外部来源，任何外部主机都可以向该映射地址发送数据包并被转发到内网"),
    ADDRESS_DEPENDENT("地址相关过滤", "Address-Dependent Filtering", "ADF", "仅允许内网主机曾主动发送过数据的目标IP地址发来的数据包通过"),
    ADDRESS_AND_PORT_DEPENDENT("地址和端口相关过滤", "Address and Port-Dependent Filtering", "APDF", "仅允许内网主机曾主动发送过数据的目标IP和端口发来的数据包通过"),
    UNKNOWN("未知", "Unknown", "N/A", "无法判断过滤行为");

    override fun toString(): String = displayName
}

/**
 * 综合 NAT 类型
 */
enum class NatType(
    val displayName: String,
    val shortName: String,
    val englishName: String,
    val description: String,
    val mapping: MappingBehavior,
    val filtering: FilteringBehavior
) {
    FULL_CONE(
        "全锥形 NAT", "全锥形NAT", "Full Cone NAT",
        "任何外部主机都可以通过映射后的公网地址向内部主机发送数据包",
        MappingBehavior.ENDPOINT_INDEPENDENT, FilteringBehavior.ENDPOINT_INDEPENDENT
    ),
    RESTRICTED_CONE(
        "受限锥形 NAT", "受限锥形NAT", "Restricted Cone NAT",
        "只有内部主机之前发送过数据包的外部IP地址才能向内网发送数据包",
        MappingBehavior.ENDPOINT_INDEPENDENT, FilteringBehavior.ADDRESS_DEPENDENT
    ),
    PORT_RESTRICTED_CONE(
        "端口受限锥形 NAT", "端口受限锥形NAT", "Port Restricted Cone NAT",
        "只有内部主机之前发送过数据包的外部IP:Port才能向内网发送数据包",
        MappingBehavior.ENDPOINT_INDEPENDENT, FilteringBehavior.ADDRESS_AND_PORT_DEPENDENT
    ),
    SYMMETRIC(
        "对称型 NAT", "对称型NAT", "Symmetric NAT",
        "每个来自相同内网IP和端口到特定目标IP和端口的请求都映射到唯一的公网IP和端口",
        MappingBehavior.ADDRESS_AND_PORT_DEPENDENT, FilteringBehavior.ADDRESS_AND_PORT_DEPENDENT
    ),
    UDP_BLOCKED(
        "UDP 被阻断", "UDP 被阻断", "UDP Blocked",
        "UDP 通信被防火墙阻断，无法进行 NAT 类型检测",
        MappingBehavior.UNKNOWN, FilteringBehavior.UNKNOWN
    ),
    UNKNOWN(
        "未知", "未知", "Unknown",
        "无法判断 NAT 类型",
        MappingBehavior.UNKNOWN, FilteringBehavior.UNKNOWN
    ),
    OTHER(
        "其他类型", "其他类型", "Other",
        "当前网络的映射行为和过滤行为不在传统协议 RFC 3489 的定义中",
        MappingBehavior.UNKNOWN, FilteringBehavior.UNKNOWN
    );

    override fun toString(): String = displayName

    companion object {
        /**
         * 根据映射行为和过滤行为确定 NAT 类型（RFC 5780）
         *
         * 合法组合：
         *   EIM  × EIF   → Full Cone（全锥形）
         *   EIM  × ADF   → Restricted Cone（受限锥形）
         *   EIM  × APDF  → Port Restricted Cone（端口受限锥形）
         *   ADM  × ADF   → Symmetric（对称型）
         *   ADM  × APDF  → Symmetric（对称型）
         *   APDM × APDF  → Symmetric（对称型）
         *
         * 非法/矛盾组合（如 APDM+EIF、ADM+EIF、APDM+ADF）→ OTHER（其他类型）
         */
        fun classify(mapping: MappingBehavior, filtering: FilteringBehavior): NatType {
            return when {
                mapping == MappingBehavior.UNKNOWN || filtering == FilteringBehavior.UNKNOWN -> UNKNOWN
                // EIM 系列
                mapping == MappingBehavior.ENDPOINT_INDEPENDENT && filtering == FilteringBehavior.ENDPOINT_INDEPENDENT -> FULL_CONE
                mapping == MappingBehavior.ENDPOINT_INDEPENDENT && filtering == FilteringBehavior.ADDRESS_DEPENDENT -> RESTRICTED_CONE
                mapping == MappingBehavior.ENDPOINT_INDEPENDENT && filtering == FilteringBehavior.ADDRESS_AND_PORT_DEPENDENT -> PORT_RESTRICTED_CONE
                // ADM 系列（仅 ADF/APDF 合法）
                mapping == MappingBehavior.ADDRESS_DEPENDENT && filtering == FilteringBehavior.ADDRESS_DEPENDENT -> SYMMETRIC
                mapping == MappingBehavior.ADDRESS_DEPENDENT && filtering == FilteringBehavior.ADDRESS_AND_PORT_DEPENDENT -> SYMMETRIC
                // APDM 系列（仅 APDF 合法）
                mapping == MappingBehavior.ADDRESS_AND_PORT_DEPENDENT && filtering == FilteringBehavior.ADDRESS_AND_PORT_DEPENDENT -> SYMMETRIC
                // 其他组合为矛盾/检测异常
                else -> OTHER
            }
        }
    }
}
