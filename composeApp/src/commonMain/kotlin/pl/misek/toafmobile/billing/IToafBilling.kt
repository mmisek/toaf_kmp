package pl.misek.toafmobile.billing

import androidx.compose.runtime.Composable

interface IToafBilling {
    fun getElementPrice(elementName: String): String?
    fun buyElement(activity: Any?, productName: String)
}