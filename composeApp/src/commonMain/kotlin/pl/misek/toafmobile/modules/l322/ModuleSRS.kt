package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_srs
import toafmobile.composeapp.generated.resources.module_srs
import toafmobile.composeapp.generated.resources.srs_l322_error_timer_start
import toafmobile.composeapp.generated.resources.srs_l322_error_timer_stop
import kotlin.time.Duration.Companion.milliseconds

class ModuleSRS(protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_SRS_L322, listOf(CarType.L322)),
    IModuleCoding, IModuleEepromProgramming, IModuleLiveData {
    private val tag = "ModuleSRS_L322"
    private val errorsMatrix = arrayOf(
        byteArrayOf(0x01,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x02,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x03,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x04,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x05,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x06,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x07,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x08,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x09,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x0A,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x0B,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x0C,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x0D,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x0E,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x0F,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x10,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x11,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x12,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x13,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x14,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x15,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x16,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x03,0xFF.toByte(),0x04,0xFF.toByte(),0x05,0xFF.toByte(),0x06,0xFF.toByte(),0x07,0x08,0x09),
        byteArrayOf(0x30,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x31,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x32,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x33,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x34,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x35,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x36,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x37,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x38,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x39,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x3A,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x3B,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x3C,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x3D,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x3E,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x3F,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x40,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x41,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x42,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x43,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x44,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x45,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x23,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x50,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x1F,0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x51,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x20,0xFF.toByte(),0x21,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x52,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x20,0xFF.toByte(),0x21,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x60,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x0A,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x0B,0xFF.toByte(),0x0C,0x08,0x09),
        byteArrayOf(0x61,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x0A,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x0B,0xFF.toByte(),0x0C,0x08,0x09),
        byteArrayOf(0x62,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x0A,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x0B,0xFF.toByte(),0x0C,0x08,0x09),
        byteArrayOf(0x63,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x0A,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x0B,0xFF.toByte(),0x0C,0x08,0x09),
        byteArrayOf(0x64,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x0A,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x0B,0xFF.toByte(),0x0C,0x08,0x09),
        byteArrayOf(0x70,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x0D,0xFF.toByte(),0x0E,0xFF.toByte(),0x0F,0xFF.toByte(),0x10,0xFF.toByte(),0x11,0x08,0x09),
        byteArrayOf(0x71,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x0D,0xFF.toByte(),0x12,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x72,0xFF.toByte(),0x23,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x73,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x0D,0xFF.toByte(),0x13,0xFF.toByte(),0x14,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x74,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x0D,0xFF.toByte(),0x13,0xFF.toByte(),0x14,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x75,0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x0D,0xFF.toByte(),0x12,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x80.toByte(),0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x15,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x81.toByte(),0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x0D,0xFF.toByte(),0x12,0xFF.toByte(),0x16,0xFF.toByte(),0x17,0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x82.toByte(),0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x18,0xFF.toByte(),0x19,0xFF.toByte(),0x1A,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x83.toByte(),0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x1B,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x84.toByte(),0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x0D,0xFF.toByte(),0x12,0xFF.toByte(),0x16,0xFF.toByte(),0x17,0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x85.toByte(),0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x18,0xFF.toByte(),0x19,0xFF.toByte(),0x1A,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x86.toByte(),0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x1B,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x87.toByte(),0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x22,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x88.toByte(),0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x22,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x90.toByte(),0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x1D,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0x91.toByte(),0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0x1E,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
        byteArrayOf(0xF0.toByte(),0xFF.toByte(),0x00,0x01,0x02,0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x08,0x09),
    )

    override fun getKoinName() = KoinNames.ModuleL322Srs

    override fun getImageResource() = Res.drawable.module_srs

    override fun getModuleDescription() = Res.string.module_description_srs

    override fun getLiveDataName() = "srs_l322"

    private companion object {
        const val ANSWER_BUSY: Byte = 0xA1.toByte()
        const val MAX_BUSY_RETRIES = 5
        const val BUSY_RETRY_DELAY_MS = 100L
        const val TIMEOUT_MS = 500L
        /** FS_LESEN: sterownik przechowuje maks. 10 bledow */
        const val MAX_ERRORS = 10
    }

    /** Ponowienie zapytania, gdy sterownik odpowiada BUSY (0xA1) - jak w SGBD */
    private suspend fun writeWithBusyRetry(data: ByteArray, timeout: Long = TIMEOUT_MS): OBDMessage {
        var message = interfaceWrite(data, timeout)
        var retries = 0
        while (message.answerCode == ANSWER_BUSY && retries < MAX_BUSY_RETRIES) {
            delay(BUSY_RETRY_DELAY_MS.milliseconds)
            message = interfaceWrite(data, timeout)
            retries++
        }
        return message
    }

    /** SECURITY_CHANGE: IDENT (00), potem 95 + bajty 11..14 odpowiedzi IDENT */
    private suspend fun securityChange(): Boolean {
        val ident = writeWithBusyRetry(byteArrayOf(0x00))
        if (!ident.isValid || ident.answer.size < 16) return false
        return writeWithBusyRetry(byteArrayOf(0x95.toByte()) + ident.answer.copyOfRange(11, 15)).isValid
    }

    /** SG_LOGIN (90 FF FF) + SECURITY_CHANGE - przed odczytem/zapisem kodowania */
    private suspend fun login(): Boolean {
        val loggedIn = writeWithBusyRetry(byteArrayOf(0x90.toByte(), 0xFF.toByte(), 0xFF.toByte())).isValid
        return loggedIn && securityChange()
    }

    override suspend fun getIdentification(): ModuleInfo? {
        val mi = super.getIdentification()
        if (mi != null) {
            mi.date = getProgrammingDate()
            mi.serial = getSerialNumber()
        }
        return mi
    }

    /** HERSTELLERDATEN_LESEN: blok 02, [5..7] = data kodowania (dzien, miesiac, rok) w BCD */
    private suspend fun getProgrammingDate(): String {
        if (login()) {
            val message = writeWithBusyRetry(byteArrayOf(0x08, 0x02))
            try {
                if (message.isValid) {
                    val answer = message.answer
                    return String.format("%02X%02X%02X", answer[5], answer[6], answer[7])
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getProgrammingDate()], $ex, $message"}
            }
        }
        return ""
    }

    /** BARCODE_LESEN: blok 04 - kod kreskowy sterownika */
    private suspend fun getSerialNumber(): String {
        val message = writeWithBusyRetry(byteArrayOf(0x08, 0x04))
        try {
            if (message.isValid) {
                return message.answer.copyOfRange(4, message.answer.size - 1)
                    .joinToString("") { b ->
                        val c = b.toInt() and 0xFF
                        if (c in 0x20..0x7E) c.toChar().toString() else String.format("%02X", c)
                    }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getSerialNumber], $ex, $message"}
        }
        return ""
    }

    /** C_FG_LESEN: blok 03, [4..5] litery, [6..8] cyfry (BCD) skroconego VIN */
    override suspend fun getVIN(): String {
        if (securityChange()) {
            val message = writeWithBusyRetry(byteArrayOf(0x08, 0x03))
            try{
                if (message.isValid) {
                    val answer = message.answer
                    return String.format(
                        "%c%c%02X%02X%02X",
                        answer[4], answer[5], answer[6], answer[7], answer[8]
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getVIN], $ex, $message"}
            }
        }
        return ""
    }

    /**
     * FS_LESEN: SECURITY_CHANGE, 04 00 = liczba bledow [3], potem 04 <n> dla kazdego bledu.
     * Rekord: [4] miejsce bledu, [5] bity rodzaju (FArtMatrix), [6] czestotliwosc,
     * [7..9] licznik czasu poczatku, [10..12] licznik czasu konca bledu [s].
     */
    override suspend fun getAllErrors(): List<ModuleError>? {
        if (!securityChange()) return null
        val quick = writeWithBusyRetry(byteArrayOf(0x04, 0x00))
        if (!quick.isValid || quick.answer.size < 4) return null
        val count = (quick.answer[3].toInt() and 0xFF).coerceAtMost(MAX_ERRORS)
        val result: MutableList<ModuleError> = ArrayList()
        for (n in 1..count) {
            val message = writeWithBusyRetry(byteArrayOf(0x04, n.toByte()))
            try {
                if (!message.isValid || message.answer.size < 14) {
                    Logger.e { "$tag [getAllErrors], error $n, $message" }
                    continue
                }
                val answer = message.answer
                val location = answer[4].toInt() and 0xFF
                val types = answer[5].toInt() and 0xFF
                fun word24(i: Int) = ((answer[i].toInt() and 0xFF) shl 16) or
                        ((answer[i + 1].toInt() and 0xFF) shl 8) or (answer[i + 2].toInt() and 0xFF)
                val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                val row = errorsMatrix.firstOrNull { (it[0].toInt() and 0xFF) == location }
                if (row != null) {
                    for (k in 0..7) {
                        val bitSet = (types and (1 shl k)) != 0
                        var type = row[1 + 2 * k + if (bitSet) 1 else 0].toInt() and 0xFF
                        if (type == 0xFF) continue
                        // FArtTexte ma dwa wpisy 0x23 - dla czujnika pozycji fotela i K-Bus drugi z nich
                        if (type == 0x23 && location == 0x72) type = 0x24
                        ResourceMapper.stringResource(String.format("srs_l322_error_description_%02x", type))
                            ?.let { errorDescription.add(Pair(it, null)) }
                    }
                }
                errorDescription.add(ResourceHelper.getErrorDescriptionCount(answer[6].toInt() and 0xFF))
                errorDescription.add(Pair(Res.string.srs_l322_error_timer_start, word24(7)))
                errorDescription.add(Pair(Res.string.srs_l322_error_timer_stop, word24(10)))
                result.add(ModuleError(ModuleAddress.MODULE_SRS_L322, String.format("%02X", location), errorDescription))
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getAllErrors], $ex, $message"}
            }
        }
        return result
    }

    /** FS_LOESCHEN: 05 */
    override suspend fun clearAllErrors(): Boolean = writeWithBusyRetry(byteArrayOf(0x05), 1500).isValid

    /** STATUS_LESEN: 0B - stany obwodow zaplonowych, pasow, SBE i satelitow MRSA */
    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val message = writeWithBusyRetry(byteArrayOf(0x0B))
        try {
            if (message.isValid && message.answer.size > 4)
                return mapOf(0x00 to message.answer.copyOfRange(3, message.answer.size - 1))
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readLiveData], $ex, $message"}
        }
        return null
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) = true

    /** C_C_LESEN: 08 <blok>, dane od [4] */
    override suspend fun readCoding(): List<ByteArray>? {
        var result: MutableList<ByteArray>? = null
        var i = 1

        var message: OBDMessage
        if (login()) {
            do {
                message = writeWithBusyRetry(byteArrayOf(0x08, i++.toByte()))
                try {
                    if (message.isValid) {
                        if (result == null) result = ArrayList()
                        result.add(message.answer.copyOfRange(4, message.answer.size - 1))
                    } else if (result != null) {
                        return result.ifEmpty { null }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex : Exception)
                {
                    Logger.e {"$tag [readCoding], $ex, $message"}
                }
            } while (message.isValid)
        }
        return result
    }

    /** C_C_AUFTRAG: 09 <blok> <dane>. Blok 01: bajty 08..0F to kopia 00..07 (MRS4.C41 _1/_2) */
    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        if (!login()) return false
        val data = dataToSave.map { it.copyOf() }
        data.firstOrNull()?.let { block ->
            if (block.size >= 0x10) block.copyInto(block, destinationOffset = 0x08, startIndex = 0x00, endIndex = 0x08)
        }
        for (i in data.indices) {
            val message = writeWithBusyRetry(byteArrayOf(0x09, (i + 1).toByte()) + data[i])
            if (!message.isValid) return false
        }
        return true
    }

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? {
        val message =  interfaceWrite(
        byteArrayOf(
            0x06,
            0x01,
            0x00,
            (0x0C + (address shr 8)).toByte(),
            (address and 0xFF).toByte(),
            size.toByte()
        ), 500
        )
        try{
            return if (message.isValid) message.answer.copyOfRange(0x03, 3 + size) else null
        }
        catch (ex: CancellationException) {
            throw ex
        }
        catch (ex : Exception)
        {
            Logger.e {"$tag [readEeprom], $ex, $message"}
            return null
        }

    }

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val dataToWrite =
        byteArrayOf(
            0x07,
            0x01,
            0x00,
            (0x0C + (address shr 8)).toByte(),
            (address and 0xFF).toByte(),
            (data.size).toByte()) + data
        return interfaceWrite(dataToWrite).isValid
    }

    override fun getEepromSize(): Int  = 1024

    override fun getEepromReadSize(): Int  = 16

    override fun getEepromWriteSize(): Int  = 8
}
