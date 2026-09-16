package pl.misek.toafmobile.interfaces.serial

import co.touchlab.kermit.Logger
import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.interfaces.ISerialDevice
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class DesktopSerialDevice : ISerialDevice
{
    private var tag = "SerialDevice"
    private var fastInitSleep = 25
    private var _serialPort : SerialPort? = null
    private var _serialPortName = ""

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
        if (_serialPort == null) connect(_serialPortName)
        if (_serialPort != null && _serialPort!!.isOpen) {
            // Diagnostic only (DS2 timing investigation): nanoTime reference for all
            // [timing] log lines below, so arrival times can be compared to write start.
            val writeStartNanos = System.nanoTime()
            Logger.i { "$tag [timing] write start data=${data?.toHex()}" }
            try {
                if (useDelay && data != null) {
                    for (b in data) {
                        _serialPort!!.writeBytes(byteArrayOf(b), 1)
                        try {
                            delay(4.milliseconds)
                        } catch (ex: Exception) {
                            Logger.e{"$tag : ${ex.toString()}"}
                        }
                    }
                } else
                    _serialPort?.writeBytes(data, data?.size ?: 0)
            } catch (ex: Exception) {
                Logger.e{"$tag : ${ex.toString()}"}
                return@withContext null
            }
            Logger.i { "$tag [timing] write done (+${(System.nanoTime() - writeStartNanos) / 1_000_000}ms since write start)" }
            if (forceAnswer) {
                val outputStream = ByteArrayOutputStream()
                var result: ByteArray?
                val buffer = ByteArray(512)
                try {
                    val start = System.currentTimeMillis()
                    var lastByteAt = start
                    // Start reading immediately instead of an unconditional
                    // delay(timeout ?: 100) first, then keep the same overall time budget
                    // as before (that delay plus the fixed read window) so a slow answer
                    // still gets the same total time to arrive, stopping early once the
                    // line has gone quiet for a bit (idleGap) after some data came in.
                    val totalTimeout = (timeout ?: 100) + (timeout ?: 500)
                    val idleGap = 50L
                    val time = measureTime {
                        while (System.currentTimeMillis() - start < totalTimeout) {
                            val n = _serialPort!!.readBytes(buffer, buffer.size)
                            if (n > 0) {
                                Logger.i { "$tag [timing] read +${(System.nanoTime() - writeStartNanos) / 1_000_000}ms since write start, ${n}B: ${buffer.copyOfRange(0, n).toHex()}" }
                                outputStream.write(buffer, 0, n)
                                lastByteAt = System.currentTimeMillis()
                            } else {
                                if (outputStream.size() > 0 && System.currentTimeMillis() - lastByteAt > idleGap) break
                                delay(2.milliseconds)
                            }
                        }
                        result = outputStream.toByteArray()
                    }
                    Logger.i{"$tag : Reading Time : $time"}
                    Logger.i { "$tag [timing] read loop done, ${result?.size ?: 0}B total (+${(System.nanoTime() - writeStartNanos) / 1_000_000}ms since write start): ${result?.toHex()}" }
                } catch (ex: Exception) {
                    Logger.e{"$tag : ${ex.toString()}"}
                    result = null
                }
                return@withContext result
            } else {
                try {
                    _serialPort?.flushIOBuffers()
                } catch (ex: Exception) {
                    Logger.e{"$tag : ${ex.toString()}"}
                }
            }

            return@withContext null
        }else
            return@withContext null
    }

    override fun disconnect() {
        if (_serialPort != null) {
            try {
                _serialPort?.closePort()
            } catch (ex: Exception) {
                Logger.e{"$tag : ${ex.toString()}"}
            }
            finally {
                _serialPort = null
            }
        }
    }

    override fun connect(deviceName: String) {
        Logger.i { "Connecting with : $_serialPortName" }
        _serialPortName = deviceName
        if (_serialPort != null && _serialPort!!.isOpen())
        {
            _serialPort!!.closePort()
            _serialPort = null
        }
        if (_serialPort == null)
        {
            val availablePorts = SerialPort.getCommPorts().map { it.systemPortName }.toSet()
            if (!availablePorts.contains(_serialPortName))
            {
                _serialPort = null
                Logger.i { "$tag : port $_serialPortName not found in available ports : $availablePorts" }
                return
            }
            _serialPort = SerialPort.getCommPort(_serialPortName)
            if (_serialPort != null) {
                _serialPort?.baudRate = 9600
                _serialPort?.numDataBits = 8
                _serialPort?.parity = SerialPort.EVEN_PARITY
                _serialPort?.numStopBits = SerialPort.ONE_STOP_BIT
                _serialPort?.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 25,0)
                if (_serialPort?.openPort() == false) {
                    Logger.e { "$tag : failed to open $deviceName, lastErrorCode : ${_serialPort?.lastErrorCode} "}
                    _serialPort = null
                    return
                }
                else {
                    Logger.i {"$tag: Connected with ${_serialPort?.systemPortName}"}
                }
                _serialPort?.addDataListener(object : SerialPortDataListener {
                    override fun getListeningEvents(): Int {
                        return SerialPort.LISTENING_EVENT_PORT_DISCONNECTED
                    }

                    override fun serialEvent(event: SerialPortEvent?) {
                        when (event?.eventType) {
                            SerialPort.LISTENING_EVENT_PORT_DISCONNECTED -> {
                                Logger.i { "Disconnected : ${_serialPort?.systemPortName}" }
                                disconnect()
                                _serialPort?.removeDataListener()
                            }
                        }
                    }
                }
                )
            }
        }
    }

    override val isConnected: Boolean
        get() = (_serialPort != null && _serialPort!!.isOpen)

    override fun setBaudrate(baudrate: Int) {
        try {
            if (_serialPort == null ) connect(_serialPortName)
            _serialPort?.let {
                it.baudRate = baudrate
                it.numStopBits = SerialPort.ONE_STOP_BIT
                it.parity = SerialPort.EVEN_PARITY
                it.numDataBits = 8
            }
        } catch (ex: Exception) {
            Logger.e{"$tag : ${ex.toString()}"}
        }
    }

    override fun initKWPStarDevice(): Boolean {
        var result = false
        if (_serialPort != null && _serialPort!!.isOpen) {
            try {
                if (_serialPort == null ) connect(_serialPortName)
                _serialPort?.let {
                    it.baudRate = 10400
                    it.numStopBits = SerialPort.ONE_STOP_BIT
                    it.parity = SerialPort.NO_PARITY
                    it.numDataBits = 8
                }
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
            _serialPort?.setBreak()
            preciseDelay(initTime.toLong())

            _serialPort?.clearBreak()
            preciseDelay(initTime.toLong())

            _serialPort?.flushIOBuffers()
            result = true
        } catch (ex: Exception) {
            Logger.e{"$tag : ${ex.message}"}
        }

        return result
    }

    override fun setDS2Device() {
        setBaudrate(9600)
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
