package com.nattype.tester.stun.client

import com.nattype.tester.stun.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * UDP STUN 客户端
 */
class StunClientUDP : StunClient {

    private var socket: DatagramSocket? = null

    override suspend fun query(
        serverAddress: InetSocketAddress,
        localAddress: InetSocketAddress?,
        timeoutMs: Int
    ): StunResult = withContext(Dispatchers.IO) {
        try {
            // 创建 UDP Socket
            val sock = if (localAddress != null) {
                DatagramSocket(localAddress)
            } else {
                DatagramSocket()
            }
            socket = sock
            sock.soTimeout = timeoutMs

            performQuery(sock, serverAddress)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            StunResult(
                isSuccess = false,
                errorMessage = "STUN 请求超时 ($timeoutMs ms)",
                logs = listOf("STUN 请求超时: ${e.message}")
            )
        } catch (e: Exception) {
            StunResult(
                isSuccess = false,
                errorMessage = "STUN 请求失败: ${e.message}",
                logs = listOf("STUN 请求异常: ${e.message}")
            )
        } finally {
            close()
        }
    }

    /**
     * 使用已有 Socket 发送 STUN 查询（复用同一本地端点）
     */
    suspend fun query(
        socket: DatagramSocket,
        serverAddress: InetSocketAddress,
        timeoutMs: Int = 3000
    ): StunResult = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = timeoutMs
            performQuery(socket, serverAddress)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            StunResult(
                isSuccess = false,
                errorMessage = "STUN 请求超时 ($timeoutMs ms)",
                logs = listOf("STUN 请求超时: ${e.message}")
            )
        } catch (e: Exception) {
            StunResult(
                isSuccess = false,
                errorMessage = "STUN 请求失败: ${e.message}",
                logs = listOf("STUN 请求异常: ${e.message}")
            )
        }
    }

    /**
     * 执行 STUN 查询的核心流程（编码-发送-接收-解码）
     */
    private suspend fun performQuery(
        sock: DatagramSocket,
        serverAddress: InetSocketAddress
    ): StunResult {
        val localEndpoint = InetSocketAddress(sock.localAddress, sock.localPort)

        // 构建并发送 STUN 绑定请求
        val request = StunMessage.createBindingRequest()
        val requestBytes = StunMessage.encode(request)
        val sendPacket = DatagramPacket(requestBytes, requestBytes.size, serverAddress)
        sock.send(sendPacket)

        // 接收响应
        val receiveBuffer = ByteArray(2048)
        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

        withTimeout(sock.soTimeout.toLong()) {
            sock.receive(receivePacket)
        }

        // 解码响应
        val response = StunMessage.decode(
            receivePacket.data,
            receivePacket.offset,
            receivePacket.length
        )

        return if (response != null && response.isSuccessResponse()) {
            val mappedAddress = response.getMappedAddress()
            val responseOrigin = response.getResponseOrigin()
            val otherAddress = response.getOtherAddress()

            StunResult(
                publicAddress = mappedAddress,
                responseOrigin = responseOrigin,
                otherAddress = otherAddress,
                localAddress = localEndpoint,
                isSuccess = true,
                logs = listOf(
                    "UDP 绑定请求成功",
                    "本地端点: $localEndpoint",
                    "映射地址: ${mappedAddress ?: "N/A"}",
                    "响应来源: ${responseOrigin ?: "N/A"}",
                    "其他地址: ${otherAddress ?: "N/A"}"
                )
            )
        } else {
            StunResult(
                localAddress = localEndpoint,
                isSuccess = false,
                errorMessage = "无效的 STUN 响应",
                logs = listOf("收到无效的 STUN 响应 (长度=${receivePacket.length})")
            )
        }
    }

    /**
     * 发送绑定请求但不读取响应（用于 RFC 3489 Change Request 测试）
     */
    suspend fun sendOnly(
        serverAddress: InetSocketAddress,
        localAddress: InetSocketAddress? = null,
        timeoutMs: Int = 3000
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sock = DatagramSocket()
            try {
                sock.soTimeout = timeoutMs
                val request = StunMessage.createBindingRequest()
                val requestBytes = StunMessage.encode(request)
                val sendPacket = DatagramPacket(requestBytes, requestBytes.size, serverAddress)
                sock.send(sendPacket)
                true
            } finally {
                sock.close()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 发送带 CHANGE-REQUEST 的绑定请求（RFC 5780）
     */
    suspend fun queryWithChangeRequest(
        serverAddress: InetSocketAddress,
        changeIp: Boolean,
        changePort: Boolean,
        timeoutMs: Int = 3000
    ): StunResult = withContext(Dispatchers.IO) {
        try {
            val sock = DatagramSocket()
            socket = sock
            sock.soTimeout = timeoutMs

            val localEndpoint = InetSocketAddress(sock.localAddress, sock.localPort)

            val changeRequest = StunAttributeBuilder.buildChangeRequest(changeIp, changePort)
            val request = StunMessage.createBindingRequest(listOf(changeRequest))
            val requestBytes = StunMessage.encode(request)
            val sendPacket = DatagramPacket(requestBytes, requestBytes.size, serverAddress)
            sock.send(sendPacket)

            val receiveBuffer = ByteArray(2048)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            withTimeout(timeoutMs.toLong()) {
                sock.receive(receivePacket)
            }

            val response = StunMessage.decode(
                receivePacket.data,
                receivePacket.offset,
                receivePacket.length
            )

            if (response != null && response.isSuccessResponse()) {
                val mappedAddress = response.getMappedAddress()
                val responseOrigin = response.getResponseOrigin()
                StunResult(
                    publicAddress = mappedAddress,
                    responseOrigin = responseOrigin,
                    localAddress = localEndpoint,
                    isSuccess = true
                )
            } else {
                StunResult(
                    localAddress = localEndpoint,
                    isSuccess = false,
                    errorMessage = "无效响应"
                )
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            StunResult(isSuccess = false, errorMessage = "超时")
        } catch (e: Exception) {
            StunResult(isSuccess = false, errorMessage = e.message)
        } finally {
            close()
        }
    }

    /**
     * 使用已有 Socket 发送带 CHANGE-REQUEST 的绑定请求（RFC 5780）
     * 复用同一本地端点，确保过滤行为检测期间 NAT 映射一致
     */
    suspend fun queryWithChangeRequest(
        socket: DatagramSocket,
        serverAddress: InetSocketAddress,
        changeIp: Boolean,
        changePort: Boolean,
        timeoutMs: Int = 3000
    ): StunResult = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = timeoutMs
            val localEndpoint = InetSocketAddress(socket.localAddress, socket.localPort)

            val changeRequest = StunAttributeBuilder.buildChangeRequest(changeIp, changePort)
            val request = StunMessage.createBindingRequest(listOf(changeRequest))
            val requestBytes = StunMessage.encode(request)
            val sendPacket = DatagramPacket(requestBytes, requestBytes.size, serverAddress)
            socket.send(sendPacket)

            val receiveBuffer = ByteArray(2048)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            withTimeout(timeoutMs.toLong()) {
                socket.receive(receivePacket)
            }

            val response = StunMessage.decode(
                receivePacket.data,
                receivePacket.offset,
                receivePacket.length
            )

            if (response != null && response.isSuccessResponse()) {
                val mappedAddress = response.getMappedAddress()
                val responseOrigin = response.getResponseOrigin()
                StunResult(
                    publicAddress = mappedAddress,
                    responseOrigin = responseOrigin,
                    localAddress = localEndpoint,
                    isSuccess = true
                )
            } else {
                StunResult(
                    localAddress = localEndpoint,
                    isSuccess = false,
                    errorMessage = "无效响应"
                )
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            StunResult(isSuccess = false, errorMessage = "超时")
        } catch (e: Exception) {
            StunResult(isSuccess = false, errorMessage = e.message)
        }
    }

    override fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
    }
}
