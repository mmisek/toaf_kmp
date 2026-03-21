package pl.misek.toafmobile.interfaces

sealed interface UsbEvent {
    data object Idle : UsbEvent
    data class Attached(val device : UsbDeviceInfo, val driver : Any?) : UsbEvent
    data class Detached(val device : UsbDeviceInfo) : UsbEvent
    data class PermissionGranted(val device : UsbDeviceInfo) : UsbEvent
    data class PermissionDenied(val devic : UsbDeviceInfo?) : UsbEvent
    data class Error(val error : String): UsbEvent
}