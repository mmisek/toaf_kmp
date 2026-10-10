package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_shd
import toafmobile.composeapp.generated.resources.module_sunroof
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val tag = "ModuleSHD"
class ModuleSHD(protocolDS2: ProtocolDS2?) : ModuleDS2( protocolDS2, ModuleAddress.MODULE_SHD, listOf(CarType.R40)), IModuleCoding, IModuleLiveData, IModuleControl
{
    override fun getKoinName() = KoinNames.ModuleR40Shd

    override fun getImageResource() = Res.drawable.module_sunroof

    override fun getModuleDescription() = Res.string.module_description_shd

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val message = interfaceWrite(byteArrayOf(0x0B))
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

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) = true

    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        val message = interfaceWrite(byteArrayOf(0x08))
        try {
            if (message.isValid) result.add(
                message.answer.copyOfRange(3,message.answer.size - 1))
            else return null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readCoding], $ex, $message"}
        }

        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        return interfaceWrite(
            arrayOf(
                byteArrayOf(0x09),
                dataToSave[0]
            )
        ).isValid
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        val message = interfaceWrite(byteArrayOf(0x04))
        try {
            if (message.isValid) {
                for (i in 0 until (message.answer[3].toInt() and 0xFF)) {
                    val errorCode = String.format("%02X", message.answer[4 + 2 * i])
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[5 + 2 * i].toInt() and 0x1F)))
                    // wg SGBD FS_LESEN: bit 0x20 (statyczny) w bajcie statusu, razem z czestoscia
                    errorDescription.add(ResourceHelper.getErrorDescriptionIsStatic((message.answer[5 + 2 * i].toInt() and 0x20) == 0x20))
                    if (result == null) result = ArrayList()
                    result.add(ModuleError(ModuleAddress.MODULE_SHD, errorCode, errorDescription))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch ( ex : Exception)
        {
            Logger.e {"$tag [getAllErrors], $ex, $message"}
        }
        return result
    }

    // Wg SGBD STEUERN_DIGITAL: 0C <nr bitu z tabeli BITS> <00/01>.
    // TIPP_H/Z/A (0x19/0x1B/0x1D) to impuls przycisku - po krotkiej chwili sygnal jest zwalniany.
    override suspend fun control(controlElement: ControlElement): Boolean {
        val result = interfaceWrite(byteArrayOf(0x0C, controlElement.block.toByte(), controlElement.value.toByte())).isValid
        if (result && controlElement.block in listOf(0x19, 0x1B, 0x1D) && controlElement.value != 0) {
            delay(300)
            interfaceWrite(byteArrayOf(0x0C, controlElement.block.toByte(), 0x00))
        }
        return result
    }

    private fun calculateCodingChecksum(data: List<ByteArray>): List<ByteArray> {
        var cs = 0x00
        for (i in data.indices) for (j in data[i].indices) cs = cs xor data[i][j].toInt()
        cs = cs xor data[9][data[9].size - 1].toInt()
        data[9][data[9].size - 1] = cs.toByte()
        return data
    }
}
