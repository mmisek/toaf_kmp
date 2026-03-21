package pl.misek.toafmobile.network

import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections


class DesktopWifiManager : IWifiManager{
    override fun isHotspotEnable(): Boolean {
        return false
    }

    override fun getLocalIpAddress() : String?
    {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            Collections.list(interfaces)
                .asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { Collections.list(it.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
                .map { it.hostAddress }
                .firstOrNull()
        }
        catch ( ex : Exception)
        {
            ex.printStackTrace()
            null
        }
    }

    override suspend fun scanNetwork(): List<String> = withContext(Dispatchers.IO) {
        val connectedIps = mutableListOf<String>()
        val myIp = getLocalIpAddress() ?: return@withContext emptyList()
        val subnet = myIp.substringBeforeLast(".") + "."

        val jobs = (1..254).map { i ->
            launch {
                val host = subnet + i
                println("scan : $host")
                if (host != myIp) {
                    try {
                        val inetAddress = InetAddress.getByName(host)
                        if (inetAddress.isReachable(200)) {
                            synchronized(connectedIps) {
                                connectedIps.add(host)
                            }
                        }
                    } catch (ex: Exception) {
                        Sentry.captureException(ex)
                    }
                }
            }
        }

        jobs.joinAll()
        connectedIps.toList()
    }
}