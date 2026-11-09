package pl.misek.toafmobile.modules.l314

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolKWPStar
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.kombi_error_status_present_not_stored
import toafmobile.composeapp.generated.resources.kombi_error_status_present_stored
import toafmobile.composeapp.generated.resources.kombi_error_status_stored_not_present
import toafmobile.composeapp.generated.resources.kombi_error_status_test_not_complete
import toafmobile.composeapp.generated.resources.kombi_error_status_warning_lamp
import toafmobile.composeapp.generated.resources.module_abs
import toafmobile.composeapp.generated.resources.module_description_asc
import kotlin.time.Duration.Companion.milliseconds

/**
 * ASC+T Freelandera L314 (Conti Teves MK25) - SGBD ASC_L22, KWP2000 10400 Bd, adres 0x29, tester 0xF7.
 * Zapytania z osobnym bajtem dlugosci (80 29 F7 len SID ...), START_COMMUNICATION (81) z dlugoscia w formacie.
 * EEPROM - brak w SGBD.
 */
class ModuleASC(private var kwp: ProtocolKWPStar) : IModule, IModuleLiveData, IModuleControl {
    private val tag = "ModuleASC"

    private val MODULE_ADDRESS = ModuleAddress.MODULE_ASC.address

    override fun getModuleProtocol() = kwp

