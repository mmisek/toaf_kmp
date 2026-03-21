package pl.misek.toafmobile.network

import kotlinx.serialization.Serializable

@Serializable
data class ToafWifiUpdateFiles(
    val Id: Int,
    val FileName: String,
    val FileData : String
    )
