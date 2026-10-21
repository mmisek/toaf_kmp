package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.r40.ModuleRadio
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_radio
import toafmobile.composeapp.generated.resources.module_radio
import kotlinx.coroutines.CancellationException

class ModuleRadio(protocolDS2: ProtocolDS2?) :
    ModuleRadio(protocolDS2, listOf(CarType.L322)),
    IModuleCoding
{
    private val tag = "ModuleRadio_L322"
    override fun getKoinName() = KoinNames.ModuleL322Radio

    override fun getImageResource() = Res.drawable.module_radio

    override fun getModuleDescription() = Res.string.module_description_radio

    override suspend fun readCoding(): List<ByteArray> {
        val result: MutableList<ByteArray> = ArrayList()
        val message = interfaceWrite(byteArrayOf(0x08), 300)
        try {
            if (message.isValid) {
                result.add(message.answer.copyOfRange(3, message.answer.size - 1))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch ( ex : Exception)
        {
            Logger.e {"$tag [readCoding], $ex, $message"}
        }
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        val data = byteArrayOf(0x09) + dataToSave[0]
        return interfaceWrite(data).isValid
    }
}