    override fun getKoinName() = KoinNames.ModuleL20Asc

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol as ProtocolKWPStar
    }

    override fun getModuleName(): String = "ASC"

    override fun getModuleAddress(): ModuleAddress = ModuleAddress.MODULE_ASC

    override fun getImageResource() = Res.drawable.module_abs

    override fun getModuleDescription() = Res.string.module_description_asc

    override fun getSupportedCarTypes(): List<CarType> = listOf(CarType.L314)

    /** START_COMMUNICATION (81) lub DIAGNOSE_WEITER (3E), gdy sesja juz trwa */
    private suspend fun moduleInit(): Boolean {
        if (request(0x3E, byteArrayOf()) != null)
            return true
        repeat(5) {
            if (responseData(kwp.write(MODULE_ADDRESS, 0x81.toByte(), byteArrayOf(), 50), 0x81) != null)
                return true
            delay(1000.milliseconds)
        }
        return false
    }

    private suspend fun request(sid: Int, data: ByteArray, timeout: Long = 50): ByteArray? {
        val message = kwp.writeWithLengthByte(MODULE_ADDRESS, sid.toByte(), data, timeout)
        return responseData(message, sid)
    }

    /** Dane odpowiedzi od bajtu SID (SID + 0x40) - naglowek z dlugoscia w formacie lub w osobnym bajcie */
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

    /**
     * IDENT (1A 80): [2..5] numer BMW (BCD, bez pierwszej cyfry), 6 HW, 7 CI, 8 DI, 9 BI,
     * 10 tydzien, 11 rok, 12 dostawca, 13 SW (BCD)
     */
    override suspend fun getIdentification(): ModuleInfo? {
        if (!moduleInit()) return null
        val data = request(0x1A, byteArrayOf(0x80.toByte())) ?: return null
        try {
            val info = ModuleInfo()
            info.id1 = (2..5).joinToString("") { bcd(data[it]) }.substring(1)
            info.hw = bcd(data[6])
            info.ci = bcd(data[7])
            info.di = bcd(data[8])
            info.bi = bcd(data[9])
            info.date = "${bcd(data[10])}/${bcd(data[11])}"
            info.producer = ModulesFunction.getProducer(data[12].toInt() and 0xFF)
            info.sw = bcd(data[13])
            return info
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getIdentification], $ex" }
            return null
        }
    }

    override suspend fun getVIN(): String? = null

    override suspend fun getOdometer(): String? = null

    /** FS_LESEN (18 02 FF FF): [1] liczba bledow, rekordy [kod hi][kod lo][status] */
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
                result.add(ModuleError(ModuleAddress.MODULE_ASC, errorCode, errorStatusDescription(data[offset + 2].toInt() and 0xFF)))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getAllErrors], $ex" }
        }
        return result
    }

    /** Status DTC (FARTTEXTE): 10 - test niezakonczony, 60 - stan zapisu, 80 - lampka ostrzegawcza */
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

    /** FS_LOESCHEN (14 FF FF) */
    override suspend fun clearAllErrors(): Boolean {
        if (!moduleInit()) return false
        return request(0x14, byteArrayOf(0xFF.toByte(), 0xFF.toByte())) != null
    }

    override suspend fun reset(): Boolean = false

    /**
     * DIAG_MODE_SEED: tryb diagnostyczny Service (10 81, numer dostepu 3), seed 27 01, klucz 27 02.
     * Klucz = obrot seeda w prawo o numer dostepu XOR seed z odwrocona kolejnoscia bitow.
     */
    private suspend fun securityAccess(): Boolean {
        val accessNumber = 3
        if (request(0x10, byteArrayOf(0x81.toByte())) == null) return false
        val seedData = request(0x27, byteArrayOf((accessNumber - 2).toByte())) ?: return false
        if (seedData.size < 4) return false
        var seedHi = seedData[2].toInt() and 0xFF
        var seedLo = seedData[3].toInt() and 0xFF
        val seed = (seedHi shl 8) or seedLo
        val rotated = ((seed shl (16 - accessNumber)) or (seed ushr accessNumber)) and 0xFFFF
        var mirrored = 0
        for (i in 0..7) {
            if ((seedLo and 0x01) == 0x01) mirrored++
            seedLo = seedLo shr 1
            mirrored = mirrored shl 1
        }
        for (i in 0..7) {
            if ((seedHi and 0x01) == 0x01) mirrored++
            seedHi = seedHi shr 1
            if (i <= 6) mirrored = mirrored shl 1
        }
        val key = (rotated xor mirrored) and 0xFFFF
        return request(0x27, byteArrayOf((accessNumber - 1).toByte(), (key shr 8).toByte(), (key and 0xFF).toByte())) != null
    }

    /** STATUS_RADGESCHWINDIGKEIT (21 07): VL, VR, HL, HR po 2 bajty [0.01 km/h], 0x0044 = 0 jak w SGBD */
    override suspend fun readLiveData(): Map<Int, Any>? {
        val data = request(0x21, byteArrayOf(0x07)) ?: return null
        try {
            val speeds = data.copyOfRange(2, 10)
            for (i in 0..3) {
                if ((speeds[2 * i].toInt() and 0xFF) == 0x00 && (speeds[2 * i + 1].toInt() and 0xFF) == 0x44)
                    speeds[2 * i + 1] = 0x00
            }
            return mapOf(1 to speeds)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readLiveData], $ex" }
            return null
        }
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean = moduleInit()

    override suspend fun getModuleVoltage() = null

    /*
     * STEUERN_DIGITAL (30 10 07 b0 b1) - tabela STEUERN:
     * b0: 01 EVVL, 02 AVVL, 04 EVVR, 08 AVVR, 10 EVHL, 20 AVHL, 40 EVHR, 80 AVHR
     * b1: 01 SV1, 02 SV2, 04 EUV1, 08 EUV2, 10 pompa, 20 BLS
     * Blok 1 - impuls jednego elementu (1 s, potem 00 00), blok 2 - odpowietrzanie (NA_ENTLUEFTUNG_LI/RE).
     */
    override suspend fun control(controlElement: ControlElement): Boolean {
        if (!moduleInit() || !securityAccess()) return false
        return when (controlElement.block) {
            0x01 -> {
                val outputs = byteArrayOf(0x00, 0x00)
                outputs[controlElement.byteInBlock] = controlElement.value.toByte()
                val result = actuate(outputs[0].toInt(), outputs[1].toInt())
                delay(1000.milliseconds)
                actuate(0x00, 0x00) && result
            }
            0x02 -> bleed(if (controlElement.value == 0x00) 0x11 else 0x44, if (controlElement.value == 0x00) 0xDD else 0x77)
            else -> false
        }
    }

    private suspend fun actuate(byte0: Int, byte1: Int): Boolean =
        request(0x30, byteArrayOf(0x10, 0x07, byte0.toByte(), byte1.toByte())) != null

    /** NA_ENTLUEFTUNG: 10 x [inlet+pompa, 55+pompa, outlet+pompa, 55+pompa, inlet+pompa], potem 20 x [inlet, 00, 00] */
    private suspend fun bleed(inlet: Int, outlet: Int): Boolean {
        for (i in 0 until 10) {
            for (valves in listOf(inlet, 0x55, outlet, 0x55, inlet)) {
                if (!actuate(valves, 0x10)) {
                    actuate(0x00, 0x00)
                    return false
                }
            }
        }
        var result = true
        for (i in 0 until 20) {
            result = actuate(inlet, 0x00) && result
            result = actuate(0x00, 0x00) && result
            result = actuate(0x00, 0x00) && result
        }
        return result
    }
}
