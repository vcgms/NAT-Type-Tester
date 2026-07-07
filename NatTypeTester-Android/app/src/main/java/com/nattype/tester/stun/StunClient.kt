package com.nattype.tester.stun

import java.net.InetSocketAddress

/**
 * STUN 客户端接口
 */
interface StunClient {
    /**
     * 向 STUN 服务器发送绑定请求并返回结果
     *
     * @param serverAddress STUN 服务器地址
     * @param localAddress 本地绑定的地址 (可选，null 表示系统自动分配)
     * @param timeoutMs 超时时间 (毫秒)
     * @return 查询结果
     */
    suspend fun query(
        serverAddress: InetSocketAddress,
        localAddress: InetSocketAddress? = null,
        timeoutMs: Int = 3000
    ): StunResult

    /**
     * 关闭客户端，释放资源
     */
    fun close()
}
