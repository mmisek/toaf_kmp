package pl.misek.toafmobile.modules.rjo

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKW1281
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_abs
import toafmobile.composeapp.generated.resources.module_description_abs53

/**
 * Pompa ABS Bosch 5.3 (Rover 25 / RJ0) - protokol typu KW1281, inicjalizacja 5 bodow, 9600 8N1.
 * Zrodlo: program AVR "obd_KWP_92" (BOSCH 5.0 5.3 EDC 1.3), logi z testera i z Delphi (ABS53_reads/writes).
 *
 * Inicjalizacja adresem 0x20 (Delphi: *639_20_...). Po inicjalizacji, raz na sesje, wgrywana procedura
 * do RAM sterownika (02 + blok SERVICE_48) - tak jak robi to Delphi przed odczytem bledow, live data i testami.
 *
 * Tytuly blokow: 08 odczyt grupy live data (FB + blok predkosci), 07 odczyt bledow (odpowiedzi FC),
 * 05 kasowanie bledow, 27 polecenie elementu (odpowiedz E8).
 */
class ModuleABS53(private var kw1281: ProtocolKW1281) : IModule, IModuleLiveData, IModuleControl {
    private val tag = "ModuleABS53"

    private val moduleAddress = ModuleAddress.MODULE_ABS53.address.toInt() and 0xFF

    init {
        // jak Delphi: procedura wgrywana raz, zaraz po kazdej inicjalizacji sesji
        kw1281.setSessionSetup(moduleAddress, listOf(TITLE_UPLOAD to ACTUATOR_ROUTINE))
    }

    override fun getKoinName() = KoinNames.ModuleRJOAbs

    /** Prefiks plikow sterowania / live data i kluczy tekstow (abs53_...) - "ABS" to modul R40 */
    override fun getModuleName(): String = "ABS53"

    override fun getModuleAddress(): ModuleAddress = ModuleAddress.MODULE_ABS53

    override fun getImageResource() = Res.drawable.module_abs

    override fun getModuleDescription() = Res.string.module_description_abs53

    override fun getModuleProtocol(): IProtocol = kw1281

    override fun setModuleProtocol(protocol: IProtocol) {
        kw1281 = protocol as ProtocolKW1281
        kw1281.setSessionSetup(moduleAddress, listOf(TITLE_UPLOAD to ACTUATOR_ROUTINE))
    }

    override fun getSupportedCarTypes(): List<CarType> = listOf(CarType.RJO)

    private suspend fun transact(title: Int, data: ByteArray = ByteArray(0), multiBlock: Boolean = false) =
        kw1281.transact(moduleAddress, title, data, multiBlock)

    /** Identyfikacja - bloki tekstowe wyslane przez sterownik po inicjalizacji */
    override suspend fun getIdentification(): ModuleInfo? {
        transact(ProtocolKW1281.ACK) ?: return null
        val ident = kw1281.identification
        if (ident.isEmpty()) return null
        return ModuleInfo().apply {
            id1 = ident.getOrElse(0) { "" }
            id2 = ident.drop(1).joinToString(" ")
        }
    }

    override suspend fun getVIN(): String? = null

    override suspend fun getOdometer(): String? = null

    /**
     * 07 - bloki FC, w kazdym do 5 bledow po 5 bajtow: [kod][..][..][..][status], status 04 - aktywny, 02 - pasywny.
     * Numer bledu jak w Delphi - pozycja kodu w tabeli obslugiwanych kodow [DTC_NUMBERS] (1..23).
     * Pozostale kody zglaszane jako nieznany blad z surowym kodem (xNN).
     */
    override suspend fun getAllErrors(): List<ModuleError>? {
        val blocks = transact(TITLE_READ_DTC, multiBlock = true) ?: return null
        val result: MutableList<ModuleError> = ArrayList()
        for (block in blocks) {
            if (block.title != TITLE_DTC) continue
            for (offset in 0..block.data.size - DTC_RECORD_SIZE step DTC_RECORD_SIZE) {
                val code = block.data[offset].toInt() and 0xFF
                if (code == 0x00 || code == 0xFF) continue
                val status = block.data[offset + 4].toInt() and 0xFF
                val description: MutableList<Pair<StringResource, Any?>> = ArrayList()
                if ((status and 0x04) == 0x04 || (status and 0x02) == 0x02)
                    description.add(ResourceHelper.getErrorDescriptionIsPresent((status and 0x04) == 0x04))
                val errorCode = DTC_NUMBERS[code]?.toString() ?: String.format("x%02X", code)
                result.add(ModuleError(ModuleAddress.MODULE_ABS53, errorCode, description))
            }
        }
        return result
    }

