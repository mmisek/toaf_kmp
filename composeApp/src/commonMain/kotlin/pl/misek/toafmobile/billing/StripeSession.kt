package pl.misek.toafmobile.billing

import kotlinx.serialization.Serializable

@Serializable
data class StripeSessionResponse(
    val code : Int,
    val data : StripeSession
)

@Serializable
data class StripeSession (
    val url : String,
    val sessionId :String
)
