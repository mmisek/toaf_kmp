package pl.misek.toafmobile.protocols

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import pl.misek.toafmobile.interfaces.IInterfaceDevice
import pl.misek.toafmobile.interfaces.IKWPInterfaceDevice

class ProtocolEDC15(private var device: IInterfaceDevice?) : IProtocol {
    private val tag = "ProtocolEDC15"
    private val tasInitTime = 24

    override fun setDevice(newDevice: IInterfaceDevice?) {
        device = newDevice
    }

    private fun connect(): Boolean {
        if (device is IKWPInterfaceDevice) {
            if (device?.isConnected == false) device?.connect("")
            return (device as IKWPInterfaceDevice).initKWPStarDevice()
        } else return false
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray): OBDMessage {
        var result = OBDMessage()
        CoroutineScope(Dispatchers.IO).launch {
            result = async {write(address, command, data, 200)}.await()
        }
        return result
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray, timeout: Int): OBDMessage {
        var message = OBDMessage()
        for (i in 0..4) {
            CoroutineScope(Dispatchers.IO).launch {
                message = async {writeEDC15(address, command, data, timeout)}.await()
            }
            if (message.wholeMessage != null && message.wholeMessage!!.isNotEmpty()) {
                val stringBuilder = StringBuilder()
                for (b in message.wholeMessage!!) stringBuilder.append(
                    String.format(
                        "[%02X] ",
                        b
                    )
                )
                println("$tag $stringBuilder")
            }
            if (!message.isValid) {
                Thread.sleep(300)
            }
        }
        message.isValid = false
        return message
    }

    override suspend fun write(
        address: Byte,
        command: Byte,
        data: ByteArray,
        timeout: Int,
        forceAnswer: Boolean
    ): OBDMessage {
        return OBDMessage()
    }

    override fun setSpeed(baud: Int) {
    }

    private suspend fun writeEDC15(address: Byte, command: Byte, data: ByteArray, timeout: Int): OBDMessage {
        val message = OBDMessage()
        if (!connect()) return message
        var checksum: Byte
        val dataToSend: ByteArray
        if (command == 0x81.toByte()) {
            dataToSend = ByteArray(4 + data.size + 1)
            dataToSend[0] = (0x80 + data.size + 1).toByte()
            dataToSend[1] = address
            dataToSend[2] = 0xF7.toByte()
            dataToSend[3] = command
        } else if (command == 0x27.toByte() && data[0] == 0x07.toByte()) {
            dataToSend = ByteArray(4 + data.size + 1)
            dataToSend[0] = (0x80 + data.size + 1).toByte()
            dataToSend[1] = address
            dataToSend[2] = 0xF1.toByte()
            dataToSend[3] = command
            System.arraycopy(data, 0, dataToSend, 4, data.size)
        } else if (command == 0x10.toByte() && data[0] == 0x85.toByte()) {
            dataToSend = ByteArray(4 + data.size + 1)
            dataToSend[0] = (0x80 + data.size + 1).toByte()
            dataToSend[1] = address
            dataToSend[2] = 0xF1.toByte()
            dataToSend[3] = command
            System.arraycopy(data, 0, dataToSend, 4, data.size)
        } else {
            dataToSend = ByteArray(5 + data.size + 1)
            dataToSend[0] = 0x80.toByte()
            dataToSend[1] = address
            dataToSend[2] = if (command == 0x30.toByte()) 0xF1.toByte() else 0xF7.toByte()
            dataToSend[3] = (data.size + 1).toByte()
            dataToSend[4] = command
            System.arraycopy(data, 0, dataToSend, 5, data.size)
        }
        checksum = 0x00
        for (i in 0 until dataToSend.size - 1) checksum = (checksum + dataToSend[i]).toByte()
        dataToSend[dataToSend.size - 1] = (checksum.toInt() and 0xFF).toByte()
        if (device is IKWPInterfaceDevice && command == 0x81.toByte()) (device as IKWPInterfaceDevice).kwpFastInit(
            tasInitTime
        )
        val result = device?.write(dataToSend, timeout, true, IProtocol.useDelay)
        if (result != null) {
            try {
                message.message = dataToSend
                message.wholeMessage = result.copyOfRange(0, result.size)
                if (result.size > (dataToSend.size + 1)) {
                    message.answer = result.copyOfRange(dataToSend.size, result.size)
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
                println("$tag: ${ex.toString()}")
            }
        }
        return message
    }
}
