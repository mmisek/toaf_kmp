package pl.misek.toafmobile.network

import android.content.Context
import android.net.wifi.WifiManager
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.activities.ToafConstants
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

class  AndroidWifiManager(private val context: Context) : IWifiManager
{
    private val tag = "WifiManager"
    override fun isHotspotEnable() : Boolean
    {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return try {
            val method = wifiManager.javaClass.getDeclaredMethod("getWifiApState")
            method.isAccessible = true
            val state = method.invoke(wifiManager) as Int
            // 10 = WIFI_AP_STATE_DISABLING
            // 11 = WIFI_AP_STATE_DISABLED
            // 12 = WIFI_AP_STATE_ENABLING
            // 13 = WIFI_AP_STATE_ENABLED
            // 14 = WIFI_AP_STATE_FAILED
            state == 13
        } catch (ex : Exception) {
            ex.printStackTrace()
            false
        }
    }

    override fun getLocalIpAddress() : List<String>?
    {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            Collections.list(interfaces)
                .asSequence()
                .filter { it.isUp && !it.isLoopback}
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
            Logger.withTag(tag).i {"Local Ip : $myIp"}
            val subnet = myIp.substringBeforeLast(".") + "."
            (1..254).map { i ->
                async {
                    val host = subnet + i
                    if (host == myIp) return@async null
                    // InetAddress.isReachable() is unreliable on Android: unprivileged
                    // apps can't send raw ICMP pings, and its TCP-port-7 fallback almost
                    // never has anything listening. Probe the actual port our device
                    // listens on instead, with a timeout generous enough to cover a cold
                    // ARP cache - the reason the very first scan of a device the phone
                    // hasn't talked to yet used to come back empty, requiring a rescan.
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(host, ToafConstants.TOAF_WIFI_PORT), 400)
                        }
                        host
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }

        results.awaitAll().filterNotNull()
    }
}