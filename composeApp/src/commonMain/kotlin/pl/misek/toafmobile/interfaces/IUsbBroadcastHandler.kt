package pl.misek.toafmobile.interfaces

import kotlinx.coroutines.flow.Flow

interface IUsbBroadcastHandler{
    fun observeUsbEvent() : Flow<UsbEvent>
    fun getAvailableDevices() : MutableList<String>
}

