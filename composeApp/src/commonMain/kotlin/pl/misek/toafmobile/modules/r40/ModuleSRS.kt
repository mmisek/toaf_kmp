package pl.misek.toafmobile.modules.r40

import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceCoding
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

class ModuleSRS( protocolKWP: ProtocolKWP?) : IModule, IModuleCoding, IModuleAdvanceCoding,IModuleVIN
{
    private val tag = "ModuleSRS"
    private var kwp: IProtocol? = null
    private val moduleAddress = ModuleAddress.MODULE_SRS.address

    init {
        if (protocolKWP != null) kwp = protocolKWP
    }

    override fun getKoinName() = KoinNames.ModuleR40Srs

    override fun getModuleProtocol(): IProtocol {
        return kwp!!
    }

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol
    }

    override fun getModuleName() = "SRS"

    override fun getModuleAddress(): ModuleAddress {
        return ModuleAddress.MODULE_SRS
    }

    override fun getImageResource() = Res.drawable.module_srs
    
    override fun getModuleDescription() = Res.string.module_description_srs

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        if (securityAccess05()) {
            val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x80.toByte()))
            if (message.isValid) {
                info = ModuleInfo()
                val answer = message.answer
                info.id1 = String(answer.copyOfRange(6, 16))
                info.hw = (answer[16].toInt() and 0xFF).toByte().toHexString()
                info.ci = (answer[17].toInt() and 0xFF).toByte().toHexString()
                info.di = (answer[18].toInt() and 0xFF).toByte().toHexString()
                info.sw = (answer[26].toInt() and 0xFF).toByte().toHexString()
                info.producer = ModulesFunction.getProducer(answer[25].toInt())
                info.date = getProgrammingDate().toString()
                info.serial = getSerialNumber().toString()
            }
        }
        reset()
        return info
    }

    private suspend fun getProgrammingDate(): String? {
        if (securityAccess05()) {
            val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x99.toByte()))
            if (message.isValid) {
                reset()
                return String.format(
                    "%02d/%02d/%02d%02d",
                    message.answer[6], message.answer[7], message.answer[8], message.answer[9]
                )
            }
        }
        return null
    }

    private suspend fun getSerialNumber(): String? {
        if (securityAccess05()) {
            val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x89.toByte()))
            if (message.isValid) {
                reset()
                return String(message.answer.copyOfRange(6, 16))
            }
        }
        return null
    }

    override suspend fun getVIN(): String? {
        if (securityAccess05()) {
            val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x90.toByte()))
            if (message.isValid) {
                //Reset();
                return String(message.answer.copyOfRange(6, 23))
            }
        }
        return null
    }

    override suspend fun getOdometer(): String? {
        return null
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        if (securityAccess05()) {
            val message = kwp!!.write(moduleAddress, 0x18.toByte(), byteArrayOf(0x00, 0x00), 500)
            if (message.isValid) {
                for (i in 0 until (message.answer[5].toInt() and 0xFF)) {
                    val errorCode =
                        ((message.answer[6 + 3 * i].toInt() and 0xFF) shl 8) + (message.answer[7 + 3 * i].toInt() and 0xFF)
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    if ((message.answer[8 + 3 * i].toInt() and 0xFF) == 0x00)
                        errorDescription.add(
                            Pair(Res.string.srs_error_detail_00,null))
                    else {
                        for (k in 0..7) {
                            val number = message.answer[8 + 3 * i].toInt() and (0x01 shl k)
                            if (number == (0x01 shl k)) {
                                ResourceMapper.stringResource(String.format("srs_error_detail_%02X",number))
                                    ?. let {errorDescription.add(Pair(it,null))}
                            }
                        }
                        if (result == null) result = ArrayList()
                        result.add(
                            ModuleError(
                                ModuleAddress.MODULE_SRS,
                                String.format("%04X", errorCode),
                                errorDescription
                            )
                        )
                    }
                }
            }
        }
        reset()
        return result
    }

    override suspend fun clearAllErrors(): Boolean {
        if (securityAccess05()) {
            val message = kwp!!.write(moduleAddress, 0x14.toByte(), byteArrayOf(0x00, 0x00), 1500)
            reset()
            return message.isValid
        }
        return false
    }

    override suspend fun reset(): Boolean {
        return kwp!!.write(moduleAddress, 0x11.toByte(), byteArrayOf(0x01)).isValid
    }

    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        if (securityAccess05()) {
            val message = kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x04), 500)
            if (message.isValid) {
                result.add(message.answer.copyOfRange(6, 10))
                reset()
                return result
            }
        }
        return null
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        if (securityAccess05()) {
            val data = byteArrayOf(0x17)+ dataToSave[0]+ dataToSave[0]
            val message = kwp!!.write(moduleAddress, 0x3B.toByte(), data, 500)
            reset()
            return message.isValid
        }
        return false
    }

    private suspend fun testerPresent(): Boolean {
        return kwp!!.write(moduleAddress, 0x3E.toByte(), byteArrayOf(), 300).isValid
    }

    private suspend fun securityAccess05(): Boolean {
        val level = 0x05.toByte()
        if (testerPresent()) {
            var message = kwp!!.write(moduleAddress, 0x10.toByte(), byteArrayOf(0x85.toByte()))
            if (message.isValid) {
                message = kwp!!.write(moduleAddress, 0x27.toByte(), byteArrayOf(level), 500)
                if (message.isValid) {
                    val code =
                        ((message.answer[6].toInt() and 0xFF) shl 8) + (message.answer[7].toInt() and 0xFF)
                    if (code == 0x0000) return true
                    else {
                        val seed = securityAccessCalculator(code)
                        val data = byteArrayOf(
                            (level + 1).toByte(),
                            ((seed and 0xFF00) shr 8).toByte(), (seed and 0xFF).toByte()
                        )
                        return kwp!!.write(moduleAddress, 0x27.toByte(), data).isValid
                    }
                }
            }
        }
        return false
    }

    private fun securityAccessCalculator(inKey: Int): Int {
        var key = inKey
        var n = key shr 8
        n = n xor 0x57
        n = n and 0x07
        n += 3
        for (i in 0 until n) {
            var x = 0
            if ((key and 0x2000) == 0x2000) x = x xor 1
            if ((key and 0x1000) == 0x1000) x = x xor 1
            if ((key and 0x0020) == 0x0020) x = x xor 1
            if ((key and 0x0004) == 0x0004) x = x xor 1
            if ((key and 0x0001) == 0x0001) x = x xor 1
            key = key shl 1
            key = key and 0xFFFF
            key = key xor x
        }
        return key
    }
    override fun getVinLength(): Int = 17
    override suspend fun writeVIN(newVin: String): Boolean {
        try {
            if (securityAccess05()) {
                return kwp!!.write(
                    moduleAddress,
                    0x3B.toByte(),
                    byteArrayOf(0x90.toByte())+ newVin.toByteArray()
                ).isValid
            }
        } catch (ex: Exception) {
            println("$tag : ${ex.toString()}")
        }
        return false
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.R40)
    }
}
