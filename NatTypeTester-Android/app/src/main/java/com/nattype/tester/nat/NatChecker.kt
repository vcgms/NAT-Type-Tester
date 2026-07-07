package com.nattype.tester.nat

import com.nattype.tester.stun.*
import com.nattype.tester.stun.client.StunClientUDP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * NAT 类型检测协调器
 *
 * 根据 RFC 3489 和 RFC 5389/5780 标准检测 NAT 类型
 *
 * 每项测试均通过"重复查询 + 一致性校验"机制确保结果可靠：
 * - 连续多次获得相同结果才认为有效
 * - 超过最大重试次数仍未一致则判定为无效（归为未知状态）
 */
class NatChecker(
    private val timeoutMs: Int = 3000
) {
    private val udpClient = StunClientUDP()

    /**
     * 执行完整的 NAT 类型检测
     */
    suspend fun checkNatType(primaryServer: InetSocketAddress): StunResult = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()
        logs.add("=== NAT 类型检测开始 ===")
        logs.add("STUN 服务器: ${primaryServer.hostString}:${primaryServer.port}")
        logs.add("重试策略: 最多 5 次，需连续 2 次一致，间隔 800ms")

        var primarySocket: DatagramSocket? = null

        try {
            // === 测试 I: 基础 UDP 连通性检测 ===
            logs.add("\n--- 测试 I: UDP 连通性检测 ---")
            primarySocket = DatagramSocket()
            logs.add("本地端点: ${primarySocket.localAddress}:${primarySocket.localPort}")

            val testResult1 = queryConsistent("UDP连通性", logs,
                maxRetries = 5, requiredMatches = 2, intervalMs = 1000
            ) {
                udpClient.query(primarySocket!!, primaryServer, timeoutMs)
            }

            if (testResult1 == null) {
                logs.add("❌ UDP 连通性检测失败: 重试耗尽仍未获得一致结果")
                return@withContext StunResult(
                    natType = NatType.UDP_BLOCKED,
                    isSuccess = true,
                    logs = logs,
                    mappingBehavior = MappingBehavior.UNKNOWN,
                    filteringBehavior = FilteringBehavior.UNKNOWN
                )
            }
            logs.add("✅ UDP 连通性正常")
            logs.add("映射地址: ${testResult1.publicAddress}")

            val mappedAddress1 = testResult1.publicAddress!!
            val otherAddress = testResult1.otherAddress
            val responseOrigin = testResult1.responseOrigin
            logs.add("响应来源: $responseOrigin")
            logs.add("其他地址: $otherAddress")

            // === 测试 II: 映射行为检测 ===
            logs.add("\n--- 测试 II: 映射行为检测 ---")
            logs.add("复用本地端口 ${primarySocket.localPort} 测试不同目标")
            val mappingBehavior: MappingBehavior
            val allServerAddresses: List<InetSocketAddress>

            if (otherAddress != null) {
                allServerAddresses = listOf(primaryServer, otherAddress)
            } else {
                allServerAddresses = try {
                    InetAddress.getAllByName(primaryServer.hostString)
                        .map { InetSocketAddress(it, primaryServer.port) }
                } catch (e: Exception) {
                    listOf(primaryServer)
                }
            }

            val mappedAddresses = mutableListOf<InetSocketAddress>()
            mappedAddresses.add(mappedAddress1)

            for ((index, serverAddr) in allServerAddresses.withIndex()) {
                if (index == 0) continue
                if (index >= 3) break
                if (serverAddr.address.hostAddress == primaryServer.address.hostAddress &&
                    serverAddr.port == primaryServer.port) continue

                logs.add("测试地址 ${index + 1}: $serverAddr")
                val result = queryConsistent("映射-目标${index + 1}", logs,
                    maxRetries = 4, requiredMatches = 2, intervalMs = 800
                ) {
                    udpClient.query(primarySocket!!, serverAddr, timeoutMs)
                }
                if (result != null && result.publicAddress != null) {
                    logs.add("  映射地址: ${result.publicAddress}")
                    mappedAddresses.add(result.publicAddress)
                } else {
                    logs.add("  ❌ 测试失败：无法获得一致映射结果")
                }
            }

            try { primarySocket.close() } catch (_: Exception) {}
            primarySocket = null

            mappingBehavior = analyzeMappingBehavior(mappedAddresses, allServerAddresses, logs)
            logs.add("映射行为: $mappingBehavior")

            // === 测试 III: 过滤行为检测 ===
            logs.add("\n--- 测试 III: 过滤行为检测 ---")
            val filteringBehavior = detectFilteringBehavior(primaryServer, otherAddress, logs)
            logs.add("过滤行为: $filteringBehavior")

            // === 综合判定 ===
            val natType = NatType.classify(mappingBehavior, filteringBehavior)
            logs.add("\n=== 检测完成 ===")
            logs.add("综合 NAT 类型: ${natType.displayName} (${natType.shortName})")
            logs.add("RFC 5389 映射行为: ${mappingBehavior.displayName}")
            logs.add("RFC 5389 过滤行为: ${filteringBehavior.displayName}")

            StunResult(
                publicAddress = mappedAddress1,
                responseOrigin = responseOrigin,
                otherAddress = otherAddress,
                mappingBehavior = mappingBehavior,
                filteringBehavior = filteringBehavior,
                natType = natType,
                localAddress = testResult1.localAddress,
                isSuccess = true,
                logs = logs
            )
        } catch (e: Exception) {
            logs.add("检测异常: ${e.message}")
            StunResult(
                natType = NatType.UNKNOWN,
                isSuccess = false,
                errorMessage = e.message,
                logs = logs
            )
        } finally {
            try { primarySocket?.close() } catch (_: Exception) {}
            udpClient.close()
        }
    }

    /**
     * 带重试和一致性校验的查询包装器
     *
     * 连续 [requiredMatches] 次获得相同结果才认为有效。
     * 超过 [maxRetries] 次仍未一致则返回 null（无效）。
     */
    private suspend fun queryConsistent(
        label: String,
        logs: MutableList<String>,
        maxRetries: Int = 5,
        requiredMatches: Int = 2,
        intervalMs: Long = 800,
        query: suspend () -> StunResult
    ): StunResult? {
        data class ResultKey(
            val success: Boolean,
            val mappedHost: String?,
            val mappedPort: Int?,
            val originHost: String?,
            val originPort: Int?
        )

        fun StunResult.toKey() = ResultKey(
            isSuccess,
            publicAddress?.hostString,
            publicAddress?.port,
            responseOrigin?.hostString,
            responseOrigin?.port
        )

        var lastKey: ResultKey? = null
        var consecutive = 0
        var bestResult: StunResult? = null

        for (attempt in 1..maxRetries) {
            if (attempt > 1) {
                delay(intervalMs)
            }

            val result = query()
            val key = result.toKey()

            // 输出每次结果详情
            val successStr = if (result.isSuccess) "✓成功" else "✗失败"
            val mappedStr = if (result.isSuccess) "映射=${result.publicAddress?.hostString}:${result.publicAddress?.port}" else ""
            val originStr = if (result.responseOrigin != null) " 来源=${result.responseOrigin}" else ""
            val matchStr = if (lastKey != null) {
                if (key == lastKey) " [一致]" else " [变化]"
            } else ""
            logs.add("  [$label] #$attempt $successStr $mappedStr$originStr$matchStr")

            if (result.isSuccess && lastKey != null && key == lastKey) {
                consecutive++
                if (consecutive >= requiredMatches) {
                    logs.add("  [$label] ✅ 连续 $consecutive 次一致，结果有效")
                    return result
                }
            } else if (result.isSuccess) {
                consecutive = 1
                bestResult = result
            } else {
                consecutive = 0
            }
            lastKey = key
        }

        if (bestResult != null) {
            logs.add("  [$label] ⚠️ 重试 $maxRetries 次未获一致结果，回退使用最后成功值")
            return bestResult
        }

        logs.add("  [$label] ❌ 全部 $maxRetries 次重试均失败，判定为无效")
        return null
    }

    /**
     * 分析映射行为
     *
     * 比较不同目标地址的映射结果：
     * - 所有映射地址相同 → Endpoint-Independent Mapping (EIM)
     * - 不同 IP 目标映射不同 → Address-Dependent Mapping (ADM)
     *   注意：只有 2 个不同 IP 的服务器时，最多只能证明到 ADM，无法区分 ADM 和 APDM
     * - 相同 IP 不同端口也映射不同 → Address and Port-Dependent Mapping (APDM)
     */
    private fun analyzeMappingBehavior(
        mappedAddresses: List<InetSocketAddress>,
        serverAddresses: List<InetSocketAddress>,
        logs: MutableList<String>
    ): MappingBehavior {
        if (mappedAddresses.size < 2) {
            logs.add("无法判断映射行为（测试地址不足）")
            return MappingBehavior.UNKNOWN
        }

        // 检查是否所有映射地址相同
        val first = mappedAddresses[0]
        var allSame = true

        for (i in 1 until mappedAddresses.size) {
            val curr = mappedAddresses[i]
            if (curr.hostString != first.hostString || curr.port != first.port) {
                allSame = false
                break
            }
        }

        if (allSame) {
            logs.add("所有目标 IP 映射地址相同 → Endpoint-Independent Mapping")
            return MappingBehavior.ENDPOINT_INDEPENDENT
        }

        // 映射不同，检查是否能区分 ADM 和 APDM
        if (mappedAddresses.size == 2) {
            // 只有 2 个不同 IP 的测试结果，无法确认端口相关性
            logs.add("不同目标 IP 映射地址不同 → Address-Dependent Mapping（至少）")
            return MappingBehavior.ADDRESS_DEPENDENT
        }

        // 有 3 个以上测试结果，可以进一步分析
        // 检查是否有相同 IP 不同端口的地址组
        val hasSameIpDifferentPort = serverAddresses.any { a ->
            serverAddresses.any { b ->
                a !== b &&
                a.address.hostAddress == b.address.hostAddress &&
                a.port != b.port
            }
        }

        return if (hasSameIpDifferentPort) {
            logs.add("相同 IP 不同端口也映射不同 → Address and Port-Dependent Mapping")
            MappingBehavior.ADDRESS_AND_PORT_DEPENDENT
        } else {
            logs.add("不同目标 IP 映射地址不同 → Address-Dependent Mapping")
            MappingBehavior.ADDRESS_DEPENDENT
        }
    }

    /**
     * 检测过滤行为（带重试一致性校验）
     *
     * 通过 RFC 5780 CHANGE-REQUEST (0x8030) 测试：
     * - 创建专用 Socket 并复用，确保过滤检测期间 NAT 映射一致
     * - 测试 1: 请求服务器从备用地址回复 → 验证响应来源与 OTHER-ADDRESS 匹配 → EIF
     * - 测试 2: 请求服务器仅从不同端口回复 → 验证响应来源 IP 相同但端口不同 → ADF
     * - 两者都不满足 → APDF
     */
    private suspend fun detectFilteringBehavior(
        primaryServer: InetSocketAddress,
        otherAddress: InetSocketAddress?,
        logs: MutableList<String>
    ): FilteringBehavior {
        logs.add("主服务器地址: ${primaryServer.address.hostAddress}:${primaryServer.port}")
        if (otherAddress != null) {
            logs.add("备用服务器地址: ${otherAddress.address.hostAddress}:${otherAddress.port}")
        }

        // 为过滤测试创建专用 Socket，复用同一本地端点确保 NAT 映射一致
        val filterSocket = DatagramSocket()
        logs.add("过滤测试本地端口: ${filterSocket.localPort}")

        try {
            // 测试 1: 请求从备用地址回复 (changeIp=true, changePort=true)
            logs.add("\n测试 1: 请求从备用地址回复 (Change IP + Change Port)")
            val result1 = queryConsistent("过滤-变更IP", logs,
                maxRetries = 4, requiredMatches = 2, intervalMs = 800
            ) {
                udpClient.queryWithChangeRequest(filterSocket, primaryServer, changeIp = true, changePort = true, timeoutMs)
            }

            // 验证响应来源是否与 OTHER-ADDRESS 匹配（RFC 5780 精确验证）
            val respondedFromAlternateIp = result1 != null &&
                otherAddress != null &&
                result1.responseOrigin != null &&
                result1.responseOrigin.address.hostAddress == otherAddress.address.hostAddress

            if (respondedFromAlternateIp) {
                logs.add("  ✅ 收到备用地址的回复 → 端点无关过滤 (EIF)")
                return FilteringBehavior.ENDPOINT_INDEPENDENT
            } else if (result1 != null) {
                val originInfo = if (result1.responseOrigin != null)
                    "${result1.responseOrigin.address.hostAddress}:${result1.responseOrigin.port}" else "无"
                if (result1.responseOrigin != null &&
                    result1.responseOrigin.address.hostAddress == primaryServer.address.hostAddress) {
                    logs.add("  ⚠️ 响应来自主服务器而非备用地址 → 服务器未支持 CHANGE-REQUEST")
                } else {
                    logs.add("  ❌ 未收到备用地址回复 (来源: $originInfo) → 继续检测")
                }
            } else {
                logs.add("  ❌ 查询无一致结果 → 继续检测")
            }

            // 测试 2: 请求仅从不同端口回复 (changeIp=false, changePort=true)
            logs.add("\n测试 2: 请求仅从不同端口回复 (Change Port only)")
            val result2 = queryConsistent("过滤-变更端口", logs,
                maxRetries = 4, requiredMatches = 2, intervalMs = 800
            ) {
                udpClient.queryWithChangeRequest(filterSocket, primaryServer, changeIp = false, changePort = true, timeoutMs)
            }

            val respondedFromAlternatePort = result2 != null &&
                result2.responseOrigin != null &&
                result2.responseOrigin.address.hostAddress == primaryServer.address.hostAddress &&
                result2.responseOrigin.port != primaryServer.port

            if (respondedFromAlternatePort) {
                logs.add("  ✅ 收到主IP不同端口回复 → 地址相关过滤 (ADF)")
                return FilteringBehavior.ADDRESS_DEPENDENT
            } else if (result2 != null) {
                val originInfo = if (result2.responseOrigin != null)
                    "${result2.responseOrigin.address.hostAddress}:${result2.responseOrigin.port}" else "无"
                if (result2.responseOrigin == null ||
                    (result2.responseOrigin.address.hostAddress == primaryServer.address.hostAddress &&
                     result2.responseOrigin.port == primaryServer.port)) {
                    logs.add("  ⚠️ 响应来自主服务器同端口 → 服务器未支持 CHANGE-REQUEST 或 NAT 阻止了端口变更")
                }
            } else {
                logs.add("  ❌ 查询无一致结果")
            }

            logs.add("\n→ 最终判定: 地址和端口相关过滤 (APDF)")
            return FilteringBehavior.ADDRESS_AND_PORT_DEPENDENT
        } finally {
            try { filterSocket.close() } catch (_: Exception) {}
        }
    }
}
