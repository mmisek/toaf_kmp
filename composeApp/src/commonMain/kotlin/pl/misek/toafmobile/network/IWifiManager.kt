package pl.misek.toafmobile.network

interface IWifiManager {
    fun isHotspotEnable(): Boolean
    fun getLocalIpAddress(): String?
    suspend fun scanNetwork(): List<String>
}