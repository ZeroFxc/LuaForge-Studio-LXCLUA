package com.luaforge.studio.lxclua.mcp

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MCP 局域网广播服务
 *
 * 使用 Android NSD (Network Service Discovery) 将本地 MCP 服务器
 * 注册到局域网，使其他设备可以发现并连接。
 *
 * 广播的服务类型: _mcp._tcp
 * 其他设备可通过 NSD 发现此服务，获取 IP 和端口后连接。
 */
class MCPBroadcastService(
    private val context: Context,
    private val port: Int = MCPLocalServer.DEFAULT_PORT
) {
    companion object {
        private const val TAG = "MCPBroadcastService"
        /** NSD 服务类型 */
        const val SERVICE_TYPE = "_mcp._tcp"
        /** 默认服务名称 */
        const val SERVICE_NAME = "LXC-LUA-MCP"
    }

    private val nsdManager: NsdManager? by lazy {
        try {
            context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        } catch (e: Exception) {
            android.util.Log.e(TAG, "获取 NsdManager 失败: ${e.message}")
            null
        }
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private val isRegistered = AtomicBoolean(false)

    /** 是否已注册广播 */
    val registered: Boolean get() = isRegistered.get()

    /**
     * 注册 NSD 服务广播
     * @param serviceName 服务名称（局域网中显示的名称）
     */
    fun register(serviceName: String = SERVICE_NAME) {
        val nsd = nsdManager ?: run {
            android.util.Log.e(TAG, "NsdManager 不可用，无法注册广播")
            return
        }

        if (isRegistered.get()) {
            android.util.Log.w(TAG, "广播已注册，先取消再注册")
            unregister()
        }

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            // Android API 34+ 需要使用 setPort() 方法
            setPort(port)
            // 设置属性，方便客户端识别
            setAttribute("server", MCPLocalServer.SERVER_NAME)
            setAttribute("version", MCPLocalServer.SERVER_VERSION)
            setAttribute("protocol", "2025-11-25")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                android.util.Log.i(TAG, "NSD 广播注册成功: ${serviceInfo.serviceName}")
                isRegistered.set(true)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                val errorMsg = when (errorCode) {
                    NsdManager.FAILURE_ALREADY_ACTIVE -> "服务已激活"
                    NsdManager.FAILURE_INTERNAL_ERROR -> "内部错误"
                    NsdManager.FAILURE_MAX_LIMIT -> "超出最大限制"
                    else -> "未知错误 (code=$errorCode)"
                }
                android.util.Log.e(TAG, "NSD 广播注册失败: $errorMsg")
                isRegistered.set(false)
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                android.util.Log.e(TAG, "NSD 广播注销失败: code=$errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                android.util.Log.i(TAG, "NSD 广播已注销: ${serviceInfo.serviceName}")
                isRegistered.set(false)
            }
        }

        try {
            nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener!!)
            android.util.Log.i(TAG, "正在注册 NSD 广播: $serviceName, 端口: $port")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "注册 NSD 广播异常: ${e.message}", e)
            isRegistered.set(false)
        }
    }

    /** 注销 NSD 服务广播 */
    fun unregister() {
        registrationListener?.let { listener ->
            try {
                nsdManager?.unregisterService(listener)
                android.util.Log.i(TAG, "正在注销 NSD 广播")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "注销 NSD 广播异常: ${e.message}", e)
            }
        }
        registrationListener = null
        isRegistered.set(false)
    }

    /**
     * 获取当前设备的局域网 IP 地址
     * @return 局域网 IP 地址字符串，如 "192.168.1.100"，获取失败返回 null
     */
    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                // 跳过回环和未启用的接口
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    // 只返回 IPv4 地址，跳过 IPv6
                    if (address is java.net.Inet4Address && !address.isLoopbackAddress) {
                        val ip = address.hostAddress
                        if (ip != null && (ip.startsWith("192.") || ip.startsWith("10.") || ip.startsWith("172."))) {
                            return ip
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e(TAG, "获取本地 IP 失败: ${e.message}")
            null
        }
    }

    /**
     * 获取 MCP 服务连接信息（供 UI 展示）
     * @return 包含 ip, port, url 的 map
     */
    fun getConnectionInfo(): Map<String, String> {
        val ip = getLocalIpAddress() ?: "未知"
        return mapOf(
            "ip" to ip,
            "port" to port.toString(),
            "url" to "http://$ip:$port/mcp",
            "serviceType" to SERVICE_TYPE,
            "serviceName" to SERVICE_NAME
        )
    }
}