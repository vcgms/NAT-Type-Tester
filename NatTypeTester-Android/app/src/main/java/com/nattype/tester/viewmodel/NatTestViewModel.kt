package com.nattype.tester.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nattype.tester.nat.NatChecker
import com.nattype.tester.network.NetworkUtils
import com.nattype.tester.network.StunServer
import com.nattype.tester.stun.*
import com.nattype.tester.stun.client.StunClientTCP
import com.nattype.tester.stun.client.StunClientUDP
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress

/**
 * NAT 测试状态
 */
data class NatTestState(
    val isTesting: Boolean = false,
    val progress: Float = 0f,
    val currentStep: String = "",
    val result: StunResult? = null,
    val localIPv4: String? = null,
    val localIPv6: String? = null,
    val publicIPv4: String? = null,
    val publicIPv6: String? = null,
    val selectedServer: StunServer = StunServer.DEFAULT_SERVERS.first(),
    val selectedProtocol: TransportProtocol = TransportProtocol.UDP,
    val errorMessage: String? = null,
    val logs: List<String> = emptyList()
)

enum class TransportProtocol(val label: String) {
    UDP("UDP"),
    TCP("TCP")
}

class NatTestViewModel : ViewModel() {

    private val _state = MutableStateFlow(NatTestState())
    val state: StateFlow<NatTestState> = _state.asStateFlow()

    private var testJob: Job? = null
    private val natChecker = NatChecker(timeoutMs = 3000)
    private val udpClient = StunClientUDP()
    private val tcpClient = StunClientTCP()

    init {
        loadLocalNetworkInfo()
    }

    private fun loadLocalNetworkInfo() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                localIPv4 = NetworkUtils.getLocalIPv4(),
                localIPv6 = NetworkUtils.getLocalIPv6()
            )
            val publicIPv4 = NetworkUtils.getPublicIP()
            val publicIPv6 = NetworkUtils.getPublicIPv6()
            _state.value = _state.value.copy(publicIPv4 = publicIPv4, publicIPv6 = publicIPv6)
        }
    }

    fun selectServer(server: StunServer) {
        _state.value = _state.value.copy(selectedServer = server, result = null, errorMessage = null)
    }

    fun selectProtocol(protocol: TransportProtocol) {
        _state.value = _state.value.copy(selectedProtocol = protocol, result = null, errorMessage = null)
    }

    fun setCustomServer(hostPort: String) {
        val server = StunServer.parse(hostPort) ?: return
        _state.value = _state.value.copy(selectedServer = server, result = null, errorMessage = null)
    }

    fun startTest() {
        if (_state.value.isTesting) return

        val server = _state.value.selectedServer
        val protocol = _state.value.selectedProtocol

        testJob?.cancel()
        testJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                isTesting = true,
                progress = 0f,
                currentStep = "解析服务器地址...",
                result = null,
                errorMessage = null,
                logs = emptyList()
            )

            val address = server.getAddress()
            if (address == null) {
                _state.value = _state.value.copy(
                    isTesting = false,
                    errorMessage = "无法解析服务器地址: ${server.host}"
                )
                return@launch
            }

            updateProgress(0.1f, "正在连接 STUN 服务器...")

            val result = when (protocol) {
                TransportProtocol.UDP -> {
                    updateProgress(0.2f, "执行 UDP NAT 类型检测...")
                    natChecker.checkNatType(address)
                }
                TransportProtocol.TCP -> {
                    updateProgress(0.2f, "执行 TCP 绑定请求...")
                    tcpClient.query(address)
                }
            }

            updateProgress(0.9f, "分析检测结果...")

            // 加载公网 IP（仅当检测结果中的公网地址为 IPv4 时才覆盖，避免用 IPv6 覆盖）
            val publicIPv4 = if (result.isSuccess) {
                val mappedHost = result.publicAddress?.hostString
                if (mappedHost != null && !mappedHost.contains(":")) mappedHost else null
            } else {
                NetworkUtils.getPublicIP()
            }

            _state.value = _state.value.copy(
                isTesting = false,
                progress = 1f,
                currentStep = "检测完成",
                result = result,
                publicIPv4 = publicIPv4 ?: _state.value.publicIPv4,
                logs = result.logs,
                errorMessage = if (result.isSuccess) null else result.errorMessage
            )
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        _state.value = _state.value.copy(
            isTesting = false,
            currentStep = "已取消"
        )
    }

    fun reset() {
        testJob?.cancel()
        _state.value = NatTestState(
            localIPv4 = _state.value.localIPv4,
            localIPv6 = _state.value.localIPv6,
            publicIPv4 = _state.value.publicIPv4,
            publicIPv6 = _state.value.publicIPv6,
            selectedServer = _state.value.selectedServer,
            selectedProtocol = _state.value.selectedProtocol
        )
    }

    private fun updateProgress(progress: Float, step: String) {
        _state.value = _state.value.copy(progress = progress, currentStep = step)
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
        udpClient.close()
        tcpClient.close()
    }
}
