package com.nattype.tester.stun

import java.net.InetSocketAddress

/**
 * STUN 查询结果
 */
data class StunResult(
    /** 公网映射地址 */
    val publicAddress: InetSocketAddress? = null,
    /** 响应来源地址 */
    val responseOrigin: InetSocketAddress? = null,
    /** 其他地址 (RFC 5780，用于 NAT 检测) */
    val otherAddress: InetSocketAddress? = null,
    /** NAT 映射行为 */
    val mappingBehavior: MappingBehavior = MappingBehavior.UNKNOWN,
    /** NAT 过滤行为 */
    val filteringBehavior: FilteringBehavior = FilteringBehavior.UNKNOWN,
    /** 综合 NAT 类型 */
    val natType: NatType = NatType.UNKNOWN,
    /** 本地端点 */
    val localAddress: InetSocketAddress? = null,
    /** 是否成功 */
    val isSuccess: Boolean = false,
    /** 错误信息 */
    val errorMessage: String? = null,
    /** 测试日志 */
    val logs: List<String> = emptyList()
)
