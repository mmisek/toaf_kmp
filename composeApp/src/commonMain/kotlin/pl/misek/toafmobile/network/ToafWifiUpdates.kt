package pl.misek.toafmobile.network

import kotlinx.serialization.Serializable

@Serializable
data class ToafWifiUpdates(
    val toafWifiSoftwareVersionId: Int,
    val toafWifiSoftwareVersion: Int
    )
