package pl.misek.toafmobile.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
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