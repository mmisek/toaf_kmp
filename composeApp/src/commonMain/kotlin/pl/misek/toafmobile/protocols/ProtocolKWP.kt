package pl.misek.toafmobile.protocols

import co.touchlab.kermit.Logger
import pl.misek.toafmobile.interfaces.IInterfaceDevice
import pl.misek.toafmobile.interfaces.IKWPInterfaceDevice

open class ProtocolKWP(private var device: IInterfaceDevice?, var useDelay: Boolean) : IProtocol {
    private val tag = "ProtocolKWP"

    override fun setDevice(newDevice: IInterfaceDevice?) {
        device = newDevice
    }

    override fun getDevice(): IInterfaceDevice? =device

    fun disconnect() {
        device?.disconnect()
    }

    override fun setSpeed(baud: Int) {
        if (device is IKWPInterfaceDevice) {
            (device as IKWPInterfaceDevice).setBaudrate(baud)
            useDelay = baud == 9600
        }
    }

    private suspend fun writeWithNoAnswer(address: Byte, command: Byte, data: ByteArray): OBDMessage {
        return writeWithNoAnswer(address, command, data, 200)
    }

    private suspend fun writeWithNoAnswer(address: Byte, command: Byte, data: ByteArray, timeout: Long?): OBDMessage {
        return write(address, command, data, timeout, false)
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray): OBDMessage {
        return write(address, command, data, 200, true)
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray, timeout: Long?): OBDMessage {
        return write(address, command, data, timeout, true)
    }

    override suspend fun write(
        address: Byte,
        command: Byte,
        data: ByteArray,
        timeout: Long?,
        forceAnswer: Boolean
    ): OBDMessage {
        val message = writeKWP(address, command, data, timeout, forceAnswer)
        val stringBuilder = StringBuilder()
        if (message.wholeMessage != null)
            for (b in message.wholeMessage!!) stringBuilder.append(String.format("[%02X] ", b))
        else
            for (b in message.message) stringBuilder.append(String.format("[%02X] ", b))
        Logger.i{"$tag: $stringBuilder"}

        return message
    }

    private suspend fun writeKWP(
        address: Byte,
        command: Byte,
        data: ByteArray,
        timeout: Long?,
        forceAnswer: Boolean
    ): OBDMessage {
        //if (device is IKWPInterfaceDevice)
        //     (device as IKWPInterfaceDevice).setDS2Device()

        val message = OBDMessage()
        val dataToSend: ByteArray
        var checkSum: Byte = 0x00
        try {
            dataToSend = ByteArray(5 + data.size + 1)
            dataToSend[0] = 0xB8.toByte()
            dataToSend[1] = address
            if (address.toInt() == 0x12) dataToSend[2] = 0xF1.toByte()
            else dataToSend[2] = 0xF7.toByte()
            dataToSend[3] = (data.size + 1).toByte()
            dataToSend[4] = command
            for (i in 5 until data.size + 5) dataToSend[i] = data[i - 5]

            for (i in 0 until dataToSend.size - 1) checkSum =
                (checkSum.toInt() xor dataToSend[i].toInt()).toByte()
            dataToSend[dataToSend.size - 1] = checkSum
            try {
                message.message = dataToSend
                message.wholeMessage = device?.write(dataToSend, timeout, forceAnswer, this.useDelay)
                if (message.wholeMessage!= null && message.wholeMessage!!.isNotEmpty()) {
                    message.answer =
                        message.wholeMessage!!.copyOfRange(
                        dataToSend.size,
                        message.wholeMessage!!.size
                    )
                    message.length = message.answer.size
                    if (message.answer.isEmpty()) return message
                    message.answerCode = message.answer[4]
                    checkSum = 0
                    for (i in 0 until message.answer.size - 1) checkSum =
                        (checkSum.toInt() xor message.answer[i].toInt()).toByte()
                    message.isValid =
                        (message.answer[message.answer.size - 1] == checkSum) && (message.answerCode == (command + 0x40).toByte())
                    Logger.i { "$tag : Message CS = ${message.answer[message.answer.size - 1]}" }
                    Logger.i { "$tag : Calculated CS = $checkSum" }
                    Logger.i { "$tag : AnswerCode = ${message.answerCode}" }
                    Logger.i { "$tag: Message Valid : ${message.isValid}" }
                }
            } catch (ex: Exception) {
                Logger.e{"$tag: ${ex.toString()}"}
                message.isValid = false
            }
            return message
        } catch (ex: Exception) {
            device!!.disconnect()
            return message
        }
    }
}
