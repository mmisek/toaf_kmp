package pl.misek.toafmobile.interfaces

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.interfaces.serial.DesktopSerialDevice
import pl.misek.toafmobile.protocols.ProtocolFactory

private const val tag = "DesktopUsbEventManager"
class DesktopUsbEventManager(private val usbBroadcastHandler: IUsbBroadcastHandler, val database : Database, private val protocolFactory: ProtocolFactory) : IUsbEventManager
{
    private val _usbState = MutableSharedFlow<UsbEvent>(replay = 1, extraBufferCapacity = 10)
    override val usbState = _usbState.asSharedFlow()

    init{
        startObserveUsbEvent()
    }

    private fun startObserveUsbEvent(){
        CoroutineScope(Dispatchers.Main).launch {
            usbBroadcastHandler.observeUsbEvent().collect { event ->
                _usbState.tryEmit(event)
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
                _usbState.tryEmit(UsbEvent.Error("Database update Error"))
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
        _usbState.tryEmit(UsbEvent.Attached(device = device, null))
        onUsbAttached(device)
    }

    override fun getAvailableDevices(): MutableList<String> = usbBroadcastHandler.getAvailableDevices()
    override fun getDeviceInfo(deviceName: String) = usbBroadcastHandler.getDeviceInfo(deviceName)
}