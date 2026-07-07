package com.nattype.tester.stun.client

import com.nattype.tester.stun.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP STUN 客户端
 *
 * TCP STUN 消息格式: 消息前有 2 字节长度前缀 (Big-Endian)
 */
class StunClientTCP : StunClient {

    private var socket: Socket? = null

    override suspend fun query(
        serverAddress: InetSocketAddress,
        localAddress: InetSocketAddress?,
        timeoutMs: Int
    ): StunResult = withContext(Dispatchers.IO) {
        try {
            val sock = Socket()
            socket = sock
            sock.soTimeout = timeoutMs

            // 可选本地绑定
            if (localAddress != null) {
                sock.bind(localAddress)
            }

            sock.connect(serverAddress, timeoutMs)
            val localEndpoint = InetSocketAddress(sock.localAddress, sock.localPort)

            // 构建 STUN 请求
            val request = StunMessage.createBindingRequest()
            val requestBytes = StunMessage.encode(request)

            // TCP STUN: 消息前添加 2 字节长度前缀
            val output = ByteArrayOutputStream()
            output.write(requestBytes.size.ushr(8))
            output.write(requestBytes.size.and(0xFF))
            output.write(requestBytes)

            sock.getOutputStream().write(output.toByteArray())
            sock.getOutputStream().flush()

            // 读取响应长度
            val input = sock.getInputStream()
            val lenBuf = ByteArray(2)

            withTimeout(timeoutMs.toLong()) {
                var read = 0
                while (read < 2) {
                    val n = input.read(lenBuf, read, 2 - read)
                    if (n < 0) throw Exception("连接关闭")
                    read += n
                }
            }

            val responseLen = ((lenBuf[0].toInt() and 0xFF) shl 8) or (lenBuf[1].toInt() and 0xFF)

            if (responseLen <= 0 || responseLen > 65535) {
                return@withContext StunResult(
                    localAddress = localEndpoint,
                    isSuccess = false,
                    errorMessage = "无效的响应长度: $responseLen"
                )
            }

            val responseBuf = ByteArray(responseLen)
            withTimeout(timeoutMs.toLong()) {
                var read = 0
                while (read < responseLen) {
                    val n = input.read(responseBuf, read, responseLen - read)
                    if (n < 0) throw Exception("连接关闭")
                    read += n
                }
            }

            val response = StunMessage.decode(responseBuf, 0, responseLen)
            if (response != null && response.isSuccessResponse()) {
                StunResult(
                    publicAddress = response.getMappedAddress(),
                    localAddress = localEndpoint,
                    isSuccess = true,
                    logs = listOf(
                        "TCP 绑定请求成功",
                        "本地端点: $localEndpoint",
                        "映射地址: ${response.getMappedAddress() ?: "N/A"}"
                    )
                )
            } else {
                StunResult(
                    localAddress = localEndpoint,
                    isSuccess = false,
                    errorMessage = "无效的 STUN 响应"
                )
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            StunResult(isSuccess = false, errorMessage = "TCP STUN 超时")
        } catch (e: Exception) {
            StunResult(isSuccess = false, errorMessage = "TCP STUN 失败: ${e.message}")
        } finally {
            close()
        }
    }

    override fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
    }
}
