package pl.misek.toafmobile.serial

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.delay
import pl.misek.toafmobile.interfaces.ISerialDevice
import java.io.ByteArrayOutputStream
import kotlin.time.measureTime

class AndroidSerialDevice (private val usbManager: UsbManager, private val _usbDriver: UsbSerialDriver?) : ISerialDevice
{
    private var tag = "SerialDevice"
    private var fastInitSleep = 25
    private var _connection: UsbDeviceConnection? = null
    private var _serialPort: UsbSerialPort? = null

    override suspend fun write(data: ByteArray?, forceAnswer: Boolean, useDelay: Boolean): ByteArray? {
        return write(data, 250, true, useDelay)
    }

    override suspend fun write(
        data: ByteArray?,
        timeout: Int,
        forceAnswer: Boolean,
        useDelay: Boolean
    ): ByteArray? {
        if (_serialPort == null) connect("")
        if (_serialPort != null && _serialPort!!.isOpen) {
            try {
                if (useDelay && data != null) {
                    for (b in data) {
                        _serialPort?.write(byteArrayOf(b), 10)
                        try {
                            delay(4)
                        } catch (ex: Exception) {
                             println("$tag : ${ex.toString()}")
                        }
                    }
                } else
                    _serialPort?.write(data, timeout)
            } catch (ex: Exception) {
               println("$tag : ${ex.toString()}")
                return null
            }
            delay(25)
            if (forceAnswer) {
                val outputStream = ByteArrayOutputStream()
                var result: ByteArray?
                val buffer = ByteArray(512)
                var n : Int
                try {
                    val time = measureTime {
                        do {
                            n = _serialPort?.read(buffer, 25) ?: 0
                            if (n > 0)
                                outputStream.write(buffer, 0, n)
                            delay(5)
                        } while (n > 0)
                        result = outputStream.toByteArray()
                    }
                    println("$tag : Reading Time : $time")
                } catch (ex: Exception) {
                   println("$tag : ${ex.toString()}")
                    result = null
                }
                return result
            } else {
                try {
                    _serialPort?.purgeHwBuffers(true, true)
                } catch (ex: Exception) {
                   println("$tag : ${ex.toString()}")
                }
            }

            return null
        }else
            return null
    }

    override fun disconnect() {
        if (_serialPort != null && _serialPort!!.isOpen) {
            try {
                _serialPort?.close()
            } catch (ex: Exception) {
               println("$tag : ${ex.toString()}")
            }
        }
    }

    override fun connect(deviceName : String) {
        if (_connection == null)
            _connection = usbManager.openDevice(_usbDriver?.device)
        if (_connection != null) {
            if (_usbDriver?.ports?.isNotEmpty() == true)
                _serialPort = _usbDriver.ports[0]
            if (_serialPort != null && !_serialPort?.isOpen!!) {
                try {
                    _serialPort?.open(_connection)
                    _serialPort?.setParameters(
                        9600,
                        8,
                        UsbSerialPort.STOPBITS_1,
                        UsbSerialPort.PARITY_EVEN
                    )
                } catch (ex: Exception) {
                   println("$tag : ${ex.toString()}")
                }
            }
        }
    }

    override val isConnected: Boolean
        get() = (_serialPort != null && _serialPort!!.isOpen)

    override fun setBaudrate(baudrate: Int) {
        try {
            if (_serialPort == null ) connect("")
            _serialPort?.setParameters(
                baudrate,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_EVEN
            )
        } catch (ex: Exception) {
           println("$tag : ${ex.toString()}")
        }
    }

    override fun initKWPStarDevice(): Boolean {
        var result = false
        if (_serialPort != null && _serialPort!!.isOpen) {
            try {
                _serialPort?.setParameters(
                    10400,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                result = true
            } catch (ex: Exception) {
               println("$tag : ${ex.toString()}")
            }
        }

        return result
    }

    override fun kwpFastInit(): Boolean {
        return kwpFastInit(fastInitSleep)
    }

    override fun kwpFastInit(initTime: Int): Boolean {
        var result = false
        try {
            _serialPort?.setBreak(true)
            Thread.sleep(initTime.toLong())
            _serialPort?.setBreak(false)
            Thread.sleep(initTime.toLong())
            result = true
        } catch (ex: Exception) {
           println("$tag : ${ex.toString()}")
        }

        return result
    }

    override fun setDS2Device() {
        if (_serialPort != null) {
            if (_serialPort!!.isOpen) {
                try {
                    _serialPort!!.setParameters(
                        9600,
                        8,
                        UsbSerialPort.STOPBITS_1,
                        UsbSerialPort.PARITY_EVEN
                    )
                } catch (ex: Exception) {
                   println("$tag : ${ex.toString()}")
                }
            }
        }
    }

    fun setFastInitSleep(newFastInitSleep: Int) {
        fastInitSleep = newFastInitSleep
    }
}
