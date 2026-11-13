package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import pl.misek.toafmobile.control.ControlElement
import org.jetbrains.compose.resources.DrawableResource
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import kotlinx.coroutines.CancellationException

/**
 * Sterownik silnika V8 (Ford EEC-V) w Rover 75 / MG ZT V8 - projekt MG Rover "X12".
 * Adres 0x10, koperta B8/F7 mostkowana przez Gateway na magistrale SCP.
 *
 * Odczyt bledow (`13`) zwraca KILKA ramek sklejonych jedna po drugiej - kazda niesie
 * dokladnie 3 sloty DTC po 2 bajty, puste sloty wypelnione 00 00. Dlatego uzywany jest
 * [ProtocolKWP.writeForMultipleAnswers], ktory waliduje kazda ramke osobno.
 *
 * Diagnostyka tego sterownika wymaga wczesniejszego zablokowania klimatyzacji ([hevac]),
 * dlatego modul HEVAC jest tu wstrzykiwany i dostepny bezposrednio.
 */
class ModuleX12(protocolKWP: ProtocolKWP?, hevac: ModuleHevacX12? = null) : IModule, IModuleLiveData {
    private val tag = "ModuleX12"
    private var kwp: IProtocol? = null
    private val moduleAddress = ModuleAddress.MODULE_X12.address
    private var liveDataElements: List<LiveDataElement> = emptyList()

    var hevac: ModuleHevacX12? = hevac
        private set

    init {
        if (protocolKWP != null) kwp = protocolKWP
    }


    suspend fun inhibitHevac(): Boolean = setHevacState(HEVAC_INHIBIT)

    suspend fun enableHevac(): Boolean = setHevacState(HEVAC_ENABLE)

    private suspend fun setHevacState(value: Int): Boolean {
        val module = hevac
        if (module == null) {
            Logger.e { "$tag [setHevacState]: HEVAC module not available" }
            return false
        }
        return module.control(ControlElement(value = value))
    }

    override fun getKoinName() = KoinNames.ModuleR40X12

