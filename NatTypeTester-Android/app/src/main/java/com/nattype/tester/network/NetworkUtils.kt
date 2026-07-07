package com.nattype.tester.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Locale

/**
 * 网络工具类
 */
object NetworkUtils {

    /**
     * 获取本机所有 IP 地址
     */
    fun getLocalAddresses(): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress) {
                        result.add(address)
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * 获取首选本地 IPv4 地址
     */
    fun getLocalIPv4(): String? {
        return getLocalAddresses()
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    /**
     * 获取首选本地 IPv6 地址
     */
    fun getLocalIPv6(): String? {
        return getLocalAddresses()
            .filter { it is Inet6Address && !it.isLoopbackAddress && !it.isLinkLocalAddress && !it.isSiteLocalAddress }
            .firstOrNull()
            ?.hostAddress
            ?.replace(Regex("%.*"), "")
    }

    /**
     * 通过 HTTP 方式获取公网 IP
     */
    suspend fun getPublicIP(): String? = withContext(Dispatchers.IO) {
        val services = listOf(
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://checkip.amazonaws.com"
        )
        for (url in services) {
            try {
                val response = fetchUrl(url)
                if (isValidIPv4(response)) {
                    return@withContext response
                }
            } catch (_: Exception) {}
        }
        return@withContext null
    }

    /**
     * 通过 HTTP 方式获取公网 IPv6
     */
    suspend fun getPublicIPv6(): String? = withContext(Dispatchers.IO) {
        val services = listOf(
            "https://api64.ipify.org",
            "https://ipv6.icanhazip.com",
            "https://api6.ipify.org"
        )
        for (url in services) {
            try {
                val response = fetchUrl(url)
                if (isValidIPv6(response)) {
                    return@withContext response
                }
            } catch (_: Exception) {}
        }
        return@withContext null
    }

    private fun fetchUrl(url: String): String {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.requestMethod = "GET"
        val response = connection.inputStream.bufferedReader().readText().trim()
        connection.disconnect()
        return response
    }

    private fun isValidIPv4(s: String): Boolean =
        s.isNotEmpty() && s.length < 16 && !s.contains(":") && s.matches(Regex("^[0-9.]+$"))

    private fun isValidIPv6(s: String): Boolean =
        s.isNotEmpty() && s.length < 45 && s.contains(":") && s.matches(Regex("^[0-9a-fA-F:]+$"))
}
