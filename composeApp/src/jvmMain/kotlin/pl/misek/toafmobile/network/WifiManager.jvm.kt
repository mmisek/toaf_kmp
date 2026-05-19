package pl.misek.toafmobile.network

import co.touchlab.kermit.Logger
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    override fun getLocalIpAddress() : List<String>?
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
                .toList()
        }
        catch ( ex : Exception)
        {
            ex.printStackTrace()
            null
        }
    }

    override suspend fun scanNetwork(): List<String> = withContext(Dispatchers.IO) {
        val myIps = getLocalIpAddress() ?: return@withContext emptyList()
        val results = myIps.flatMap { myIp ->
            val subnet = myIp.substringBeforeLast(".") + "."
            (1..254).map { i ->
                async {
                    val host = subnet + i
                    if (host == myIp) return@async null
                    try {
                        val inetAddress = InetAddress.getByName(host)
                        if (inetAddress.isReachable(200)) {
                            host
                        }
                        else
                            null
                        } catch (_: Exception) {
                        null
                        }
                    }
                }
            }

        results.awaitAll().filterNotNull()
    }
}