    override suspend fun clearAllErrors(): Boolean = transact(TITLE_CLEAR_DTC, multiBlock = true) != null

    override suspend fun reset(): Boolean = false

    /**
     * Grupa 08 (jak Delphi): dane kolejnych blokow do ACK - [3 bajty stanow][4 x predkosc kola LE16].
     * Predkosc = ((raw + 10) * 6) / 100 km/h (raw 001C = 2 km/h, zgodnie z Delphi).
     * Blok 1: predkosci LP, PP, LT, PT po 3 bajty [0.01 km/h].
     * Blok 2: pedal hamulca (1 - styk zamkniety), przekaznik masy pompy (1 - zal.), przekaznik zasilania (1 - zal.) -
     * kolejnosc bajtow stanow przyjeta jak komorki RAM 0098, 0022, 0020 w programie AVR.
     */
    override suspend fun readLiveData(): Map<Int, Any>? {
        try {
            val blocks = transact(TITLE_READ_GROUP, multiBlock = true) ?: return null
            val data = blocks.fold(ByteArray(0)) { acc, block -> acc + block.data }
            if (data.size < 11) return null
            val speeds = ByteArray(12)
            for (index in 0..3) {
                val raw = (data[3 + 2 * index].toInt() and 0xFF) or ((data[4 + 2 * index].toInt() and 0xFF) shl 8)
                val speed = (raw + 10) * 6
                speeds[3 * index] = (speed shr 16).toByte()
                speeds[3 * index + 1] = (speed shr 8).toByte()
                speeds[3 * index + 2] = speed.toByte()
            }
            val states = byteArrayOf(
                if (((data[0].toInt() and 0xFF) shr 4) == 0x0F) 1 else 0,
                if (((data[1].toInt() and 0xFF) shr 4) == 0x0F) 0 else 1,
                if (((data[2].toInt() and 0xFF) shr 4) == 0x0F) 1 else 0
            )
            return mapOf(1 to speeds, 2 to states)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readLiveData], $ex" }
            return null
        }
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean = true

    /** Testy elementow - polecenie 27 (odpowiedz E8) */
    override suspend fun control(controlElement: ControlElement): Boolean {
        val command = CONTROL_COMMANDS[controlElement.byteInBlock] ?: return false
        return transact(TITLE_RAM, command) != null
    }

