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
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_mfl
import toafmobile.composeapp.generated.resources.module_mfl

class ModuleMFL( protocolDS2: ProtocolDS2?) :
    ModuleDS2( protocolDS2, ModuleAddress.MODULE_MFL, listOf(CarType.L322)),
    IModuleLiveData {

    private val tag = "ModuleMFL"

    override fun getKoinName() = KoinNames.ModuleL322Mfl

    override fun getImageResource() = Res.drawable.module_mfl

    override fun getModuleDescription() = Res.string.module_description_mfl

    override suspend fun getAllErrors(): List<ModuleError>? {
        //[50] [05] [04] [02] [53] : [50] [05] [04] [02] [53] [50] [04] [FF] [AB]
        getIdentification()
        var result: MutableList<ModuleError>? = null
        for (x in 1..2) {
            val message = interfaceWrite(byteArrayOf(0x04, x.toByte()))
            try {
                if (message.isValid && message.length > 5) {
                    result = ArrayList()
                    for (i in 0 until (message.answer[3].toInt() and 0xFF)) {
                        val errorCode = String.format("%02X", message.answer[4 + 2 * i])
                        val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                        errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[5 + 2 * i].toInt() and 0xFF) and 0x1F))
                        errorDescription.add(ResourceHelper.getErrorDescriptionIsPresent((message.answer[5 + 2 * i].toInt() and 0x20) == 0x20))
                        result.add(
                            ModuleError(
                                ModuleAddress.MODULE_MFL,
                                errorCode,
                                errorDescription
                            )
                        )
                    }
                }
            } catch ( ex : Exception)
            {
                Logger.e {"$tag [getAllErrors], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val message = interfaceWrite(byteArrayOf(0x0B), 500)
        try {
            if (message.isValid) result[0] =
                message.answer.copyOfRange(3, message.answer.size)
            else
                return null
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readLiveData], $ex, $message"}
        }
        return result
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean  = true
}
