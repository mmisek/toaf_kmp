package pl.misek.toafmobile.protocols

import co.touchlab.kermit.Logger
import pl.misek.toafmobile.interfaces.IInterfaceDevice
import pl.misek.toafmobile.interfaces.IKWPInterfaceDevice
import pl.misek.toafmobile.interfaces.ISerialDevice

class ProtocolKWPStarEMS2K(private var device: IInterfaceDevice?, private val useDelay: Boolean) :
    IProtocol {
    private val tag = "ProtocolKWPStarEMS2K"

    override fun setDevice(newDevice: IInterfaceDevice?) {
        device = newDevice
    }

    override fun getDevice(): IInterfaceDevice? = device

    private fun connect(): Boolean {
        return if (device is ISerialDevice) (device as IKWPInterfaceDevice).initKWPStarDevice()
        else false
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray): OBDMessage {
        return write(address, command, data, 200)
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray, timeout: Long?): OBDMessage {
        val message = writeKwpStar(address, command, data, timeout)
        val messageData = if (message.wholeMessage != null && message.wholeMessage!!.isNotEmpty()) message.wholeMessage else message.message
        val stringBuilder = StringBuilder()
        for (b in messageData!!) stringBuilder.append(String.format("[%02X] ", b))
        Logger.i{"$tag : ${stringBuilder.toString()}"}
        return message
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

    private suspend fun writeKwpStar(address: Byte, command: Byte, data: ByteArray, timeout: Long?): OBDMessage {
        val message = OBDMessage()
        if (!connect()) return message
        var checksum: Byte
        var dataToSend = ByteArray(5 + data.size + 1)
        if (command == 0x81.toByte()) {
            dataToSend = ByteArray(5)
            dataToSend[0] = (0x81).toByte()
            dataToSend[1] = address
            dataToSend[2] = 0xF7.toByte()
            dataToSend[3] = 0x81.toByte()
        } else {
            dataToSend[0] = (0x80).toByte()
            dataToSend[1] = address
            dataToSend[2] = 0xF7.toByte()
            dataToSend[3] = (data.size + 1).toByte()
            dataToSend[4] = command
            for (i in data.indices) dataToSend[5 + i] = data[i]
        }
        checksum = 0x00
        for (i in 0 until dataToSend.size - 1) checksum = (checksum + dataToSend[i]).toByte()
        dataToSend[dataToSend.size - 1] = (checksum.toInt() and 0xFF).toByte()
        if (device is IKWPInterfaceDevice) (device as IKWPInterfaceDevice).kwpFastInit()
        val result = device?.write(dataToSend, timeout, true, useDelay)
        if (result != null) {
            try {
                message.message = dataToSend
                message.wholeMessage = result.copyOfRange(0, result.size)
                val shift = 0
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
                Logger.e{"$tag : ${ex.toString()}"}
            }
        }
        return message
    }
}
