package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_smac
import toafmobile.composeapp.generated.resources.module_smac
import kotlinx.coroutines.CancellationException

private const val tag = "ModuleSMAC"
class ModuleSMAC( protocolDS2: ProtocolDS2?) : ModuleDS2( protocolDS2, ModuleAddress.MODULE_SMAC, listOf(CarType.R40)), IModuleControl, IModuleCoding
{
    override fun getKoinName() = KoinNames.ModuleR40Smac

    override fun getImageResource() = Res.drawable.module_smac

    override fun getModuleDescription() = Res.string.module_description_smac

    override suspend fun control(controlElement: ControlElement): Boolean {
        val data = byteArrayOf(0x0C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        data[controlElement.byteInBlock + 2] = controlElement.value.toByte()
        return interfaceWrite(data).isValid
    }

    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        for (i in 0..0xb) {
            val message = interfaceWrite(byteArrayOf(0x08, i.toByte()))
            try {
                if (message.isValid) result.add(
                    message.answer.copyOfRange(3, message.answer.size - 1)
                )
                else return null
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [readCoding], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        val dataToSaveWithCRC = calculateCodingChecksum(dataToSave)
        for (i in dataToSaveWithCRC.indices) {
            val message = interfaceWrite(
                arrayOf(
                    byteArrayOf(
                        0x09,
                        i.toByte()
                    ), dataToSaveWithCRC[i]
                )
            )
            if (!message.isValid) return false
        }
        reset()
        return true
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        for (j in 0..2) {
            val message = interfaceWrite(byteArrayOf(0x04, j.toByte()))
            try {
                if (message.isValid) {
                    for (i in 0..6) {
                        if ((message.answer[3 + 2 * i].toInt() and 0xFF) != 0xFF) {
                            val errorCode = String.format("%02X", message.answer[3 + 2 * i])
                            val errorDescription: MutableList<Pair<StringResource, Any?>> =
                                ArrayList()
                            errorDescription.add(ResourceHelper.getErrorDescriptionCount(message.answer[4 + 2 * i].toInt() and 0x1F))
                            errorDescription.add(ResourceHelper.getErrorDescriptionIsStatic((message.answer[4 + 2 * i].toInt() and 0x20) == 0x20))
                            if (result == null) result = ArrayList()
                            result.add(
                                ModuleError(
                                    ModuleAddress.MODULE_SHD,
                                    errorCode,
                                    errorDescription
                                )
                            )
                        }
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getAllErrors], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun getVIN(): String? {
        val message = interfaceWrite(byteArrayOf(0x02))
        try{
            if (message.isValid) {
                return String.format(
                    "%s%02X%02X%02X",
                    String(message.answer.copyOfRange(3, 14)),
                    message.answer[14], message.answer[15], message.answer[16]
                )
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getVIN], $ex, $message"}
        }

        return null
    }

    private fun calculateCodingChecksum(data: List<ByteArray>): List<ByteArray> {
        var cs = 0x00
        for (i in data.indices) for (j in data[i].indices) cs = cs xor data[i][j].toInt()
        cs = cs xor data[9][data[9].size - 1].toInt()
        data[9][data[9].size - 1] = cs.toByte()
        return data
    }
}
