package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.mfl_error_active
import toafmobile.composeapp.generated.resources.mfl_error_not_active
import toafmobile.composeapp.generated.resources.module_description_mfl
import toafmobile.composeapp.generated.resources.module_mfl
import kotlinx.coroutines.CancellationException

class ModuleMFL( protocolDS2: ProtocolDS2?) :
    ModuleDS2( protocolDS2, ModuleAddress.MODULE_MFL, listOf(CarType.L322)),
    IModuleLiveData {

    private val tag = "ModuleMFL"

    override fun getKoinName() = KoinNames.ModuleL322Mfl

    override fun getImageResource() = Res.drawable.module_mfl

    override fun getModuleDescription() = Res.string.module_description_mfl

    // Wg SGBD FS_LESEN: 04 01 -> [3] liczba bledow, rekordy 2-bajtowe od [4] (max 7 w bloku):
    // kod + (czestosc & 0x1F, bit 0x20 = blad aktywny); 04 02 tylko gdy bledow wiecej niz 7.
    override suspend fun getAllErrors(): List<ModuleError>? {
        getIdentification()
        val first = interfaceWrite(byteArrayOf(0x04, 0x01))
        if (!first.isValid) return null
        val result: MutableList<ModuleError> = ArrayList()
        val count = first.answer[3].toInt() and 0xFF
        parseErrors(first, minOf(count, 7), result)
        if (count > 7) {
            val second = interfaceWrite(byteArrayOf(0x04, 0x02))
            if (second.isValid)
                parseErrors(second, count - 7, result)
        }
        return result
    }

    private fun parseErrors(message: OBDMessage, count: Int, result: MutableList<ModuleError>) {
        try {
            for (i in 0 until count) {
                if (message.answer.size <= 5 + 2 * i) break
                val errorCode = String.format("%02X", message.answer[4 + 2 * i])
                val status = message.answer[5 + 2 * i].toInt() and 0xFF
                val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                errorDescription.add(ResourceHelper.getErrorDescriptionCount(status and 0x1F))
                errorDescription.add(
                    Pair(if ((status and 0x20) == 0x20) Res.string.mfl_error_active else Res.string.mfl_error_not_active, null)
                )
                result.add(ModuleError(ModuleAddress.MODULE_MFL, errorCode, errorDescription))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getAllErrors], $ex, $message" }
        }
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val message = interfaceWrite(byteArrayOf(0x0B), 500)
        try {
            // klucz 0x01 zgodnie z "block" w mfl_live_data.json
            if (message.isValid) result[0x01] =
                message.answer.copyOfRange(3, message.answer.size)
            else
                return null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readLiveData], $ex, $message"}
        }
        return result
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean  = true
}
