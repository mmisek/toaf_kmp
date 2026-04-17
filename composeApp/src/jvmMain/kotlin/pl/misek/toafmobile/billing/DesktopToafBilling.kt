package pl.misek.toafmobile.billing

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import pl.misek.toafmobile.network.ToafApi

class DesktopToafBilling : IToafBilling, KoinComponent {
    private val toafApi : ToafApi by inject()
    private var lastProductInfo : StripeProductInfo? = null

    override suspend fun getElementPrice(elementName: String): String? {
        var result : String? = null
        lastProductInfo = toafApi.getStripeProductInfo(elementName)
        if (lastProductInfo!=null)
            result = "${lastProductInfo?.amount} ${lastProductInfo?.currency}"
        return result
    }

    override suspend fun getStripeSession(elementName: String): StripeSession? {
        var stripeSession : StripeSession? = null
        if (lastProductInfo != null && lastProductInfo?.productName == elementName.uppercase())
        {
            stripeSession = toafApi.createStripeCheckout(
                priceId = lastProductInfo?.priceId ?: "",
                productName = elementName
            )
        }
        return stripeSession
    }

    override fun buyElement(activity: Any?, productName: String) {
    }

}