    companion object {
        private const val TITLE_UPLOAD = 0x02
        private const val TITLE_CLEAR_DTC = 0x05
        private const val TITLE_READ_DTC = 0x07
        private const val TITLE_READ_GROUP = 0x08
        private const val TITLE_RAM = 0x27
        private const val TITLE_DTC = 0xFC
        private const val DTC_RECORD_SIZE = 5

        /**
         * Kod bledu w bloku FC -> numer bledu w Delphi (klucz tekstu abs53_error_N), z testow symulatorem.
         * Kody wewnetrzne Bosch pogrupowane wg elementu - brak wzoru, stad tabela.
         */
        private val DTC_NUMBERS = mapOf(
            0x01 to 1,      // niskie napiecie
            0x04 to 2,      // czujnik predkosci tylny lewy - niewiarygodny
            0x05 to 3,      // czujnik predkosci tylny prawy - niewiarygodny
            0x06 to 4,      // czujnik predkosci przedni lewy - niewiarygodny
            0x07 to 5,      // czujnik predkosci przedni prawy - niewiarygodny
            0x0E to 6,      // przekaznik zaworow
            0x0F to 7,      // pompa powrotna
            0x14 to 8,      // diagnoza kodem blyskowym - blad obslugi
            0x15 to 9,      // sterownik
            0x18 to 10,     // wieniec zebaty
            0x19 to 11,     // wlacznik swiatel stop - obwod
            0x1E to 12,     // czujnik predkosci tylny lewy - obwod
            0x1F to 13,     // czujnik predkosci tylny prawy - obwod
            0x20 to 14,     // czujnik predkosci przedni prawy - obwod
            0x21 to 15,     // czujnik predkosci przedni lewy - obwod
            0x2F to 16,     // zawor wylotowy tylny lewy
            0x30 to 17,     // zawor wylotowy tylny prawy
            0x31 to 18,     // zawor wylotowy przedni prawy
            0x32 to 19,     // zawor wylotowy przedni lewy
            0x33 to 20,     // zawor wlotowy tylny lewy
            0x34 to 21,     // zawor wlotowy tylny prawy
            0x35 to 22,     // zawor wlotowy przedni prawy
            0x36 to 23      // zawor wlotowy przedni lewy
        )

        private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

        private fun command(state: Int, element: Int, length: Int) =
            ByteArray(length).also { it[0] = state.toByte(); it[1] = element.toByte() }

        /** Test lampki jak w Delphi: 00 [element] 00 00 [czas ms LE] FF [element] 00 00, czas 3000 ms */
        private fun lampTest(element: Int) =
            bytes(0x00, element, 0x00, 0x00, LAMP_TEST_MS and 0xFF, LAMP_TEST_MS shr 8, 0xFF, element, 0x00, 0x00)

        private const val LAMP_TEST_MS = 3000

        /** byte w JSON sterowania -> dane polecenia 27 */
        private val CONTROL_COMMANDS = mapOf(
            0x00 to command(0xFF, 0x22, 10),    // pompa - start
            0x01 to command(0x00, 0x22, 10),    // pompa - stop
            0x02 to command(0xFF, 0x30, 12),    // zawor lewy przedni - otwarcie
            0x03 to command(0x00, 0x30, 12),    // zawor lewy przedni - zamkniecie
            0x04 to command(0xFF, 0x34, 12),    // zawor prawy przedni - otwarcie
            0x05 to command(0x00, 0x34, 12),    // zawor prawy przedni - zamkniecie
            0x06 to command(0xFF, 0x3C, 12),    // zawor lewy tylny - otwarcie
            0x07 to command(0x00, 0x3C, 12),    // zawor lewy tylny - zamkniecie
            0x08 to command(0xFF, 0x38, 12),    // zawor prawy tylny - otwarcie
            0x09 to command(0x00, 0x38, 12),    // zawor prawy tylny - zamkniecie
            0x0A to lampTest(0x02),             // lampka ABS (Delphi: ABS Indicator Lamp)
            0x0B to lampTest(0x0E)              // lampka ukladu hamulcowego (Delphi: Brake System warning lamp)
        )

        /** SERVICE_48 - procedura wgrywana raz na sesje po inicjalizacji (identyczna z blokiem wysylanym przez Delphi) */
        private val ACTUATOR_ROUTINE = bytes(
            0x04, 0x00, 0x00, 0xBB, 0x07, 0x07, 0x64, 0x07, 0x00, 0xB1, 0x02, 0x24, 0xA3, 0x01, 0xA2, 0x20,
            0x20, 0xA2, 0x79, 0x22, 0xA2, 0x21, 0x1E, 0x88, 0x00, 0x1E, 0xDF, 0x34, 0x51, 0xFE, 0x1E, 0x1D,
            0x78, 0x23, 0x1D, 0xD7, 0xEF, 0x51, 0xF8, 0x1F, 0x1C, 0x08, 0x02, 0x1C, 0x67, 0x01, 0xAC, 0x20,
            0x1C, 0xA2, 0x1C, 0x1C, 0x38, 0x1E, 0x16, 0xA1, 0x01, 0x01, 0x20, 0x71, 0x07, 0x1F, 0x09, 0x1F,
            0x20, 0x92, 0x1C, 0x20, 0x3A, 0x22, 0x03, 0x94, 0x21, 0x20, 0xB0, 0x20, 0x22, 0xC6, 0x1C, 0x22,
            0xE0, 0x24, 0xB9, 0x07, 0x40, 0x9A, 0x40, 0x00, 0xDF, 0x15, 0xA2, 0x79, 0x7C, 0x88, 0x00, 0x7C,
            0xD7, 0x04, 0x07, 0x40, 0x27, 0xA3, 0x6B, 0x01, 0xA6, 0x20, 0x7C, 0xD9, 0x02, 0x07, 0x40, 0xF0
        )
    }
}