    override fun getModuleProtocol(): IProtocol = kwp!!

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol
    }

    override fun getModuleName() = "ECU V8"

    override fun getModuleAddress(): ModuleAddress = ModuleAddress.MODULE_X12

    override fun getImageResource(): DrawableResource? = Res.drawable.module_engine

    override fun getModuleDescription() = Res.string.module_description_x12

    override fun getSupportedCarTypes(): List<CarType> = listOf(CarType.R40)

    // --- Identyfikacja (23 - Read by DMR) --------------------------------------
    // Szesc odczytow pamieci; kazdy zwraca 4 znaki ASCII na stalej pozycji answer[7..10].
    // Sklejane w: Software Level (3 pierwsze odczyty), Module Number (2 kolejne), Software Year (ostatni).
    override suspend fun getIdentification(): ModuleInfo? {
        try {
            inhibitHevac()
            val parts = IDENT_ADDRESSES.map { readIdentPart(it) ?: return null }
            val info = ModuleInfo()
            info.id1 = parts[0] + parts[1] + parts[2]
            info.additionalInfo.add(Pair(Res.string.x12_info_module_number, parts[3] + parts[4]))
            info.additionalInfo.add(Pair(Res.string.x12_info_software_year, parts[5]))
            return info
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getIdentification], $ex" }
        }
        return null
    }

    /** Odczytuje 4 znaki ASCII spod adresu [address] w pamieci sterownika (`23 09 FF <adr>`). */
    private suspend fun readIdentPart(address: Int): String? {
        val request = byteArrayOf(
            0x09, 0xFF.toByte(),
            ((address shr 8) and 0xFF).toByte(), (address and 0xFF).toByte()
        )
        val message = kwp!!.write(moduleAddress, 0x23.toByte(), request, 300)
        if (!message.isValid || message.answer.size < IDENT_TEXT_END) {
            Logger.e { "$tag [readIdentPart] address ${"%04X".format(address)} failed, $message" }
            return null
        }
        return String(message.answer.copyOfRange(IDENT_TEXT_START, IDENT_TEXT_END))
    }

    override suspend fun getVIN(): String? = null

    override suspend fun getOdometer(): String? = null

    // --- Odczyt bledow (13) ----------------------------------------------------
    // Odpowiedz: kilka ramek `53` + 3 sloty DTC po 2 bajty (puste = 00 00).
    override suspend fun getAllErrors(): List<ModuleError>? {
        inhibitHevac()
        val protocol = kwp as? ProtocolKWP ?: run {
            Logger.e { "$tag [getAllErrors]: protocol is not ProtocolKWP" }
            return null
        }
        try {
            val messages = protocol.writeForMultipleAnswers(moduleAddress, 0x13.toByte(), byteArrayOf(), 250)
            if (messages.isEmpty()) return null
            val errors = ArrayList<ModuleError>()
            for (message in messages) {
                if (!message.isValid) {
                    Logger.e { "$tag [getAllErrors]: invalid frame $message" }
                    continue
                }
                val answer = message.answer
                for (slot in 0 until DTC_SLOTS_PER_FRAME) {
                    val high = DTC_FIRST_BYTE + slot * 2
                    if (high + 1 >= answer.size - 1) break
                    val code = ((answer[high].toInt() and 0xFF) shl 8) +
                            (answer[high + 1].toInt() and 0xFF)
                    if (code == 0) continue
                    errors.add(
                        ModuleError(
                            ModuleAddress.MODULE_X12,
                            "P%04X".format(code),
                            ArrayList()
                        )
                    )
                }
            }
            return errors
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getAllErrors], $ex" }
        }
        return null
    }

    // --- Kasowanie bledow (14) -------------------------------------------------
    override suspend fun clearAllErrors(): Boolean {
        try {
            inhibitHevac()
            return kwp!!.write(moduleAddress, 0x14.toByte(), byteArrayOf(), 1500).isValid
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [clearAllErrors], $ex" }
        }
        return false
    }

    override suspend fun reset(): Boolean = false

    override fun getLiveDataName() = "x12"

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        this.liveDataElements = liveDataElements
        inhibitHevac()
        return true
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (pid in liveDataElements.map { it.block }.distinct()) {
            val request = byteArrayOf(((pid shr 8) and 0xFF).toByte(), (pid and 0xFF).toByte())
            val message = kwp!!.write(moduleAddress, 0x22.toByte(), request, 250)
            try {
                if (message.isValid && message.length > LIVE_DATA_FIRST_BYTE)
                    result[pid] = message.answer.copyOfRange(LIVE_DATA_FIRST_BYTE, message.length - 1)
                else
                    Logger.e { "$tag [readLiveData] PID ${"%04X".format(pid)} failed, $message" }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [readLiveData], $ex, $message" }
            }
        }
        return result.ifEmpty { null }
    }

    companion object {
        /** Kazda ramka odpowiedzi `53` niesie dokladnie 3 sloty DTC. */
        private const val DTC_SLOTS_PER_FRAME = 3

        /** Indeks pierwszego bajtu DTC w ramce: B8 F7 10 LEN 53 <tu>. */
        private const val DTC_FIRST_BYTE = 5

        /**
         * Adresy szesciu blokow identyfikacji, w kolejnosci:
         * Software Level (czesci 1-3), Module Number (czesci 1-2), Software Year.
         */
        private val IDENT_ADDRESSES = listOf(0x0680, 0x0A8C, 0x0E88, 0x1492, 0x189E, 0x7CFA)

        /** 4 znaki ASCII leza na stalej pozycji: B8 F7 10 LEN 63 x x <tu 4 bajty>. */
        private const val IDENT_TEXT_START = 7
        private const val IDENT_TEXT_END = 11

        /** Dane live data zaczynaja sie po `62 <PID_Hi> <PID_Lo>`. */
        private const val LIVE_DATA_FIRST_BYTE = 7

        /** Wartosci SetEcuState modulu HEVAC. */
        private const val HEVAC_INHIBIT = 0xFF
        private const val HEVAC_ENABLE = 0x00
    }
}
