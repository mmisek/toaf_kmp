package pl.misek.toafmobile.serial

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import co.touchlab.kermit.Logger
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.interfaces.ISerialDevice
import java.io.ByteArrayOutputStream
import kotlin.time.measureTime

class AndroidSerialDevice (private val usbManager: UsbManager) : ISerialDevice
{
    private var tag = "SerialDevice"
    private var fastInitSleep = 25
    private var _connection: UsbDeviceConnection? = null
    private var _serialPort: UsbSerialPort? = null
    private val _repeatTimes = 5
    override suspend fun write(data: ByteArray?, forceAnswer: Boolean, useDelay: Boolean): ByteArray? {
        return write(data, 250, true, useDelay)
    }

    override suspend fun write(
        data: ByteArray?,
        timeout: Long?,
        forceAnswer: Boolean,
        useDelay: Boolean
    ): ByteArray? = withContext(Dispatchers.IO) {
        if (_serialPort == null)
            connect("")
        val port = _serialPort?.takeIf {it.isOpen} ?: return@withContext null

        try {
            port.purgeHwBuffers(true, true)
        }
        catch (ex: UnsupportedOperationException) {
        } catch (ex: Exception) {
            Logger.e { "$tag : purge before write: $ex" }
        }
        try {
            if (useDelay && data != null) {
                for (b in data) {
                    port.write(byteArrayOf(b), 10)
                    delay(4)
                }
            } else
                port.write(data, timeout?.toInt() ?: 0)
        } catch (ex: CancellationException) {
            throw ex
        }
        catch (ex: Exception) {
            Logger.e{"$tag : write error : ${ex.toString()}"}
            return@withContext null
        }
        delay(timeout ?: 250)
        if (forceAnswer) {
            val outputStream = ByteArrayOutputStream()
            var result: ByteArray?
            val buffer = ByteArray(512)
            var n : Int
            try {
                var repeat = 0
                val time = measureTime {
                    do {
                        n = port.read(buffer,buffer.size, 100)
                        if (n > 0)
                            outputStream.write(buffer, 0, n)
                        delay(10)
                        repeat++
                    } while (repeat < _repeatTimes)
                    result = outputStream.toByteArray()
                }
                Logger.i{"$tag : Reading Time : $time"}
            } catch (ex: Exception) {
                Logger.e{"$tag : ${ex.toString()}"}
                result = null
            }
            return@withContext result
        } else {
            try {
                port.purgeHwBuffers(true, true)
            }
            catch (ex: UnsupportedOperationException) {
            } catch (ex: Exception) {
                Logger.e{"$tag : ${ex.toString()}"}
            }
        }

        return@withContext null
    }

    override fun disconnect() {
        if (_serialPort != null && _serialPort!!.isOpen) {
            try {
                _serialPort?.close()
                _connection?.close()
                _serialPort = null
                _connection = null
            } catch (ex: Exception) {
                Logger.e{"$tag : ${ex.toString()}"}
            }
        }
    }

    override fun connect(deviceName : String) {
        val usbDriver =  UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).firstOrNull()
        if (_connection == null) {
            _connection = usbManager.openDevice(usbDriver?.device)
        }
        if (_connection != null) {
            if (usbDriver?.ports?.isNotEmpty() == true) {
                _serialPort = usbDriver.ports[0]
                try {
                    if (_serialPort?.isOpen!!)
                        _serialPort?.close()
                }catch ( ex : Exception)
                {
                    Logger.i{"$tag : ${ex.toString()}"}
                }
            }
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
                    Logger.e{"$tag : ${ex.toString()}"}
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
            Logger.e{"$tag : ${ex.toString()}"}
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
                Logger.e{"$tag : ${ex.toString()}"}
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
            preciseDelay(initTime.toLong())

            _serialPort?.setBreak(false)
            preciseDelay(initTime.toLong())

            result = true
        } catch (ex: Exception) {
            Logger.e{"$tag : ${ex.message}"}
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
                    Logger.e{"$tag : ${ex.toString()}"}
                }
            }
        }
    }

    fun setFastInitSleep(newFastInitSleep: Int) {
        fastInitSleep = newFastInitSleep
    }
    private fun preciseDelay(millis: Long) {
        val delayNanos = millis * 1_000_000L
        val startTime = System.nanoTime()

        while (System.nanoTime() - startTime < delayNanos) {
            // waiting
        }
    }
}
