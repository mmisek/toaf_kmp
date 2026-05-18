package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_bmbt
import toafmobile.composeapp.generated.resources.module_description_bmbt

private const val tag = "ModuleBMBT"
class ModuleBMBT(protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_BMBT, listOf(CarType.R40, CarType.L322)),
    IModuleControl, IModuleLiveData, IModuleEepromProgramming {

    override fun getKoinName() = KoinNames.ModuleR40Bmbt

    override fun getImageResource() = Res.drawable.module_bmbt

    override fun getModuleDescription() = Res.string.module_description_bmbt

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (i in 1..2) {
            val message = interfaceWrite(byteArrayOf(0x0B, i.toByte()))
            if (message.isValid) {
                try {
                    result[i] = message.answer.copyOfRange(3, message.answer.size)
                } catch ( ex : Exception)
                {
                    Logger.e{"$tag [readLiveData()], $ex, $message"}
                    return result.ifEmpty { null }
                }
            }
        }
        return result.ifEmpty { null }
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        when (controlElement.block) {
            0x00 -> return interfaceWrite(
                byteArrayOf(
                    0x1C,
                    0x01,
                    controlElement.value.toByte()
                )
            ).isValid

            0x01 -> return  interfaceWrite(
                byteArrayOf(
                    0x0C,
                    controlElement.byteInBlock.toByte(),
                    controlElement.value.toByte()
                )
            ).isValid

            0x0E -> return interfaceWrite(
                byteArrayOf(
                    0x9B.toByte(),
                    (controlElement.controlSetValue!!.value * 2).toByte()
                )
            ).isValid

            0x0F -> return interfaceWrite(
                byteArrayOf(0x9C.toByte(), controlElement.value.toByte()),
                1000
            ).isValid
        }
        return false
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) = true

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        val message = interfaceWrite(byteArrayOf(0x04, 0x01))
        if (message.isValid) {
            try {
                for (i in 0 until message.answer[3]) {
                    val errorCode = String.format("%02X", message.answer[4 + 2 * i])
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[5 + 2 * i].toInt() and 0x1F)))
                    errorDescription.add(ResourceHelper.getErrorDescriptionIsPresent((message.answer[5 + 2 * i].toInt() and 0x20) == 0x20))
                    if (result == null) result = ArrayList()
                    result.add(ModuleError(ModuleAddress.MODULE_BMBT, errorCode, errorDescription))
                }
            } catch ( ex: Exception)
            {
                Logger.e {"$tag [getAllErrors], $ex, $message"}
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
