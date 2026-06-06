package pl.misek.toafmobile.interfaces

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import androidx.core.content.IntentCompat
import co.touchlab.kermit.Logger
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import pl.misek.toafmobile.activities.ToafConstants
import kotlin.apply

private const val tag = "UsbBroadcastHandler"
class AndroidUsbBroadcastHandler(private var context : Context) : IUsbBroadcastHandler {
    private val  actionUsbPermission = ToafConstants.ACTION_USB_PERMISSION

    override fun observeUsbEvent() : Flow<UsbEvent> = callbackFlow {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val usbReceiver = object : BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                Logger.i {"$tag : onReceive : ${intent?.action}"}
                when(intent?.action)
                {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        val device = IntentCompat.getParcelableExtra<UsbDevice>(intent,UsbManager.EXTRA_DEVICE,UsbDevice::class.java)
                        device?.let {
                            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                            if (driver != null)
                                trySend(UsbEvent.Attached(device = device.getUsbDeviceInfo(), driver = driver))
                            else
                                trySend(UsbEvent.Error("Not Use SerialDevice"))
                        }
                    }

                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device = IntentCompat.getParcelableExtra<UsbDevice>(intent,UsbManager.EXTRA_DEVICE,UsbDevice::class.java)
                        device?.let {
                            try {
                                trySend(UsbEvent.Detached(device.getUsbDeviceInfo()))
                            } catch ( ex : Exception)
                            {
                                Logger.w { "$tag : UsbManager.ACTION_USB_DEVICE_DETACHED Error , ${ex.message}" }
                            }
                        }
                    }
                    actionUsbPermission ->{
                        synchronized(this){
                            val device = IntentCompat.getParcelableExtra<UsbDevice>(intent,UsbManager.EXTRA_DEVICE,UsbDevice::class.java)
                            val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            if (permissionGranted && device != null)
                                trySend(UsbEvent.PermissionGranted(device.getUsbDeviceInfo()))
                            else
                                trySend(UsbEvent.PermissionDenied(device?.getUsbDeviceInfo()))
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(actionUsbPermission).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val listenToBroadcastFromOtherApps = ContextCompat.RECEIVER_EXPORTED

        registerReceiver(context,usbReceiver,filter,listenToBroadcastFromOtherApps)
        usbManager.deviceList.values.forEach { device ->
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            trySend(UsbEvent.Attached(device.getUsbDeviceInfo(),driver))
            if (usbManager.hasPermission(device))
                trySend(UsbEvent.PermissionGranted(device.getUsbDeviceInfo()))
        }
        awaitClose {
            context.unregisterReceiver(usbReceiver)
        }
    }

    fun UsbDevice.getUsbDeviceInfo() : UsbDeviceInfo
    {
        val serial = try {
            this.serialNumber
        } catch (e: Exception)
        {
            Logger.w { "$tag : No permission to read serial number for ${this.deviceName}"}
            ""
        }

        val manufacturerName = try {
            this.manufacturerName
        } catch (e: Exception)
        {
            Logger.w { "$tag : No permission to read manufacturerName for ${this.deviceName}"}
            ""
        }

        val version = try {
            this.manufacturerName
        } catch (e: Exception)
        {
            Logger.w { "$tag : No permission to read verison for ${this.deviceName}"}
            ""
        }
        return UsbDeviceInfo(
            serialNumber = serial,
            name = this.deviceName,
            productId = this.productId.toString(),
            vendorID = this.vendorId.toString(),
            manufacturer = manufacturerName,
            version = version
        )
    }

    override fun getAvailableDevices() = mutableListOf<String>()
    override fun getDeviceInfo(deviceName: String): UsbDeviceInfo?  = null
}