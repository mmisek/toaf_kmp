package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_navi
import toafmobile.composeapp.generated.resources.module_navigation
import kotlinx.coroutines.CancellationException
import pl.misek.toafmobile.modules.enums.ModuleFeature
import pl.misek.toafmobile.modules.interfaces.IModuleVIN

private const val tag = "ModuleNavMk3"
class ModuleNavMk3( protocolDS2: ProtocolDS2?) :
    ModuleDS2( protocolDS2, ModuleAddress.MODULE_NAVMK3, listOf(CarType.R40, CarType.L322)),
    IModuleCoding, IModuleLiveData, IModuleVIN {

    override fun getKoinName() = KoinNames.ModuleR40NavMk3

    override fun getImageResource() = Res.drawable.module_navigation
    override fun getModuleDescription() = Res.string.module_description_navi

    override suspend fun getIdentification(): ModuleInfo? {
        val message = interfaceWrite(byteArrayOf(0x00), 200)
        if (message.isValid) {
            try {
                val moduleInfo = ModuleInfo()
                val answer = message.answer
                moduleInfo.id1 = ModulesFunction.decodeModuleID(answer.copyOfRange(3, 10))
                moduleInfo.hw = String(answer.copyOfRange(10, 12))
                moduleInfo.ci = String(answer.copyOfRange(12, 14))
                moduleInfo.di = String(answer.copyOfRange(14, 16))
                moduleInfo.bi = String(answer.copyOfRange(16, 18))
                moduleInfo.date =
                    String(answer.copyOfRange(18, 20)) + "/" + String(answer.copyOfRange(20, 22))
                //moduleInfo.Serial = new String(message.answer.copyOfRange(22,32));
                moduleInfo.sw = String(answer.copyOfRange(32, 34))
                moduleInfo.producer = ModulesFunction.getProducer(0x56)
                return moduleInfo
            }
            catch (ex: CancellationException) {
                throw ex
            }
            catch (ex : Exception)
            {
                Logger.e {"$tag [getIdentification] : $ex, $message"}
            }
        }
        return null
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        do {
            val message = interfaceWrite(byteArrayOf(0x0B),200)
            if (message.isValid) {
                result[0x00] = message.answer.copyOfRange(3, message.answer.size)
                return result
            }
        } while (message.answerCode != 0xA1.toByte())

        return null
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) : Boolean
    {
        return interfaceWrite(byteArrayOf(0x00)).isValid
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        getIdentification()
        val result = mutableListOf<ModuleError>()
        readErrorRecords(byteArrayOf(0x04, 0x01), 3, result)
        readErrorRecords(byteArrayOf(0x14, 0x01), 4, result)
        return result.ifEmpty { null }
    }

    private suspend fun readErrorRecords(command: ByteArray, recordSize: Int, result: MutableList<ModuleError>) {
        val message = interfaceWrite(command, 500)
        if (!message.isValid) return
        try {
            val declaredCount = message.answer[3].toInt() and 0xFF
            val availableCount = ((message.answer.size - 4) / recordSize).coerceAtLeast(0)
            for (i in 0 until minOf(declaredCount, availableCount)) {
                val base = 4 + recordSize * i
                val errorCode = String.format("%02X", message.answer[base])
                val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                errorDescription.add(ResourceHelper.getErrorDescriptionCount(message.answer[base + 2].toInt() and 0xFF))
                errorDescription.add(ResourceHelper.getErrorDescriptionIsPresent((message.answer[base + 1].toInt() and 0x40) == 0x40))
                result.add(ModuleError(ModuleAddress.MODULE_NAVMK3, errorCode, errorDescription))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e("$tag [readErrorRecords]: $ex, $message")
        }
    }

    override suspend fun getVIN(): String? {
        val data = readCoding()
        val codingBytes = data?.firstOrNull()
        if (codingBytes == null || codingBytes.size < 13) {
            Logger.w {"$tag [getVIN]: coding data too short or missing, $data"}
            return null
        }
        return try {
            val vin = String(codingBytes.copyOfRange(4, 4+7))
            if (vin.all { it.isLetterOrDigit() }) vin else {
                Logger.w {"$tag [getVIN]: VIN niezakodowany/niepoprawny"}
                null
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getVIN], $ex,$data"}
            null
        }
    }
    override suspend fun writeVIN(newVin: String): Boolean {
        val data = readCoding()
        val codingBytes = data?.firstOrNull()
        if (codingBytes == null || codingBytes.size < 13) {
            Logger.w {"$tag [getVIN]: coding data too short or missing, $data"}
            return false
        }
        for ((i, element) in newVin.withIndex())
        {
            codingBytes[4+i] = element.code.toByte()
        }
        return writeCoding(listOf(codingBytes))
    }

    override fun getVinLength() = 7

    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        val message = interfaceWriteWithAddress(
            ModuleAddress.MODULE_GT.address,
            byteArrayOf(0x08, 0x00, 0x00, 0x00), 300
        )
        try {
            if (message.isValid) result.add(
                message.answer.copyOfRange(3,message.answer.size - 1))
            else return null
        }
        catch (ex: CancellationException) {
            throw ex
        }
        catch (ex : Exception)
        {
            Logger.e {"$tag [readCoding]: $ex, $message"}
        }

        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        interfaceWriteWithAddress(ModuleAddress.MODULE_GT.address, byteArrayOf(0x00), 500)
        interfaceWrite(byteArrayOf(0x00), 500)
        val data = byteArrayOf(0x09, 0x00, 0x00) + dataToSave[0]
        return interfaceWriteWithAddress(ModuleAddress.MODULE_GT.address, data, 500).isValid
    }

    override fun getNewFeatures(): Set<ModuleFeature> {
        return super.getNewFeatures() + ModuleFeature.AdvanceCoding
    }
}
