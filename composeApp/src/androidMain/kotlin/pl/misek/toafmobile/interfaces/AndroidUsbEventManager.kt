package pl.misek.toafmobile.interfaces

import android.hardware.usb.UsbManager
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.protocols.ProtocolFactory
import pl.misek.toafmobile.serial.AndroidSerialDevice

private const val tag = "UsbEventManager"
class AndroidUsbEventManager(private val usbBroadcastHandler: IUsbBroadcastHandler, private val usbManager: UsbManager,  val database : Database, private val protocolFactory: ProtocolFactory) :
    DefaultLifecycleObserver, IUsbEventManager
    {
    private val _usbState = MutableSharedFlow<UsbEvent>(replay = 1, extraBufferCapacity =  10)
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
                    is UsbEvent.Attached -> { onUsbAttached(event.device, event.driver as? UsbSerialDriver)}
                    is UsbEvent.Detached ->{ onUsbDeattached(event.device)}
                    is UsbEvent.PermissionGranted -> {}
                    is UsbEvent.PermissionDenied -> {}
                    is UsbEvent.Error ->{}
                }
            }
        }
    }

    private fun onUsbAttached(device : UsbDeviceInfo, driver: UsbSerialDriver?)
    {
        if (driver == null) return
        Log.d(tag, "$device was attached")
        CoroutineScope(Dispatchers.IO).launch()
        {
            val serialDevice = AndroidSerialDevice(usbManager = usbManager, driver)
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
        Log.d(tag, "$device was deattached")
    }

    override fun handeConnectedDevice(device: UsbDeviceInfo, driver: Any?)
    {
        if (driver !is UsbSerialDriver) return
        _usbState.tryEmit(UsbEvent.Attached(device = device, driver = driver))
        onUsbAttached(device, driver = driver)
    }

    override fun getAvailableDevices(): MutableList<String> = usbBroadcastHandler.getAvailableDevices()

    override fun getDeviceInfo(deviceName: String): UsbDeviceInfo {
        TODO("Not yet implemented")
    }
}