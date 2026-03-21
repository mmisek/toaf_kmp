package pl.misek.toafmobile.modules.containers

class LWSAdjustmentData {
    @JvmField
    var Adjustment: Int = 0
    @JvmField
    var Id: Int = 0
    @JvmField
    var Vin: String? = null

    constructor(adjustment: Int, id: Int, vin: String?) {
        Adjustment = adjustment
        Id = id
        Vin = vin
    }
}
