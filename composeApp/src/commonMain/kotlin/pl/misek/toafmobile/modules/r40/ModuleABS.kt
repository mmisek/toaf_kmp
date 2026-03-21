package pl.misek.toafmobile.modules.r40

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
import toafmobile.composeapp.generated.resources.*

class ModuleABS( protocolDS2: ProtocolDS2?) :ModuleDS2(protocolDS2, ModuleAddress.MODULE_ABS, listOf(CarType.R40)), IModuleLiveData
{
    override fun getKoinName() = KoinNames.ModuleR40Abs

    override fun getImageResource() = Res.drawable.module_abs

    override fun getModuleDescription() = Res.string.module_description_abs

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        getIdentification()
        val message =  interfaceWrite(byteArrayOf(0x04, 0x01))
        if (message.isValid && message.answer.size > 3) {
            result = ArrayList()
            for (i in 0 until (message.answer[3].toInt() and 0xFF)) {
                val errorCode = String.format("%02X", message.answer[4 + 4 * i])
                val errorDescription: MutableList<Pair<StringResource,Any?>> = ArrayList()
                errorDescription.add(ResourceHelper.getErrorDescriptionCount(message.answer[6 + 4 * i].toInt() and 0xFF))
                errorDescription.add(ResourceHelper.getErrorDescriptionSpeed(
                    ((message.answer[7 + 4 * i].toInt() and 0xFF) shl 8) + 0.0625 * (message.answer[8 + 4 * i].toInt() and 0xFF)))
                if ((message.answer[5 + 4 * i].toInt() and 0x10) == 0x10)
                    errorDescription.add(Pair(Res.string.abs_error_detail_00,null))
                else
                    errorDescription.add(Pair(Res.string.abs_error_detail_01, null))
                if ((message.answer[5 + 4 * i].toInt() and 0x20) == 0x20)
                    errorDescription.add(Pair(Res.string.abs_error_detail_02, null))
                else
                    errorDescription.add(Pair(Res.string.abs_error_detail_03,null))
                if ((message.answer[5 + 4 * i].toInt() and 0x40) == 0x40) errorDescription.add(
                    Pair(Res.string.abs_error_detail_04, null))
                else
                    errorDescription.add(Pair(Res.string.abs_error_detail_05,null))
                if ((message.answer[5 + 4 * i].toInt() and 0x80) == 0x80) errorDescription.add(
                    Pair(Res.string.abs_error_detail_06,null))
                else
                    errorDescription.add(Pair(Res.string.abs_error_detail_07,null))
                result.add(ModuleError(ModuleAddress.MODULE_ABS, errorCode, errorDescription))
            }
        }
        return result
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        if (interfaceWrite(byteArrayOf(0x00)).isValid) {
            val message = interfaceWrite(byteArrayOf(0x0B))
            if (message.isValid) {
                val res = message.answer.copyOfRange( 3, message.answer.size - 1)

                var i = 0
                while (i < 8) {
                    val a = res[i]
                    val b = res[i + 1]
                    res[i] = b
                    res[i + 1] = a
                    i += 2
                }
                val c = res[0x0E]
                val d = res[0x0F]
                res[0x0E] = d
                res[0x0F] = c

                result[0x00] = res
                return result
            } else return null
        } else return null
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) = true
}
