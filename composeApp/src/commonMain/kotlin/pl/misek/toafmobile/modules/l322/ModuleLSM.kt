package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.enums.FlashProgrammingState
import pl.misek.toafmobile.modules.enums.ModuleFeature
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleFlashProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.activity_flash_programming_close
import toafmobile.composeapp.generated.resources.activity_flash_programming_end
import toafmobile.composeapp.generated.resources.activity_flash_programming_failed
import toafmobile.composeapp.generated.resources.activity_flash_programming_flash_reading
import toafmobile.composeapp.generated.resources.activity_flash_programming_read_error
import toafmobile.composeapp.generated.resources.activity_flash_programming_read_time
import toafmobile.composeapp.generated.resources.activity_flash_programming_start
import toafmobile.composeapp.generated.resources.activity_flash_read_identification
import toafmobile.composeapp.generated.resources.module_description_lcm
import toafmobile.composeapp.generated.resources.module_lsm

import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ModuleLSM(protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_LCM_L322, listOf(CarType.L322)),
    IModuleVIN,
    IModuleControl,
    IModuleCoding,
    IModuleLiveData,
    IModuleEepromProgramming,
    IModuleFlashProgramming
    //IModuleAdvanceControl
{

    // Obraz EEPROM LCM = pamiec 0x0D00-0x0FFF (768 B, jak LCM_eep.bin w BMW Scanner).
    // Offset w pliku = adres pamieci - 0x0D00: ident/VIN/status na poczatku, dalej kodowanie/kalibracja.
    private val EEPROM_BASE = 0x0D00
    private val EEPROM_SIZE = 0x300
    private val EEPROM_BLOCK = 0x20          // {06 Hi Lo} zwraca staly blok 32 B
    private val EEPROM_HEADER_LEN = 0x19     // 0x0D00-0x0D18: ident(12)+{02 00}(13), chronione przed {06}
    private val MEMORY_READ_TIMEOUT = 500L

    // Mapa pamieci LCM (MC68HC11PH8 - 48K ROM / 2K RAM / 768 B EEPROM, wszystko wewnetrzne;
    // zgadza sie co do bajta ze zrzutem LCM_0000-FFFF.bin):
    //   0x0000-0x007F  rejestry sterujace (firmware adresuje je trybem direct, np. STAA $39 = OPTION)
    //   0x0080-0x087F  RAM 2 KB (stos pod 0x087F - stad wynika, ze rejestry zajmuja 128 B, nie 64 B)
    //   0x0880-0x0CFF  niezmapowane (same 0x00)
    //   0x0D00-0x0FFF  EEPROM 768 B - jedyna pamiec zapisywalna w aucie
    //   0x1000-0x3FFF  nic - modul odpowiada poprawnie, ale samymi zerami
    //   0x4000-0xFFFF  ROM maskowy 48 KB; wektor resetu 0xFFFE wskazuje 0x4000, stamtad obraz
    private val FLASH_BASE = 0x4000
    private val FLASH_SIZE = 0xC000
    // Przy ciaglym odczycie modul sporadycznie gubi ramke (w logu referencyjnym 13 z 2128).
    // Bez ponowienia caly odczyt przerywalby sie po kilku minutach.
    private val FLASH_READ_RETRIES = 3
    private val tag = "ModuleLSM_L322"
    private val marketEce = arrayOf(
        byteArrayOf(0x00,0x5E,0x1B,0x8B.toByte(),0x65,0x63,0x65,0x11,0x03,0x13,0x8B.toByte(),0x20,0x03,0x14,0x88.toByte(),0x40,0x12,0xDB.toByte(),0x01,0x4D,0xF6.toByte(),0x05,0x50,0x7C,0x05,0x50,0x5D,0x26,0x08,0xF6.toByte(),0x05),
        byteArrayOf(0x51,0x27,0x03,0x7A,0x05,0x51,0x13,0xE3.toByte(),0x02,0x03,0x15,0xDC.toByte(),0x01,0x12,0xE3.toByte(),0x02,0x0E,0x12,0xE1.toByte(),0x01,0x0A,0x14,0xDC.toByte(),0x01,0xC6.toByte(),0x01,0xF7.toByte(),0x05,0x52,0x20,0x23),
        byteArrayOf(0x12,0xE3.toByte(),0x02,0x1F,0x13,0xE1.toByte(),0x01,0x1B,0xF6.toByte(),0x05,0x52,0x27,0x09,0x5F,0xF7.toByte(),0x05,0x52,0xC6.toByte(),0x32,0xF7.toByte(),0x05,0x51,0xF6.toByte(),0x05,0x51,0x27,0x05,0x15,0xDC.toByte(),0x01,0x20),
        byteArrayOf(0x03,0x14,0xDC.toByte(),0x01,0x39,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00), 
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00))

    private val marketUs = arrayOf(byteArrayOf(0x00,0x82.toByte(),0x24,0x49,0x75,0x73,0x5F,0x11,0x03,0x13,0x8B.toByte(),0x20,0x27,0x14,0x88.toByte(),0x40,0xF6.toByte(),0x0D,0x36,0xC4.toByte(),0x01,0x27,0x1D,0x13,0x88.toByte(),0x20,0x19,0x13,0x85.toByte(),0x02,0x04),
        byteArrayOf(0x12,0x88.toByte(),0x40,0x08,0x13,0x85.toByte(),0x04,0x0D,0x13,0x88.toByte(),0x80.toByte(),0x09,0x15,0x8A.toByte(),0x01,0xCE.toByte(),0x01,0x8A.toByte(),0x1C,0x00,0x08,0x12,0xDB.toByte(),0x01,0x4D,0xF6.toByte(),0x05,0x50,0x7C,0x05,0x50),
        byteArrayOf(0x5D,0x26,0x08,0xF6.toByte(),0x05,0x51,0x27,0x03,0x7A,0x05,0x51,0x13,0xE3.toByte(),0x02,0x03,0x15,0xDC.toByte(),0x01,0x12,0xE3.toByte(),0x02,0x0E,0x12,0xE1.toByte(),0x01,0x0A,0x14,0xDC.toByte(),0x01,0xC6.toByte(),0x01),
        byteArrayOf(0xF7.toByte(),0x05,0x52,0x20,0x23,0x12,0xE3.toByte(),0x02,0x1F,0x13,0xE1.toByte(),0x01,0x1B,0xF6.toByte(),0x05,0x52,0x27,0x09,0x5F,0xF7.toByte(),0x05,0x52,0xC6.toByte(),0x32,0xF7.toByte(),0x05,0x51,0xF6.toByte(),0x05,0x51,0x27),
        byteArrayOf(0x05,0x15,0xDC.toByte(),0x01,0x20,0x03,0x14,0xDC.toByte(),0x01,0x39,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00),
        byteArrayOf(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00))

    override fun getKoinName() = KoinNames.ModuleL322Lsm

    override fun getLiveDataName() = "lcm_l322"

    override fun getImageResource() = Res.drawable.module_lsm

    override fun getModuleDescription() = Res.string.module_description_lcm

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        //Live data
        var message = interfaceWrite(byteArrayOf(0x0B))
        try {
            if (message.isValid)
                result[0x00] = message.answer.copyOfRange(3, message.answer.size)
            else
                return null
            //Live data - working time
            message = interfaceWrite(byteArrayOf(0x1B))
            if (message.isValid)
                result[0x01] = message.answer.copyOfRange(3, message.answer.size)
            else
                return null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readLiveData], $ex, $message"}
        }
        return result
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean  = true

    override suspend fun control(controlElement: ControlElement): Boolean {
        when (controlElement.block) {
            0x00 -> {
                //clear all
                val data = byteArrayOf(
                    0x0C.toByte(),
                    0x00.toByte(), 0x00.toByte(),
                    0x00.toByte(), 0x00.toByte(),
                    0x00.toByte(), 0x00.toByte(),
                    0x00.toByte(), 0x00.toByte(),
                    0x00.toByte(), 0x1E.toByte(),
                    0x00.toByte(), 0x00.toByte()
                )
                if (interfaceWrite(data).isValid) {
                    if (controlElement.controlSetValue != null) data[controlElement.byteInBlock + 1] =
                        controlElement.controlSetValue!!.value.toByte()
                    else data[controlElement.byteInBlock + 1] = controlElement.value.toByte()

                    return interfaceWrite(data).isValid
                }
            }

            0x01 -> return interfaceWrite(byteArrayOf(0x1C.toByte(), 0xFF.toByte())).isValid
            else -> {}
        }
        return false
    }

    override suspend fun getVIN(): String? {
        val message = interfaceWrite(byteArrayOf(0x02, 0x00))
        if (message.isValid) {
            try {
                val answer = message.answer
                return String.format(
                    "%c%c%02X%02X%02X",
                    answer[4], answer[5], answer[6], answer[7], answer[8]
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e {"$tag [getVIN], $ex, $message"}
            }
        }
        return null
    }

    override suspend fun getOdometer(): String? {
        val message = interfaceWrite(byteArrayOf(0x02, 0x00))
        if (message.isValid) {
            try {
                val i =
                    ((message.answer[9].toInt() and 0xFF) shl 8) or (message.answer[10].toInt() and 0xFF)
                return String.format(Locale.getDefault(), "%d", i * 100)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e {"$tag [getOdometer], $ex, $message"}
            }
        }
        return null
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null

        for (n in 0x01..0x9) {
            // Blok bledow to pelna ramka 0x23 (35 B); domyslne 50 ms (okno 100 ms) ucina ja
            // i rozsynchronizowuje kolejne odczyty - dajemy wiekszy timeout (okno ~500 ms).
            val message = interfaceWrite(byteArrayOf(0x04, n.toByte()), 250)
            try {
                if (message.isValid) {
                    if (message.answer[3].toInt() != 0x00) {
                        for (i in 0 until (message.answer[3].toInt() and 0xFF)) {
                            val errorCode = String.format("%02X", message.answer[4 + 2 * i])
                            val errorDescription: MutableList<Pair<StringResource, Any?>> =
                                ArrayList()
                            errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[5 + 2 * i].toInt() and 0xFF) and 0x1F))
                            errorDescription.add(ResourceHelper.getErrorDescriptionIsPresent((message.answer[5 + 2 * i].toInt() and 0x20) == 0x20))
                            if (result == null) result = ArrayList()
                            result.add(
                                ModuleError(
                                    ModuleAddress.MODULE_LCM_L322,
                                    errorCode,
                                    errorDescription
                                )
                            )
                        }
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getAllErros], $ex,$message"}
            }
        }
        return result
    }

    override suspend fun clearAllErrors(): Boolean {
        var result = true
        for (i in 1..7) {
            result = result and interfaceWrite(byteArrayOf(0x05, i.toByte())).isValid
        }
        return result
    }

    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        var i = 0

        var message: OBDMessage? = null
        try {
            do {
                message = interfaceWrite(byteArrayOf(0x08, i++.toByte()), 300)
                if (message.isValid)
                    result.add(message.answer.copyOfRange(4, message.answer.size - 1))
                else if
                             (result.isNotEmpty()) return result
                else return null
            } while (message.isValid)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readCoding], $ex, $message"}
        }

        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        val data = dataToSave.map { it.copyOf() }.toMutableList()
        val ci = getIdentification()?.ci
        if (ci == null) {
            Logger.e { "$tag [writeCoding]: identification read failed" }
            return false
        }
        if (ci == "11") {
            val marketData =
                if (data[1][0x01] == marketEce[0x00][0x01]) marketEce else marketUs
            for (i in marketData.indices)
                if (i + 1 < data.size) data[i + 1] = marketData[i].copyOf()
        }

        val dataToSaveWithCRC = calculateChecksum(data, ci)
        for (i in dataToSaveWithCRC.indices) {
            val message = interfaceWrite(
                arrayOf(
                    byteArrayOf(
                        0x09,
                        i.toByte()
                    ), dataToSaveWithCRC[i]
                )
            )
            if (!message.isValid) return false
        }
        return true
    }

    override fun getVinLength(): Int = 7

    override suspend fun writeVIN(newVin: String): Boolean {
        val vinBytes = newVin.toByteArray()
        if (!interfaceWrite(byteArrayOf(0x07, 0x01, 0x5F, 0x00)).isValid) return false
        if (!interfaceWrite(byteArrayOf(0x07, 0x01, 0x60, 0x0C)).isValid) return false
        if (!interfaceWrite(byteArrayOf(0x07, 0x01, 0x61, 0x00)).isValid) return false
        if (!interfaceWrite(byteArrayOf(0x07, 0x01, 0x62, 0x05)).isValid) return false
        if (!interfaceWrite(byteArrayOf(0x07, 0x01, 0x3F, vinBytes[0])).isValid) return false
        if (!interfaceWrite(byteArrayOf(0x07, 0x01, 0x40, vinBytes[1])).isValid) return false
        for (i in 0..2) {
            val x = newVin.substring(2 + 2 * i, 4 + 2 * i).toInt(16)
            if (!interfaceWrite(
                    byteArrayOf(
                        0x07,
                        0x01,
                        (0x41 + i).toByte(),
                        x.toByte()
                    )
                ).isValid
            ) return false
        }
        return interfaceWrite(byteArrayOf(0x07, 0x01, 0x3C, 0xFF.toByte())).isValid &&
                interfaceWrite(byteArrayOf(0x07, 0x01, 0x33, 0x10)).isValid
    }

    private fun calculateChecksum(data: List<ByteArray>, ci: String): List<ByteArray> {
        val (lwrBlock, vehicleBlock) = when (ci) {
            "11" -> 0x0E to 0x0F
            "12" -> 0x05 to 0x07
            else -> return data
        }
        data.getOrNull(lwrBlock)?.let { block ->
            if (block.isNotEmpty()) {
                var checksum = 0
                for (i in 0 until block.size - 1) checksum = checksum xor block[i].toInt()
                block[block.size - 1] = checksum.toByte()
            }
        }
        data.getOrNull(vehicleBlock)?.let { block ->
            if (block.size > 0x0F) {
                var checksum = 0
                for (i in 0x01..0x0E) checksum = checksum xor block[i].toInt()
                block[0x0F] = checksum.toByte()
            }
        }
        return data
    }

    // Odczyt pamieci {06 AddrHi AddrLo} - zwraca staly blok 32 B (bez pola rozmiaru), timeout 500 ms.
    private suspend fun readMemory(memoryAddress: Int, size: Int): ByteArray? {
        val message = interfaceWrite(
            byteArrayOf(
                0x06,
                (memoryAddress shr 8).toByte(),
                (memoryAddress and 0xFF).toByte()
            ),
            MEMORY_READ_TIMEOUT
        )
        return if (message.isValid) message.answer.copyOfRange(0x03, 0x03 + size) else null
    }

    private suspend fun readEepromHeader(): ByteArray? {
        val ident = interfaceWrite(byteArrayOf(0x00), MEMORY_READ_TIMEOUT)
        val status = interfaceWrite(byteArrayOf(0x02, 0x00), MEMORY_READ_TIMEOUT)
        if (!ident.isValid || !status.isValid) return null
        return ident.answer.copyOfRange(3, 3 + 12) + status.answer.copyOfRange(4, 4 + 13)
    }

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? {
        return try {
            if (address >= EEPROM_HEADER_LEN)
                return readMemory(EEPROM_BASE + address, size)
            val header = readEepromHeader() ?: return null
            val headEnd = minOf(address + size, EEPROM_HEADER_LEN)
            val headPart = header.copyOfRange(address, headEnd)
            if (address + size <= EEPROM_HEADER_LEN) headPart
            else {
                val rest = readMemory(EEPROM_BASE + EEPROM_HEADER_LEN, address + size - EEPROM_HEADER_LEN)
                    ?: return null
                headPart + rest
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readEeprom], $ex" }
            null
        }
    }

    // Zapis EEPROM niezweryfikowany dla LCM - na razie wylaczony (tylko odczyt).
    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean = false

    override val eepromWriteAvailable: Boolean = false

    override fun getEepromSize(): Int = EEPROM_SIZE

    override fun getEepromReadSize(): Int = EEPROM_BLOCK

    override fun getEepromWriteSize(): Int = EEPROM_BLOCK

    private val _programmingState = MutableSharedFlow<FlashProgrammingState>(
        replay = 10, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val programmingState = _programmingState.asSharedFlow()

    // Zapis flasha przez DS2 niezweryfikowany - udostepniamy tylko odczyt.
    override val flashProgrammingAvailable = false

    override fun getNewFeatures() = super.getNewFeatures() + ModuleFeature.FlashProgramming

    private suspend fun readFlashBlock(address: Int): ByteArray? {
        for (attempt in 0..FLASH_READ_RETRIES) {
            val data = try {
                readMemory(address, EEPROM_BLOCK)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [readFlashBlock], addr ${address.toString(16)}, $ex" }
                null
            }
            if (data != null && data.size == EEPROM_BLOCK) return data
            Logger.w { "$tag [readFlashBlock], addr ${address.toString(16)}, proba ${attempt + 1} nieudana" }
        }
        return null
    }

    override suspend fun readFlash() {
        val blockSize = EEPROM_BLOCK
        var result = ByteArray(0)
        val startTime = Calendar.getInstance().time
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_read_identification))
        val ident = getIdentification()
        if (ident == null) {
            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_failed))
            return
        }
        _programmingState.tryEmit(FlashProgrammingState.Message("ID : ${ident.id1}"))
        val vin = getVIN()
        if (!vin.isNullOrEmpty()) _programmingState.tryEmit(FlashProgrammingState.Message("VIN: $vin"))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_flash_reading))
        var x = 0
        val endAddress = FLASH_BASE + FLASH_SIZE
        val steps = (FLASH_SIZE + blockSize - 1) / blockSize
        var i = FLASH_BASE
        while (i < endAddress) {
            _programmingState.tryEmit(FlashProgrammingState.Progress((x++ * 100) / steps))
            val data = readFlashBlock(i)
            if (data == null) {
                _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_read_error))
                return
            }
            result += data
            i += blockSize
        }
        _programmingState.tryEmit(FlashProgrammingState.Progress(100))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_end))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_close))

        val endTime = Calendar.getInstance().time
        val totalMillis = endTime.time - startTime.time
        val seconds = (totalMillis / 1000).toInt() % 60
        val minutes = ((totalMillis / 1000).toInt() / 60) % 60
        val hours = (totalMillis / 1000).toInt() / 3600

        _programmingState.tryEmit(
            FlashProgrammingState.MessageWithParameter(
                Res.string.activity_flash_programming_read_time,
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            )
        )
        try {
            _programmingState.tryEmit(FlashProgrammingState.FlashData(result.toList(), ident, vin))
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readFlash]: $ex" }
        }
    }

    override suspend fun writeFlash(data: ByteArray) {}
}
