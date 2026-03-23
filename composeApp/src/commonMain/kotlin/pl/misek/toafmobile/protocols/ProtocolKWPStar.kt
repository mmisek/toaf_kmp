package pl.misek.toafmobile.protocols

import kotlinx.coroutines.delay
import pl.misek.toafmobile.interfaces.IInterfaceDevice
import pl.misek.toafmobile.interfaces.IKWPInterfaceDevice
import pl.misek.toafmobile.interfaces.wifi.ToafWifiInterface

class ProtocolKWPStar(private var device: IInterfaceDevice?, private val useDelay: Boolean) : IProtocol
{
    private val tag = "ProtocolKWPStar"

    override fun setDevice(newDevice: IInterfaceDevice?) {
        device = newDevice
    }

    override fun getDevice(): IInterfaceDevice?  = device

    private fun connect(): Boolean {
        return if (device is IKWPInterfaceDevice) (device as IKWPInterfaceDevice).initKWPStarDevice()
        else device is ToafWifiInterface
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray): OBDMessage {
        return write(address, command, data, 200)
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray, timeout: Long?): OBDMessage {
        return writeKWPStar(address, command, data, timeout)
    }

    override suspend fun write(
        address: Byte,
        command: Byte,
        data: ByteArray,
        timeout: Long?,
        forceAnswer: Boolean
    ): OBDMessage {
        return OBDMessage()
    }

    override fun setSpeed(baud: Int) {
    }

    private suspend fun writeKWPStar(address: Byte, command: Byte, data: ByteArray, timeout: Long?): OBDMessage {
        val message = OBDMessage()
        if (!connect()) return message
        var checksum: Byte
        val dataToSend = ByteArray(4 + data.size + 1)
        dataToSend[0] = (0x80 + data.size + 1).toByte()
        dataToSend[1] = address
        dataToSend[2] = 0xF7.toByte()
        dataToSend[3] = command
        System.arraycopy(data, 0, dataToSend, 4, data.size)
        checksum = 0x00
        for (i in 0 until dataToSend.size - 1) checksum = (checksum + dataToSend[i]).toByte()
        dataToSend[dataToSend.size - 1] = (checksum.toInt() and 0xFF).toByte()
        delay(200)
        if (device is IKWPInterfaceDevice) (device as IKWPInterfaceDevice).kwpFastInit()
        val result = device?.write(dataToSend, timeout, true, useDelay)
        if (result != null) {
            try {
                message.message = dataToSend
                message.wholeMessage = result.copyOfRange(0, result.size)
                if (message.wholeMessage!!.isNotEmpty()) {
                    val stringBuilder = StringBuilder()
                    for (b in message.wholeMessage!!)
                        stringBuilder.append(String.format("[%02X] ",b))
                    println("$tag : $stringBuilder")
                }
                val shift = 0
                //if (command == (byte)0x81)
                //    shift = 1;
                if (result.size > (dataToSend.size + 1)) {
                    message.answer =
                        result.copyOfRange(dataToSend.size + shift, result.size)
                    message.length = message.answer.size
                    if (message.answer.isEmpty()) return message
                    message.answerCode = message.answer[3]
                    checksum = 0
                    for (i in 0 until message.answer.size - 1) checksum =
                        (checksum + message.answer[i]).toByte()
                    message.isValid =
                        (message.answer[message.answer.size - 1] == (checksum.toInt() and 0xFF).toByte()) && (message.answerCode == (command + 0x40).toByte())
                }
            } catch (ex: Exception) {
                println("$tag : ${ex.toString()}")
            }
        }
        return message
    }
}
