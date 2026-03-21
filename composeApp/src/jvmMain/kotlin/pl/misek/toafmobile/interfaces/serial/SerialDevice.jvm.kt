package pl.misek.toafmobile.interfaces.serial

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.interfaces.ISerialDevice
import java.io.ByteArrayOutputStream
import kotlin.time.measureTime

class DesktopSerialDevice : ISerialDevice
{
    private var tag = "SerialDevice"
    private var fastInitSleep = 25
    private var _serialPort : SerialPort? = null
    private var _serialPortName = ""

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
            try {
                if (useDelay && data != null) {
                    for (b in data) {
                        _serialPort!!.writeBytes(byteArrayOf(b), 1)
                        try {
                            delay(4)
                        } catch (ex: Exception) {
                             println("$tag : ${ex.toString()}")
                        }
                    }
                } else
                    _serialPort?.writeBytes(data, data?.size ?: 0)
            } catch (ex: Exception) {
               println("$tag : ${ex.toString()}")
                return@withContext null
            }
            delay(timeout ?: 50)
            if (forceAnswer) {
                val outputStream = ByteArrayOutputStream()
                var result: ByteArray?
                val buffer = ByteArray(512)
                try {
                    val time = measureTime {
                        var n : Int
                        do {
                            n = _serialPort!!.readBytes(buffer, buffer.size)
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
                return@withContext result
            } else {
                try {
                    _serialPort?.flushIOBuffers()
                } catch (ex: Exception) {
                   println("$tag : ${ex.toString()}")
                }
            }

            return@withContext null
        }else
            return@withContext null
    }

    override fun disconnect() {
        if (_serialPort != null && _serialPort!!.isOpen) {
            try {
                _serialPort?.closePort()
            } catch (ex: Exception) {
               println("$tag : ${ex.toString()}")
            }
        }
    }

    override fun connect(deviceName: String) {
        _serialPortName = "COM1"
        if (_serialPort == null)
        {
            _serialPort = SerialPort.getCommPort(_serialPortName)
            _serialPort?.let {
                it.baudRate = 9600
                it.numDataBits = 8
                it.parity = SerialPort.EVEN_PARITY
                it.numStopBits = SerialPort.ONE_STOP_BIT
                it.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 25,0)
                if (!it.openPort())
                    println("$tag : failed to open $deviceName")
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
           println("$tag : ${ex.toString()}")
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
            _serialPort?.setBreak()
            Thread.sleep(initTime.toLong())
            _serialPort!!.clearBreak()
            Thread.sleep(initTime.toLong())
            result = true
        } catch (ex: Exception) {
           println("$tag : ${ex.toString()}")
        }

        return result
    }

    override fun setDS2Device() {
        setBaudrate(9600)
    }

    fun setFastInitSleep(newFastInitSleep: Int) {
        fastInitSleep = newFastInitSleep
    }
}
