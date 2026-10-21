package pl.misek.toafmobile.modules.l314

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolKWPStar
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.kombi_error_status_present_not_stored
import toafmobile.composeapp.generated.resources.kombi_error_status_present_stored
import toafmobile.composeapp.generated.resources.kombi_error_status_stored_not_present
import toafmobile.composeapp.generated.resources.kombi_error_status_test_not_complete
import toafmobile.composeapp.generated.resources.kombi_error_status_warning_lamp
import toafmobile.composeapp.generated.resources.module_description_kombi
import toafmobile.composeapp.generated.resources.module_ipk
import kotlin.time.Duration.Companion.milliseconds

class ModuleKOMBI(private var kwp: ProtocolKWPStar) : IModule, IModuleVIN, IModuleCoding {
    private val tag = "ModuleKOMBI"

    private val MODULE_ADDRESS = ModuleAddress.MODULE_KOMBI.address

    private val codingLocalIds = listOf(0x02, 0x03, 0x09, 0x0A, 0x0B)

    override fun getModuleProtocol() = kwp

    override fun getKoinName() = KoinNames.ModuleL20Kombi

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol as ProtocolKWPStar
    }

    override fun getModuleName(): String = "KOMBI"

    override fun getModuleAddress(): ModuleAddress = ModuleAddress.MODULE_KOMBI

    override fun getImageResource() = Res.drawable.module_ipk

    override fun getModuleDescription() = Res.string.module_description_kombi

    override fun getSupportedCarTypes(): List<CarType> = listOf(CarType.L314)

    private suspend fun moduleInit(): Boolean {
        if (request(0x3E, byteArrayOf()) != null)
            return true
        repeat(5) {
            if (request(0x81, byteArrayOf()) != null)
                return true
            delay(1000.milliseconds)
        }
        return false
    }

    private suspend fun request(sid: Int, data: ByteArray, timeout: Long = 50): ByteArray? {
        val message = kwp.write(MODULE_ADDRESS, sid.toByte(), data, timeout)
        return responseData(message, sid)
    }

    private fun responseData(message: OBDMessage, sid: Int): ByteArray? {
        try {
            val answer = message.answer
            if (answer.size < 4) return null
            val formatLength = answer[0].toInt() and 0x3F
            val headerLength = if (formatLength == 0) 4 else 3
            val length = if (formatLength == 0) answer[3].toInt() and 0xFF else formatLength
            if (length == 0 || answer.size < headerLength + length + 1) return null
            var checksum = 0
            for (i in 0 until headerLength + length) checksum += answer[i].toInt()
            if ((checksum and 0xFF) != (answer[headerLength + length].toInt() and 0xFF)) return null
            val data = answer.copyOfRange(headerLength, headerLength + length)
            return if ((data[0].toInt() and 0xFF) == ((sid + 0x40) and 0xFF)) data else null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [responseData], $ex, $message" }
            return null
        }
    }

    private fun bcd(value: Byte) = String.format("%02X", value.toInt() and 0xFF)

    override suspend fun getIdentification(): ModuleInfo? {
        if (!moduleInit()) return null
        val data = request(0x1A, byteArrayOf(0x80.toByte())) ?: return null
        try {
            val info = ModuleInfo()
            info.id1 = String(data.copyOfRange(2, 12)).trim()
            info.hw = bcd(data[0x0C])
            info.ci = bcd(data[0x0D])
            info.di = bcd(data[0x0E])
            info.date = ResourceHelper.byteArrayToDate(data.copyOfRange(0x11, 0x15))
            info.producer = ModulesFunction.getProducer(data[0x15].toInt() and 0xFF)
            info.sw = bcd(data[0x16])
            info.bi = bcd(data[0x17])
            return info
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getIdentification], $ex" }
            return null
        }
    }

    override suspend fun getVIN(): String? {
        if (!moduleInit()) return null
        val data = request(0x1A, byteArrayOf(0x90.toByte())) ?: return null
        return if (data.size >= 19) String(data.copyOfRange(2, 19)) else null
    }

    override fun getVinLength(): Int = 17

    override suspend fun writeVIN(newVin: String): Boolean {
        val vin = newVin.uppercase()
        if (vin.length != getVinLength() || !vin.all { it in '0'..'9' || it in 'A'..'Z' }) return false
        if (!moduleInit() || !securityAccess(1)) return false
        if (request(0x3B, byteArrayOf(0x12) + vin.encodeToByteArray()) == null) return false
        return getVIN() == vin
    }

    override suspend fun getOdometer(): String? = null

    override suspend fun getAllErrors(): List<ModuleError>? {
        if (!moduleInit()) return null
        val data = request(0x18, byteArrayOf(0x02, 0xFF.toByte(), 0xFF.toByte())) ?: return null
        val result: MutableList<ModuleError> = ArrayList()
        try {
            val count = data[1].toInt() and 0xFF
            for (i in 0 until count) {
                val offset = 2 + 3 * i
                if (offset + 2 >= data.size) break
                val errorCode = String.format("%02X%02X", data[offset].toInt() and 0xFF, data[offset + 1].toInt() and 0xFF)
                result.add(ModuleError(ModuleAddress.MODULE_KOMBI, errorCode, errorStatusDescription(data[offset + 2].toInt() and 0xFF)))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getAllErrors], $ex" }
        }
        return result
    }

    private fun errorStatusDescription(status: Int): MutableList<Pair<StringResource, Any?>> {
        val result: MutableList<Pair<StringResource, Any?>> = ArrayList()
        when (status and 0x60) {
            0x20 -> result.add(Pair(Res.string.kombi_error_status_stored_not_present, null))
            0x40 -> result.add(Pair(Res.string.kombi_error_status_present_not_stored, null))
            0x60 -> result.add(Pair(Res.string.kombi_error_status_present_stored, null))
        }
        if ((status and 0x10) == 0x10) result.add(Pair(Res.string.kombi_error_status_test_not_complete, null))
        if ((status and 0x80) == 0x80) result.add(Pair(Res.string.kombi_error_status_warning_lamp, null))
        return result
    }

    override suspend fun clearAllErrors(): Boolean {
        if (!moduleInit()) return false
        return request(0x14, byteArrayOf(0xFF.toByte(), 0xFF.toByte())) != null
    }

    override suspend fun reset(): Boolean = false

    private suspend fun securityAccess(level: Int): Boolean {
        request(0x10, byteArrayOf(0x81.toByte()))
        val mode = if (level == 1) 0x01 else 0x03
        val seedData = request(0x27, byteArrayOf(mode.toByte())) ?: return false
        if (seedData.size < 4) return false
        val seedHi = seedData[2].toInt() and 0xFF
        val seedLo = seedData[3].toInt() and 0xFF
        var key = (seedHi shl 8) or seedLo
        if (key == 0) return true
        var rounds = if (level == 1) ((seedHi xor 0x7D) and 0x07) + 1 else ((seedLo xor 0x7D) and 0x35) + 1
        while (rounds > 0) {
            val newBit = ((key shr 15) xor (key shr 9) xor (key shr 8) xor (key shr 3) xor key) and 0x01
            key = ((key shl 1) and 0xFFFE) or newBit
            rounds--
        }
        return request(0x27, byteArrayOf((mode + 1).toByte(), (key shr 8).toByte(), (key and 0xFF).toByte())) != null
    }

    /** C_C_LESEN: blok na kazdy lokalny identyfikator (21 LID), dane od bajtu 2 */
    override suspend fun readCoding(): List<ByteArray>? {
        if (!moduleInit()) return null
        val result: MutableList<ByteArray> = ArrayList()
        for (localId in codingLocalIds) {
            val data = request(0x21, byteArrayOf(localId.toByte()), 300) ?: return null
            result.add(data.copyOfRange(2, data.size))
        }
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        if (!CODING_WRITE_ENABLED) return false
        if (dataToSave.size != codingLocalIds.size) return false
        val current = readCoding() ?: return false
        if (!securityAccess(1)) return false
        for ((index, localId) in codingLocalIds.withIndex()) {
            val block = dataToSave[index]
            if (block.contentEquals(current[index])) continue
            if (request(0x3B, byteArrayOf(localId.toByte()) + block) == null) return false
            val verify = request(0x21, byteArrayOf(localId.toByte())) ?: return false
            if (!verify.copyOfRange(2, verify.size).contentEquals(block)) return false
        }
        return true
    }

    private companion object {
        const val CODING_WRITE_ENABLED = false
    }
}
