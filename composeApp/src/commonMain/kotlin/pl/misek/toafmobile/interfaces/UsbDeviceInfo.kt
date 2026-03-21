package pl.misek.toafmobile.interfaces

data class UsbDeviceInfo (
    var portName : String? = null,
    var name : String? = null,
    var serialNumber : String? = null,
    var manufacturer : String? = null,
    var vendorID : String? = null,
    var productId : String? = null,
    var description : String? = null,
    var version: String? = null
)
