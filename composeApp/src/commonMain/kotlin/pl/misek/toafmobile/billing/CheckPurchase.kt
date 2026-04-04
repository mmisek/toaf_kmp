package pl.misek.toafmobile.billing

import kotlinx.serialization.Serializable

@Serializable
data class CheckPurchaseResponse (
    val code : Int = 0,
    val data : String? = null
)