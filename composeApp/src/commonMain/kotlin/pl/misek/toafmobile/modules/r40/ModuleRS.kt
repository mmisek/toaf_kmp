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
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_rs
import toafmobile.composeapp.generated.resources.module_rs
import kotlinx.coroutines.CancellationException

private const val tag = "ModuleRS"
class ModuleRS( protocolDS2: ProtocolDS2?) : ModuleDS2( protocolDS2, ModuleAddress.MODULE_RS, listOf(CarType.R40)), IModuleControl, IModuleLiveData {

    override fun getKoinName() = KoinNames.ModuleR40Rs

    override fun getImageResource() = Res.drawable.module_rs

    override fun getModuleDescription() = Res.string.module_description_rs

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = mutableMapOf()
        val message = interfaceWrite(byteArrayOf(0x1B))
        try {
            if (message.isValid) {
                result[0x00] = message.answer.copyOfRange(3, message.answer.size)
                return result
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readLiveData], $ex, $message"}
        }
        return null
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        return controlElement.block == 0x00 && moduleInit()
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) = true

    override suspend fun getAllErrors(): List<ModuleError> {
        val result: MutableList<ModuleError> = mutableListOf()
        val message = interfaceWrite(byteArrayOf(0x04))
        try {
            if (message.isValid) {
                if (message.answer[3].toInt() != 0x00) {
                    val errorCode = String.format("%02X", message.answer[3])
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[5].toInt() and 0xFF)))
                    result.add(ModuleError(ModuleAddress.MODULE_RS, errorCode, errorDescription))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getAllErros], $ex, $message"}
        }
        return result
    }

    private suspend fun moduleInit(): Boolean {
        val tab = byteArrayOf(0x01, 0x04, 0x05)
        for (b in tab) {
            if (!interfaceWrite(byteArrayOf(0x07, 0x01, b, 0xFF.toByte())).isValid) return false
        }
        return true
    }
}
