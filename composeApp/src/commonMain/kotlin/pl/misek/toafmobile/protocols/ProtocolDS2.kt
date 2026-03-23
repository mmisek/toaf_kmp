package pl.misek.toafmobile.protocols

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.interfaces.IInterfaceDevice
import pl.misek.toafmobile.interfaces.IKWPInterfaceDevice


class ProtocolDS2(private var device: IInterfaceDevice?) : IProtocol {
    private val tag = "ProtocolDS2"
    private var useDelay: Boolean = false

    fun disconnect() {
        device?.disconnect()
    }

    suspend fun write(destinationDevice: Byte, data: ByteArray): OBDMessage {
        return write(destinationDevice, data, 250)
    }

    suspend fun write(destinationDevice: Byte, data: Array<ByteArray>): OBDMessage {
        val newData = data.flatMap { it.asList() }.toByteArray()
        return write(destinationDevice, newData)
    }

    suspend fun write(destinationDevice: Byte, data: ByteArray, readTimeout: Long?): OBDMessage {
        return writeToDs2(destinationDevice, data, readTimeout).takeIf { it.isValid } ?: OBDMessage()
    }

    private suspend fun writeToDs2(
        destinationDevice: Byte,
        data: ByteArray,
        readTimeout: Long?
    ): OBDMessage {
        if (device is IKWPInterfaceDevice) {
            (device as IKWPInterfaceDevice).setDS2Device()
        }

        val message = OBDMessage()
        val dataToSend = ByteArray(3 + data.size).apply {
            this[0] = destinationDevice
            this[1] = size.toByte()
            System.arraycopy(data, 0, this, 2, data.size)
            this[lastIndex] =
                dropLast(1).fold(0x00.toByte()) { acc, byte -> (acc.toInt() xor byte.toInt()).toByte() }
        }

        message.message = dataToSend
        if (device == null) {
            println("$tag No device : " + message.message.joinToString(" ") { "[%02X]".format(it) })
            return message
        }

        message.wholeMessage = withContext(Dispatchers.IO) { device?.write(dataToSend, readTimeout, true, useDelay)}
        if (message.wholeMessage != null && message.wholeMessage!!.isNotEmpty()) {
            val messageToLog =
                message.message.joinToString(" ") { "[%02X]".format(it) } +
                        " : " +
                        message.wholeMessage!!.joinToString(" ") { "[%02X]".format(it) }
            println("$tag:  $messageToLog")
        }

        try {
            message.answer =
                message.wholeMessage!!.copyOfRange(dataToSend.size, message.wholeMessage!!.size)
            message.length = message.answer.size
            if (message.answer.isEmpty())
                return message

            message.answerCode = message.answer[2]
            val checkSum = message.answer.dropLast(1)
                .fold(0x00.toByte()) { acc, byte -> (acc.toInt() xor byte.toInt()).toByte() }
            message.isValid =
                (message.answer[message.answer.size - 1] == checkSum) && (message.answerCode == 0xA0.toByte())
        } catch (ex: Exception) {
            message.answer = ByteArray(0)
            message.length = 0
            message.answerCode = 0x00.toByte()
            message.isValid = false
        }
        return message
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray): OBDMessage {
        return write(address, byteArrayOf(command) + data)
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray, timeout: Long?): OBDMessage {
        return OBDMessage()
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

    override fun setDevice(newDevice: IInterfaceDevice?) {
        device = newDevice
    }

    override fun getDevice(): IInterfaceDevice? = device
}