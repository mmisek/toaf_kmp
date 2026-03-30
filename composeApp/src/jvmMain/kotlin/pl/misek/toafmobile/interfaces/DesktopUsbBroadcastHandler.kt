package pl.misek.toafmobile.interfaces

import co.touchlab.kermit.Logger
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val tag = "DesktopUsbBroadcastHandler"
class DesktopUsbBroadcastHandler() : IUsbBroadcastHandler {

    override fun observeUsbEvent() : Flow<UsbEvent> = callbackFlow {
        fun sendCurrentDevice(){
            SerialPort.getCommPorts().forEach {
                trySend(UsbEvent.Attached(it?.getUsbDeviceInfo() ?: UsbDeviceInfo(), null))
            }
        }
        sendCurrentDevice()
        val job = kotlinx.coroutines.MainScope().launch {
            var lastDevices = SerialPort.getCommPorts().map { it.systemPortName }.toSet()
            while (isActive) {
                kotlinx.coroutines.delay(2000)
                val currentPorts = SerialPort.getCommPorts()
                val currentNames = currentPorts.map { it.systemPortName }.toSet()

                currentPorts.filter { it.systemPortName !in lastDevices }.forEach {
                    trySend(UsbEvent.Attached(it.getUsbDeviceInfo(), null))
                }

                val disconnected = lastDevices.filter { it !in currentNames }
                disconnected.forEach { name ->
                    trySend(UsbEvent.Detached(UsbDeviceInfo()))
                }

                lastDevices = currentNames
            }
        }

        awaitClose {
            job.cancel()
        }
    }
    private fun SerialPort.getUsbDeviceInfo() : UsbDeviceInfo{
        return UsbDeviceInfo(
            serialNumber = this.serialNumber,
            name = this.portDescription,
            productId = this.productID.toString(),
            vendorID = this.vendorID.toString(),
            manufacturer = this.manufacturer,
        )
    }

    override fun getAvailableDevices(): MutableList<String> {
        val result = mutableListOf<String>()
        SerialPort.getCommPorts().forEach{
            Logger.i {"Serial Port : $it"}
            result.add(it.systemPortName) }
        return result
    }
}