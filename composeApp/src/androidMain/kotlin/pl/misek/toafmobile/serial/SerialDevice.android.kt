package pl.misek.toafmobile.serial

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import co.touchlab.kermit.Logger
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.interfaces.ISerialDevice
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.time.measureTime

class AndroidSerialDevice (private val usbManager: UsbManager) : ISerialDevice
{
    private var tag = "SerialDevice"
    private var fastInitSleep = 25
    private var _connection: UsbDeviceConnection? = null
    private var _serialPort: UsbSerialPort? = null
    private val rxBuffer = java.util.concurrent.LinkedBlockingQueue<Byte>()
    private var ioManager: SerialInputOutputManager? = null
    override suspend fun write(data: ByteArray?, forceAnswer: Boolean, useDelay: Boolean): ByteArray? {
        return write(data, 50, true, useDelay)
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
            rxBuffer.clear()
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
            val out = ByteArrayOutputStream()
            val start = System.currentTimeMillis()
            var lastByteAt = start
            val totalTimeout = timeout ?: 1000
            val idleGap = 50L

            while (System.currentTimeMillis() - start < totalTimeout) {
                val b = rxBuffer.poll()
                if (b != null) {
                    out.write(b.toInt() and 0xFF)
                    lastByteAt = System.currentTimeMillis()
                } else {
                    if (out.size() > 0 && System.currentTimeMillis() - lastByteAt > idleGap) break
                    delay(2)
                }
            }
            return@withContext out.toByteArray().takeIf { it.isNotEmpty() }
        }
        /*
        delay(timeout ?: 25)
        if (forceAnswer) {
            val outputStream = ByteArrayOutputStream()
            var result: ByteArray?
            val buffer = ByteArray(512)
            try {
                val time = measureTime {
                    var idleMs = 0L
                    val maxIdle = 300L
                    val totalDeadline = 2000L
                    val start = System.currentTimeMillis()
                    while (idleMs < maxIdle && System.currentTimeMillis() - start < totalDeadline) {
                        val n = port.read(buffer, buffer.size, 25)
                        if (n > 0) {
                            outputStream.write(buffer, 0, n)
                            idleMs = 0
                        } else {
                            idleMs += 25
                        }
                    }
                    result = outputStream.toByteArray()
                }
                Logger.i{"$tag : Reading Time : $time"}
            } catch (ex: Exception) {
                Logger.e{"$tag : ${ex.toString()}"}
                result = null
            }
            return@withContext result
    */
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


    fun startIoManager(port: UsbSerialPort?) {
        ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) {
                for (b in data) rxBuffer.put(b)   // wątek w tle ciągle odbiera
            }
            override fun onRunError(e: Exception) {
                Logger.e { "$tag : IO manager error: $e" }
            }
        }).also { it.start() }
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
                    (_serialPort as? FtdiSerialDriver.FtdiSerialPort)?.let {
                        try { it.setLatencyTimer(1) } catch (_: Exception) {}
                    }
                    startIoManager(_serialPort)
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
