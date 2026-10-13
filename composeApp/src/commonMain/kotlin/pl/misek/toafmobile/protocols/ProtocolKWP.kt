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

    /**
     * Wysyla zapytanie i czeka na odpowiedz koncowa: dopoki ostatnia ramka to 7F [command] 78 (response pending),
     * doczytuje kolejne ramki (zapis pustych danych - sam odczyt), lacznie do [pendingTimeout] ms.
     * Zwraca wiadomosc z ostatnia ramka odpowiedzi (answer / answerCode / isValid jak w write).
     */
    suspend fun writeWithPending(address: Byte, command: Byte, data: ByteArray, timeout: Long, pendingTimeout: Long): OBDMessage {
        val start = System.currentTimeMillis()
        val message = write(address, command, data, timeout, true)
        if (message.answer.isEmpty()) return message
        var frames = splitFrames(message.answer)
        var whole = message.wholeMessage ?: ByteArray(0)
        while (frames.lastOrNull()?.let { isPending(it, command) } == true) {
            if (System.currentTimeMillis() - start > pendingTimeout) {
                Logger.e { "$tag : response pending timeout, command %02X".format(command) }
                break
            }
            val more = device?.write(ByteArray(0), timeout, true, false)
            if (more == null || more.isEmpty()) continue
            Logger.i { "$tag : pending answer ${more.joinToString(" ") { String.format("%02X", it) }}" }
            whole += more
            frames = frames + splitFrames(more)
        }
        val last = frames.lastOrNull() ?: return message
        val result = OBDMessage()
        result.message = message.message
        result.wholeMessage = whole
        result.answer = last
        result.length = last.size
        result.answerCode = last[4]
        var checkSum: Byte = 0
        for (i in 0 until last.size - 1) checkSum = (checkSum.toInt() xor last[i].toInt()).toByte()
        result.isValid = last[last.size - 1] == checkSum && result.answerCode == (command + 0x40).toByte()
        return result
    }

    private fun isPending(frame: ByteArray, command: Byte) =
        frame.size >= 7 && frame[4] == 0x7F.toByte() && frame[5] == command && frame[6] == 0x78.toByte()

    /** Dzieli bajty odpowiedzi na ramki [B8][adr][adr][dl][dane][cs] */
    private fun splitFrames(bytes: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        var i = 0
        while (i + 4 < bytes.size) {
            val end = i + 4 + (bytes[i + 3].toInt() and 0xFF) + 1
            if (end > bytes.size) break
            frames.add(bytes.copyOfRange(i, end))
            i = end
        }
        return frames
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
