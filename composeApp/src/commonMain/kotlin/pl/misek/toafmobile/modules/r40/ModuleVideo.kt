package pl.misek.toafmobile.modules.r40

import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_video
import toafmobile.composeapp.generated.resources.module_video

class ModuleVideo(protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_VIDEO, listOf(CarType.R40, CarType.L322)),
    IModuleLiveData, IModuleEepromProgramming {
    override fun getImageResource() = Res.drawable.module_video

    override fun getKoinName() = KoinNames.ModuleR40Video

    override fun getModuleDescription()= Res.string.module_description_video

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val message = interfaceWrite(byteArrayOf(0x1B, 0x90.toByte()))
        if (message.isValid) {
            result[0x00] = message.answer.copyOfRange(3, message.answer.size)
        }
        return result.ifEmpty { null }
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>)= true

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        var message = interfaceWrite(byteArrayOf(0x04, 0x01))
        if (message.isValid) {
            result = ArrayList()
            val errorDescription: MutableList<Pair<StringResource,Any?>> = ArrayList()
            for (i in 0 until message.answer[3]) {
                val errorCode = String.format("%02X", 0x80 + message.answer[4 + 2 * i])
                errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[5 + 2 * i].toInt() and 0xFF)))
                result.add(ModuleError(ModuleAddress.MODULE_VIDEO, errorCode, errorDescription))
            }
        }
        message = interfaceWrite(byteArrayOf(0x14, 0x01))
        if (message.isValid) {
            for (i in 0 until message.answer[3]) {
                val errorCode = String.format("%02X", message.answer[4 + 2 * i])
                val errorDescription: MutableList<Pair<StringResource,Any?>> = ArrayList()
                errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[5 + 2 * i].toInt() and 0xFF)))
                if (result == null) result = ArrayList()
                result.add(ModuleError(ModuleAddress.MODULE_VIDEO, errorCode, errorDescription))
            }
        }
        return result
    }

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? {
        val message = interfaceWrite(
            byteArrayOf(
                0x06,
                0x03,
                0x00,
                0x00,
                (address and 0xFF).toByte(),
                size.toByte()
            )
        )
        return if (message.isValid) message.answer.copyOfRange(0x03, 3 + size) else null
    }

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val dataToWrite =
            byteArrayOf(
                0x07,
                0x03,
                0x00,
                0x00,
                (address and 0xFF).toByte(),
                (data.size / 2).toByte()) + data
        return interfaceWrite(dataToWrite).isValid
    }

    override fun getEepromSize(): Int {
        return 256
    }

    override fun getEepromReadSize(): Int {
        return 8
    }

    override fun getEepromWriteSize(): Int {
        return 8
    }
}
