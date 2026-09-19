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
    // Diagnostic only (DS2 timing investigation): timestamp of the most recent write,
    // read by the background onNewData() callback to log arrival times relative to it.
    private var lastWriteAtNanos: Long = 0L

    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }

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
            lastWriteAtNanos = System.nanoTime()
            Logger.i { "$tag [timing] write start data=${data?.toHex()}" }
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
        Logger.i { "$tag [timing] write done (+${(System.nanoTime() - lastWriteAtNanos) / 1_000_000}ms since write start)" }
        if (forceAnswer) {
            val out = ByteArrayOutputStream()
            val start = System.currentTimeMillis()
            var lastByteAt = start
            // Start listening immediately instead of an unconditional delay(timeout ?: 250)
            // first - keep the same overall time budget as before (that delay plus the
            // read window) so a slow answer still gets the same total time to arrive, but
            // a fast one is no longer held up by a wait it didn't need.
            val totalTimeout = (timeout ?: 250) + (timeout ?: 1000)
            val idleGap = 50L
            // Diagnostic only: DS2/KWP both echo the request before the real answer, so
            // once we've received exactly `echoSize` bytes the echo is complete; anything
            // beyond that is the start of the real answer. If both arrive in the same
            // chunk (common on fast/local links) the two timestamps below end up equal -
            // that's expected, not a bug.
            val echoSize = data?.size ?: 0
            var echoAtMs = -1L
            var answerStartAtMs = -1L

            while (System.currentTimeMillis() - start < totalTimeout) {
                val b = rxBuffer.poll()
                if (b != null) {
                    out.write(b.toInt() and 0xFF)
                    lastByteAt = System.currentTimeMillis()
                    val size = out.size()
                    if (echoAtMs < 0 && echoSize > 0 && size >= echoSize) {
                        echoAtMs = (System.nanoTime() - lastWriteAtNanos) / 1_000_000
                        Logger.i { "$tag [timing] echo complete after ${echoAtMs}ms since write start" }
                    }
                    if (answerStartAtMs < 0 && size > echoSize) {
                        answerStartAtMs = (System.nanoTime() - lastWriteAtNanos) / 1_000_000
                        val sinceEcho = if (echoAtMs >= 0) answerStartAtMs - echoAtMs else -1
                        Logger.i { "$tag [timing] answer started after ${answerStartAtMs}ms since write start (+${sinceEcho}ms after echo)" }
                    }
                } else {
                    if (out.size() > 0 && System.currentTimeMillis() - lastByteAt > idleGap) break
                    delay(2)
                }
            }
            val result = out.toByteArray()
            Logger.i { "$tag [timing] read loop done, ${result.size}B total (+${(System.nanoTime() - lastWriteAtNanos) / 1_000_000}ms since write start): ${result.toHex()}" }
            return@withContext result.takeIf { it.isNotEmpty() }
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


    fun startIoManager(port: UsbSerialPort?) {
        ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) {
                val sinceWriteMs = if (lastWriteAtNanos > 0) (System.nanoTime() - lastWriteAtNanos) / 1_000_000 else -1
                Logger.i { "$tag [timing] onNewData +${sinceWriteMs}ms since write start, ${data.size}B: ${data.toHex()}" }
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
