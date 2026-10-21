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
            // Spozniona odpowiedz na poprzednie zapytanie nie moze trafic do biezacego odczytu
            try {
                _serialPort!!.flushIOBuffers()
            } catch (ex: Exception) {
                Logger.e{"$tag : ${ex.toString()}"}
            }
            val writeStartNanos = System.nanoTime()
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
            if (forceAnswer) {
                val outputStream = ByteArrayOutputStream()
                var result: ByteArray?
                val buffer = ByteArray(512)
                try {
                    val start = System.currentTimeMillis()
                    var lastByteAt = start
                    val totalTimeout = (timeout ?: 100) + (timeout ?: 500)
                    val idleGap = 50L
                    val echoSize = data?.size ?: 0
                    var echoAtMs = -1L
                    var answerStartAtMs = -1L
                    val time = measureTime {
                        while (System.currentTimeMillis() - start < totalTimeout) {
                            val n = _serialPort!!.readBytes(buffer, buffer.size)
                            if (n > 0) {
                                outputStream.write(buffer, 0, n)
                                lastByteAt = System.currentTimeMillis()
                                val size = outputStream.size()
                                if (echoAtMs < 0 && echoSize > 0 && size >= echoSize) {
                                    echoAtMs = (System.nanoTime() - writeStartNanos) / 1_000_000
                                }
                                if (answerStartAtMs < 0 && size > echoSize) {
                                    answerStartAtMs = (System.nanoTime() - writeStartNanos) / 1_000_000
                                    val sinceEcho = if (echoAtMs >= 0) answerStartAtMs - echoAtMs else -1
                                }
                            } else {
                                if (outputStream.size() > echoSize && System.currentTimeMillis() - lastByteAt > idleGap) break
                                delay(2.milliseconds)
                            }
                        }
                        result = outputStream.toByteArray()
                    }
                    Logger.i{"$tag : Reading Time : $time"}
                } catch (ex: Exception) {
                    Logger.e{"$tag : $ex"}
                    result = null
                }
                return@withContext result
            } else {
                try {
                    _serialPort?.flushIOBuffers()
                } catch (ex: Exception) {
                    Logger.e{"$tag : $ex"}
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
                Logger.e{"$tag : $ex"}
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

    override fun kw1281SlowInit(address: Int, baudrate: Int): Boolean {
        try {
            if (_serialPort == null) connect(_serialPortName)
            val port = _serialPort?.takeIf { it.isOpen } ?: return false
            port.baudRate = baudrate
            port.numDataBits = 8
            port.numStopBits = SerialPort.ONE_STOP_BIT
            port.parity = SerialPort.NO_PARITY
            port.clearBreak()
            preciseDelay(300)
            // bit startu (0), 8 bitow adresu od LSB (0 - break, 1 - linia wolna), bit stopu (1)
            port.setBreak()
            preciseDelay(200)
            for (bit in 0..7) {
                if (((address shr bit) and 0x01) == 0) port.setBreak() else port.clearBreak()
                preciseDelay(200)
            }
            port.clearBreak()
            port.flushIOBuffers()
            return true
        } catch (ex: Exception) {
            Logger.e { "$tag [kw1281SlowInit] : $ex" }
            return false
        }
    }

    override fun kw1281WriteByte(value: Int): Boolean {
        return try {
            (_serialPort?.takeIf { it.isOpen }?.writeBytes(byteArrayOf(value.toByte()), 1) ?: 0) == 1
        } catch (ex: Exception) {
            Logger.e { "$tag [kw1281WriteByte] : $ex" }
            false
        }
    }

    override fun kw1281ReadByte(timeoutMs: Long): Int {
        val port = _serialPort?.takeIf { it.isOpen } ?: return -1
        return try {
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, timeoutMs.toInt().coerceAtLeast(1), 0)
            val buffer = ByteArray(1)
            if (port.readBytes(buffer, 1) == 1) buffer[0].toInt() and 0xFF else -1
        } catch (ex: Exception) {
            Logger.e { "$tag [kw1281ReadByte] : $ex" }
            -1
        } finally {
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 25, 0)
        }
    }

    override fun kw1281ClearInput() {
        try {
            _serialPort?.flushIOBuffers()
        } catch (ex: Exception) {
            Logger.e { "$tag [kw1281ClearInput] : $ex" }
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
