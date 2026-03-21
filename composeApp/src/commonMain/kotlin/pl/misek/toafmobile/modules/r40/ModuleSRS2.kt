package pl.misek.toafmobile.modules.r40

import kotlinx.coroutines.delay
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceCoding
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWPStar
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

class ModuleSRS2( protocolKWPStar: ProtocolKWPStar?) : IModule, IModuleCoding, IModuleVIN,
    IModuleAdvanceCoding {
    private var kwp: ProtocolKWPStar? = null
    private val moduleAddress = ModuleAddress.MODULE_SRS.address

    init {
        if (protocolKWPStar != null) kwp = protocolKWPStar
    }
    override fun getKoinName() = KoinNames.ModuleR40Srs2

    override fun getModuleProtocol() = kwp!!

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol as ProtocolKWPStar
    }

    private suspend fun testerPresent(): Boolean {
        return kwp!!.write(moduleAddress, 0x81.toByte(), byteArrayOf(), 250).isValid
    }

    override fun getModuleName() = "SRS"

    override fun getModuleAddress() =  ModuleAddress.MODULE_SRS2

    override fun getImageResource() = Res.drawable.module_srs

    override fun getModuleDescription() = Res.string.module_description_srs2

    override suspend fun getIdentification(): ModuleInfo? {
        val info = ModuleInfo()
        if (testerPresent()) {
            val message =
                kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x80.toByte()), 200)
            if (message.isValid) {
                info.id1 = "YWC001160"
                info.hw = String(message.answer.copyOfRange( 5, 10))
                info.ci = "02"
                info.di = (message.answer[18].toInt() and 0xFF).toByte().toHexString()
                info.producer = "Autoliv"
            }
            info.serial = getSerialNumber()
            return info
        }
        return null
    }

    private suspend fun getSerialNumber(): String {
        val message = kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x81.toByte()), 200)
        if (message.isValid) {
            return String.format(
                "%02x%02X%02X%02X",
                message.answer[5].toInt() and 0xFF,
                message.answer[6].toInt() and 0xFF,
                message.answer[7].toInt() and 0xFF,
                message.answer[8].toInt() and 0xFF
            )
        }
        return ""
    }

    override suspend fun getVIN(): String? {
        delay(5000)
        if (testerPresent()) {
            val message =
                kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x8F.toByte()), 200)
            if (message.isValid) {
                return String(message.answer.copyOfRange(5, 22))
            }
        }
        return null
    }

    override suspend fun getOdometer(): String? {
        return null
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        if (testerPresent()) {
            val message =
                kwp!!.write(moduleAddress, 0x17.toByte(), byteArrayOf(0xFF.toByte(), 0x00), 200)
            if (message.isValid) {
                var n = message.answer[4].toInt()
                if (n > 12) n = 12
                for (i in 0 until n) {
                    val errorCode =
                        ((message.answer[5 + 2 * i].toInt() and 0x0F) shl 8) + (message.answer[6 + 2 * i].toInt() and 0xFF)
                    val error = ModuleError(
                        ModuleAddress.MODULE_SRS2,
                        String.format("%03X", errorCode),
                        ArrayList()
                    )
                    if (error.description != null) {
                        if (result == null) result = ArrayList()
                        result.add(error)
                    }
                }
            }
        }
        return result
    }

    override suspend fun clearAllErrors(): Boolean {
        if (testerPresent()) {
            return kwp!!.write(
                moduleAddress,
                0x14.toByte(),
                byteArrayOf(0xFF.toByte(), 0x00),
                200
            ).isValid
        }
        return false
    }

    override suspend fun reset(): Boolean {
        return kwp!!.write(moduleAddress, 0x11.toByte(), byteArrayOf(0x01)).isValid
    }

    override suspend fun readCoding(): List<ByteArray>? {
        delay(3000)
        val result: MutableList<ByteArray> = ArrayList()
        if (testerPresent()) {
            delay(200)
            if (securityAccess()) {
                delay(200)
                val message =
                    kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0xD0.toByte()), 200)
                if (message.isValid) {
                    result.add(message.answer.copyOfRange(5, 10))
                    return result
                }
            }
        }
        return null
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        if (testerPresent()) {
            if (securityAccess()) {
                val data = byteArrayOf(0xD0.toByte()) + dataToSave[0]
                return kwp!!.write(moduleAddress, 0x3B.toByte(), data, 200).isValid
            }
        }
        return false
    }

    private suspend fun securityAccess(): Boolean {
        run {
            val message = kwp!!.write(moduleAddress, 0x27.toByte(), byteArrayOf(), 200)
            if (message.isValid) {
                val code =
                    ((message.answer[4].toInt() and 0xFF) shl 8) + (message.answer[5].toInt() and 0xFF)
                val seed = securityAccessCalculator(code)
                val data = byteArrayOf(((seed and 0xFF00) shr 8).toByte(), (seed and 0xFF).toByte())
                return kwp!!.write(moduleAddress, 0x27.toByte(), data, 200).isValid
            }
        }
        return false
    }

    private fun securityAccessCalculator(inKey: Int): Int {
        var key = inKey
        val n = ((((key and 0xFF00) shr 8) xor 0x53) and 0x1E) + 4
        for (i in 0 until n) {
            var x = 0
            if ((key and 0x0800) == 0x0800) x = x xor 1
            if ((key and 0x0020) == 0x0020) x = x xor 1
            if ((key and 0x0002) == 0x0002) x = x xor 1
            key = key shl 1
            key = key and 0xFFFE
            key = key xor x
        }
        return key
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.R40)
    }

    override fun getVinLength(): Int = 17

    override suspend fun writeVIN(newVin: String): Boolean {
        delay(3000)
        if (testerPresent()) {
            delay(200)
            if (securityAccess()) {
                delay(200)
                return kwp!!.write(
                    moduleAddress,
                    0x3B.toByte(),
                    byteArrayOf(0x8F.toByte()) + newVin.toByteArray()
                ).isValid
            }
        }
        return false
    }
}
