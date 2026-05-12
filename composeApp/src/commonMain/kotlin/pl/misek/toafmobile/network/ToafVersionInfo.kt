package pl.misek.toafmobile.network

import kotlinx.serialization.Serializable

@Serializable
data class ToafVersionInfo(
    val versionName : String,
    val versionCode : Int,
    val platform : String
)

@Serializable
data class ToafVersionInfoResponse(
    val code : Int,
    val message : ToafVersionInfo
)