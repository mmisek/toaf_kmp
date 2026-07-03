package pl.misek.toafmobile.interfaces.wifi

import kotlinx.serialization.Serializable

@Serializable
data class ToafWifiUpdateInfo(
    var version : String,
    var files : List<String>
)

