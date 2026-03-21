package pl.misek.toafmobile.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Coding(
    val checksum: Int = 0,
    val coding: String = "",
    @SerialName("module_vin") val moduleVin: String = "",
    )
@Serializable
data class CodingResponse(
    val code : Int,
    val message : List<Coding>
)