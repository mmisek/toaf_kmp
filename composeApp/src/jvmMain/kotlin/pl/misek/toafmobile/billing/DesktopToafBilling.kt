package pl.misek.toafmobile.billing

class DesktopToafBilling : IToafBilling {
    override fun getElementPrice(elementName: String): String? {
        return null
    }

    override fun buyElement(activity: Any?, productName: String) {
        TODO("Not yet implemented")
    }

}