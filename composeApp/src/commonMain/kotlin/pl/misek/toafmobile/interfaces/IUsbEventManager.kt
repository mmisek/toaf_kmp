package pl.misek.toafmobile.interfaces

import kotlinx.coroutines.flow.StateFlow

interface IUsbEventManager {

    val usbState : StateFlow<UsbEvent>
    fun handeConnectedDevice(device: UsbDeviceInfo, driver: Any?)
}