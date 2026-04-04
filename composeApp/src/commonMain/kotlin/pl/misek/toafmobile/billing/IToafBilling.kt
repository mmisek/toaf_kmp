package pl.misek.toafmobile.billing

import androidx.compose.runtime.Composable

interface IToafBilling {
    suspend fun getElementPrice(elementName: String): String?
    fun buyElement(activity: Any?, productName: String)
    suspend fun getStripeSession(elementName: String) : StripeSession?
}