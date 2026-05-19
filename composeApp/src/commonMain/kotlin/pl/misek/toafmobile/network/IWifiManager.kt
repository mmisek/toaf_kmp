package pl.misek.toafmobile.network

interface IWifiManager {
    fun isHotspotEnable(): Boolean
    fun getLocalIpAddress(): List<String>?
    suspend fun scanNetwork(): List<String>
}