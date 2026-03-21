package pl.misek.toafmobile.interfaces

interface IKWPInterfaceDevice {
    fun setBaudrate(baudrate: Int)
    fun initKWPStarDevice(): Boolean
    fun setDS2Device()
    fun kwpFastInit(): Boolean
    fun kwpFastInit(initTime: Int): Boolean
}
