package com.nattype.tester.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * STUN 服务器配置
 */
data class StunServer(
    val host: String,
    val port: Int = 3478,
    val label: String = host
) {
    /**
     * 获取主地址
     */
    suspend fun getAddress(): InetSocketAddress? = withContext(Dispatchers.IO) {
        return@withContext try {
            val addr = InetAddress.getByName(host)
            InetSocketAddress(addr, port)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取备用地址 (如果服务器有多个 IP)
     */
    suspend fun getAlternateAddresses(): List<InetSocketAddress> = withContext(Dispatchers.IO) {
        return@withContext try {
            InetAddress.getAllByName(host).map { InetSocketAddress(it, port) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        /**
         * 内置公共 STUN 服务器列表
         */
        val DEFAULT_SERVERS = listOf(
            StunServer("stun.miwifi.com", 3478, "小米"),
            StunServer("stun.l.google.com", 19302, "Google (Primary)"),
            StunServer("stun.cloudflare.com", 3478, "Cloudflare"),
        )

        /**
         * 解析自定义服务器地址 "host:port" 或 "host"
         */
        fun parse(hostPort: String): StunServer? {
            val trimmed = hostPort.trim()
            if (trimmed.isEmpty()) return null
            val colonIdx = trimmed.lastIndexOf(':')
            return if (colonIdx > 0 && colonIdx < trimmed.length - 1) {
                val port = trimmed.substring(colonIdx + 1).toIntOrNull()
                if (port != null && port in 1..65535) {
                    StunServer(trimmed.substring(0, colonIdx), port, trimmed)
                } else {
                    StunServer(trimmed, 3478, trimmed)
                }
            } else {
                StunServer(trimmed, 3478, trimmed)
            }
        }
    }
}
