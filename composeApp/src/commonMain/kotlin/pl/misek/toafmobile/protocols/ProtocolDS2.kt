package pl.misek.toafmobile.protocols

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.interfaces.IInterfaceDevice
import pl.misek.toafmobile.interfaces.IKWPInterfaceDevice


class ProtocolDS2(private var device: IInterfaceDevice?) : IProtocol {
    private val tag = "ProtocolDS2"
    private var useDelay: Boolean = false
    private var currentBaudRate = 9600
    fun disconnect() {
        device?.disconnect()
    }

    suspend fun write(destinationDevice: Byte, data: ByteArray): OBDMessage {
        return write(destinationDevice, data, 50)
    }

    suspend fun write(destinationDevice: Byte, data: Array<ByteArray>): OBDMessage {
        val newData = data.flatMap { it.asList() }.toByteArray()
        return write(destinationDevice, newData)
    }

    suspend fun write(destinationDevice: Byte, data: Array<ByteArray>, timeout: Long?): OBDMessage {
        val newData = data.flatMap { it.asList() }.toByteArray()
        return write(destinationDevice, newData, timeout)
    }

    suspend fun write(destinationDevice: Byte, data: ByteArray, readTimeout: Long?): OBDMessage {
        val message = writeToDs2(destinationDevice, data, readTimeout)
        // Keep the answer code for invalid messages so callers can detect e.g. BUSY (0xA1)
        return message.takeIf { it.isValid } ?: OBDMessage().also { it.answerCode = message.answerCode }
    }

    /**
     * Odpowiedz = bajty po echu zapytania, przycieta do dlugosci z naglowka.
     * Resztki poprzedniej odpowiedzi przed echem (spozniona ramka) sa pomijane.
     */
    private fun extractAnswer(wholeMessage: ByteArray, request: ByteArray): ByteArray {
        var echoEnd = request.size
        for (i in 0..wholeMessage.size - request.size) {
            if (wholeMessage.copyOfRange(i, i + request.size).contentEquals(request)) {
                echoEnd = i + request.size
                break
            }
        }
        if (echoEnd > wholeMessage.size)
            return ByteArray(0)
        val answer = wholeMessage.copyOfRange(echoEnd, wholeMessage.size)
        val declaredLength = if (answer.size >= 2) answer[1].toInt() and 0xFF else 0
        return if (declaredLength in 4 until answer.size) answer.copyOf(declaredLength) else answer
    }

    private suspend fun writeToDs2(
        destinationDevice: Byte,
        data: ByteArray,
        readTimeout: Long?
    ): OBDMessage {
        if (device is IKWPInterfaceDevice && currentBaudRate == 9600) {
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
            Logger.i{"$tag No device : " + message.message.joinToString(" ") { "[%02X]".format(it) }}
            return message
        }

        message.wholeMessage = withContext(Dispatchers.IO) { device?.write(dataToSend, readTimeout, true, useDelay)}

        if (message.wholeMessage != null && message.wholeMessage!!.isNotEmpty()) {
            try {
                val messageToLog =
                    message.message.joinToString(" ") { "[%02X]".format(it) } +
                            " : " +
                            message.answer.joinToString(" ") { "[%02X]".format(it) } + "" +
                            "\n" +
                            message.wholeMessage!!.joinToString(" ") { "[%02X]".format(it) }
                Logger.i { "$tag:  $messageToLog" }
                message.answer = extractAnswer(message.wholeMessage!!, dataToSend)
                message.length = message.answer.size
                if (message.answer.isEmpty())
                    return message

                message.answerCode = message.answer[2]
                val checkSum = message.answer.dropLast(1)
                    .fold(0x00.toByte()) { acc, byte -> (acc.toInt() xor byte.toInt()).toByte() }
                message.isValid =
                    (message.answer[message.answer.size - 1] == checkSum) && (message.answerCode == 0xA0.toByte())
                Logger.i { "$tag : Message CS = ${message.answer[message.answer.size - 1]}" }
                Logger.i { "$tag : Calculated CS = $checkSum" }
                Logger.i { "$tag : AnswerCode = ${message.answerCode}" }
                Logger.i { "$tag: Message Valid : ${message.isValid}" }
            } catch (_: Exception) {
                message.answer = ByteArray(0)
                message.length = 0
                message.answerCode = 0x00.toByte()
                message.isValid = false
            }
        }
        return message
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray): OBDMessage {
        return write(address, byteArrayOf(command) + data)
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray, timeout: Long?): OBDMessage {
        return write(address,command,data,timeout, false)
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
        if (device is IKWPInterfaceDevice) {
            (device as IKWPInterfaceDevice).setBaudrate(baud)
            currentBaudRate = baud
        }
    }

    override fun setDevice(newDevice: IInterfaceDevice?) {
        device = newDevice
    }

    override fun getDevice(): IInterfaceDevice? = device
}