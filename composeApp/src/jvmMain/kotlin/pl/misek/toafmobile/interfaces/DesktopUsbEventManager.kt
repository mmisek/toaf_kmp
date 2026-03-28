package pl.misek.toafmobile.interfaces

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.interfaces.serial.DesktopSerialDevice
import pl.misek.toafmobile.protocols.ProtocolFactory

private const val tag = "DesktopUsbEventManager"
class DesktopUsbEventManager(private val usbBroadcastHandler: IUsbBroadcastHandler, val database : Database, private val protocolFactory: ProtocolFactory) : IUsbEventManager
{
    private val _usbState = MutableStateFlow<UsbEvent>(UsbEvent.Idle)
    override val usbState = _usbState.asStateFlow()

    init{
        startObserveUsbEvent()
    }

    private fun startObserveUsbEvent(){
        CoroutineScope(Dispatchers.Main).launch {
            usbBroadcastHandler.observeUsbEvent().collect { event ->
                _usbState.value = event
                when(event) {
                    UsbEvent.Idle ->  {}
                    is UsbEvent.Attached -> { onUsbAttached(event.device)}
                    is UsbEvent.Detached ->{ onUsbDeattached(event.device)}
                    is UsbEvent.PermissionGranted -> {}
                    is UsbEvent.PermissionDenied -> {}
                    is UsbEvent.Error ->{}
                }
            }
        }
    }

    private fun onUsbAttached(device : UsbDeviceInfo)
    {
        Logger.i{"$tag, $device was attached"}
        CoroutineScope(Dispatchers.IO).launch()
        {
            val serialDevice = DesktopSerialDevice()
            protocolFactory.updateProtocolFactory(serialDevice)
            val result = database.updateStringSettingByName(DatabaseSettingFields.LAST_DEVICE_TYPE, "USB")
            if (!result) {
                _usbState.value = UsbEvent.Error("Database update Error")
            }
        }
    }

    private fun onUsbDeattached(device : UsbDeviceInfo)
    {
        protocolFactory.updateProtocolFactory(null)
        Logger.i {"$tag $device was deattached"}
    }

    override fun handeConnectedDevice(device: UsbDeviceInfo, driver: Any?)
    {
        _usbState.value = UsbEvent.Attached(device = device, null)
        onUsbAttached(device)
    }
}