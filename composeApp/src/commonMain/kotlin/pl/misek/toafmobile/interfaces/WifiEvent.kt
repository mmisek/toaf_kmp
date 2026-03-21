package pl.misek.toafmobile.interfaces

import pl.misek.toafmobile.interfaces.wifi.ToafWifiDeviceInfo

sealed interface WifiEvent {
    data object Idle : WifiEvent
    data class Selected(val wifiDevice : ToafWifiDeviceInfo) : WifiEvent
    data class Connected(val wifiDevice : ToafWifiDeviceInfo) : WifiEvent
    data class Disconnected(val wifiDevice: ToafWifiDeviceInfo) : WifiEvent
    data class Error(val wifiDevice: ToafWifiDeviceInfo): WifiEvent
}