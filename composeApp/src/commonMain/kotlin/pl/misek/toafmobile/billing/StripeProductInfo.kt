package pl.misek.toafmobile.billing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StripeProductInfoResponse (
    val code : Int = 0,
    val data : StripeProductInfo? = null
)
@Serializable
data class StripeProductInfo (
    val amount: Double = 0.0,
    val currency : String = "",
    val priceId : String = "",
    val productId : String = "",
    val productName :String = "",
    @SerialName("type") val typeOfProduct : String = ""
)
