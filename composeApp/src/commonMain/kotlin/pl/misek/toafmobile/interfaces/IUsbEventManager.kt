package pl.misek.toafmobile.interfaces

import kotlinx.coroutines.flow.SharedFlow

interface IUsbEventManager {

    val usbState : SharedFlow<UsbEvent>
    fun handeConnectedDevice(device: UsbDeviceInfo, driver: Any?)
    fun getAvailableDevices() : MutableList<String>
    fun getDeviceInfo(deviceName :String) : UsbDeviceInfo?
}