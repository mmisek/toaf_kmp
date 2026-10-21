package pl.misek.toafmobile.modules.l322

import androidx.compose.ui.text.intl.Locale
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
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.elv_error_clear_date
import toafmobile.composeapp.generated.resources.elv_error_info_memory
import toafmobile.composeapp.generated.resources.module_description_elv
import toafmobile.composeapp.generated.resources.module_elv
import kotlinx.coroutines.CancellationException

class ModuleELV( protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_ELV, listOf(CarType.L322)),
    IModuleLiveData, IModuleControl, IModuleCoding {
    private val tag = "ModuleELV"

    override fun getKoinName() = KoinNames.ModuleL322Elv

    override fun getImageResource() = Res.drawable.module_elv

    override fun getModuleDescription() = Res.string.module_description_elv

    override suspend fun getAllErrors(): List<ModuleError>? {
        val header = interfaceWrite(byteArrayOf(0x04, 0x00))
        if (!header.isValid || header.answer.size < 6) return null
        val result: MutableList<ModuleError> = ArrayList()
        val count = header.answer[3].toInt() and 0xFF
        val clearDate = String.format("%02d/%02d", header.answer[4].toInt() and 0xFF, header.answer[5].toInt() and 0xFF)
        val blocks = when {
            count == 0 -> emptyList()
            count <= 10 -> listOf(0x01)
            else -> listOf(0x01, 0x02)
        }
        for (block in blocks)
            readErrorBlock(byteArrayOf(0x04, block.toByte()), result, Pair(Res.string.elv_error_clear_date, clearDate))
        readErrorBlock(byteArrayOf(0x14, 0x00), result, Pair(Res.string.elv_error_info_memory, null))
        return result
    }

    private suspend fun readErrorBlock(request: ByteArray, result: MutableList<ModuleError>, extraDetail: Pair<StringResource, Any?>) {
        val message = interfaceWrite(request)
        try {
            if (message.isValid && message.answer.size > 4) {
                for (i in 0 until (message.answer[3].toInt() and 0xFF)) {
                    val errorCode = String.format("%02X", message.answer[4 + 2 * i])
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[5 + 2 * i].toInt() and 0xFF) and 0x1F))
                    errorDescription.add(ResourceHelper.getErrorDescriptionIsStatic((message.answer[5 + 2 * i].toInt() and 0x20) == 0x20))
                    errorDescription.add(extraDetail)
                    result.add(
                        ModuleError(
                            ModuleAddress.MODULE_ELV,
                            errorCode,
                            errorDescription
                        )
                    )
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readErrorBlock], $ex, $message"}
        }
    }

    override suspend fun control(controlElement: ControlElement): Boolean =
        interfaceWrite(byteArrayOf(0x0C, controlElement.block.toByte(), controlElement.value.toByte())).isValid

    override suspend fun readCoding(): List<ByteArray>? {
        val message = interfaceWrite(byteArrayOf(0x08), 300)
        try {
            if (message.isValid && message.answer.size >= 6)
                return listOf(message.answer.copyOfRange(3, 6))
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readCoding], $ex, $message" }
        }
        return null
    }

    // Zapis: 09 <adres 00> <3 bajty> (ADDRESS_MODE FREE), weryfikacja ponownym odczytem
    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        val data = dataToSave.firstOrNull() ?: return false
        if (data.size != 3) return false
        if (!interfaceWrite(byteArrayOf(0x09, 0x00) + data).isValid) return false
        val verify = readCoding()?.firstOrNull()
        return verify != null && verify.contentEquals(data)
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val message = interfaceWrite(byteArrayOf(0x0B))
        try {
            if (message.isValid)
                result[0] = message.answer.copyOfRange(3, message.answer.size)
            else
                return null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e { "$tag [readLiveData],$ex, $message" }
            return null
        }
        return result
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean  = true

    suspend fun readEeprom(): ByteArray? {
        var result = ByteArray(0)
        var address: Byte = 0x00
        for (i in 0..15) {
            val message = interfaceWrite(byteArrayOf(0x06, 0x03, 0x01, address, 0x10))
            try {
                if (message.isValid) {
                    result += message.answer.copyOfRange(3, 0x13)
                    address = (address + 0x10).toByte()
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [readEeprom], $ex, $message"}
            }
        }
        return if (result.size == 256) result else null
    }

    suspend fun writeEeprom(dataToWrite: ByteArray): Boolean {
        if (dataToWrite.size != 256) return false
        for (i in 0 until dataToWrite.size / 8) {
            val data = byteArrayOf(0x07, 0x03, 0x00, 0x00, ((i * 8) / 2).toByte(), 0x04) + dataToWrite.copyOfRange( i * 8, 8 * (i + 1))
            interfaceWrite(data)
        }

        return reset()
    }
}
