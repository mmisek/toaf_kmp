package pl.misek.toafmobile.modules.r40

import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_ac
import toafmobile.composeapp.generated.resources.module_description_atc

class ModuleATC( protocolDS2: ProtocolDS2?) : ModuleDS2(protocolDS2, ModuleAddress.MODULE_ATC, listOf(CarType.R40)), IModuleControl, IModuleLiveData
{
    override fun getKoinName() = KoinNames.ModuleR40Atc

    override fun getImageResource() = Res.drawable.module_ac

    override fun getModuleDescription()= Res.string.module_description_atc

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val message = interfaceWrite(byteArrayOf(0x0B, 0x00))
        if (message.isValid) {
            result[0x00] = message.answer.copyOfRange(3, message.answer.size)
            return result
        }
        return null
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        return interfaceWrite(
            byteArrayOf(
                0x1C.toByte(),
                0x01.toByte(),
                controlElement.block.toByte(),
                controlElement.value.toByte()
            )
        ).isValid
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) = true

    override suspend fun getIdentification(): ModuleInfo? {
        var moduleInfo: ModuleInfo? = null
        val message = interfaceWrite(byteArrayOf(0x00))
        if (message.isValid) {
            moduleInfo = ModuleInfo()
            moduleInfo.sw = String.format("%02X", message.answer[4])
            moduleInfo.producer = ModulesFunction.getProducer(message.answer[3].toInt())
        }
        return moduleInfo
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        val message = interfaceWrite(byteArrayOf(0x04, 0x00))
        if (message.isValid) {
            for (i in 3 until message.answer.size - 1) {
                if (message.answer[i].toInt() != 0x00) {
                    val errorCode = String.format("%02X", message.answer[i])
                    if (result == null) result = ArrayList()
                    result.add(ModuleError(ModuleAddress.MODULE_ATC, errorCode, ArrayList()))
                }
            }
        }

        return result
    }
}
