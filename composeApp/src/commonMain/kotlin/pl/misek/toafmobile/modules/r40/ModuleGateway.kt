package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.DrawableResource
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Locale
import kotlinx.coroutines.CancellationException

/**
 * Gateway X12 - mostek K-line <-> SCP w Rover 75 / MG ZT V8 (projekt MG Rover "X12").
 * Protokol KWP2000 w kopercie B8/F7, adres 0x0F.
 *
 * Dostep dwupoziomowy: sesja 0x84 (ident / VIN / bledy), sesja 0x86 (live data).
 */
class ModuleGateway(protocolKWP: ProtocolKWP?) : IModule, IModuleLiveData {
    private val tag = "ModuleGateway"
    private var kwp: IProtocol? = null
    private val moduleAddress = ModuleAddress.MODULE_GATEWAY.address

    init {
        if (protocolKWP != null) kwp = protocolKWP
    }

    override fun getKoinName() = KoinNames.ModuleR40Gateway

    override fun getModuleProtocol(): IProtocol = kwp!!

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol
    }

    override fun getModuleName() = "Gateway"

    override fun getModuleAddress(): ModuleAddress = ModuleAddress.MODULE_GATEWAY

    override fun getImageResource(): DrawableResource? = null

    override fun getModuleDescription() = Res.string.module_description_gateway

    override fun getSupportedCarTypes(): List<CarType> = listOf(CarType.R40)

    // --- Identyfikacja (1A 80) -------------------------------------------------
    // Blok 16 bajtow od answer[6]: part (3 ASCII + 3 BCD), HW (wartosc dziesietna),
    // reszta pol w BCD. Ostatnie 2 bajty nieuzywane.
    override suspend fun getIdentification(): ModuleInfo? {
        if (!securityAccess(SESSION_DIAGNOSTIC, SEED_DIAGNOSTIC)) return null
        val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x80.toByte()))
        try {
            if (message.isValid) {
                val answer = message.answer
                val info = ModuleInfo()
                info.id1 = String(answer.copyOfRange(6, 9)) +
                        (9..11).joinToString("") { "%02d".format(answer[it].bcd()) }
                info.hw = (answer[12].toInt() and 0xFF).toString()
                info.ci = answer[13].bcd().toString()
                info.di = answer[14].bcd().toString()
                info.bi = answer[15].bcd().toString()
                info.sw = answer[19].bcd().toString()
                info.producer = ModulesFunction.getProducer(answer[18].toInt() and 0xFF)
                info.date = String.format(
                    Locale.getDefault(),
                    "%02d/%02d",
                    answer[16].bcd(), answer[17].bcd()
                )
                return info
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getIdentification], $ex, $message" }
        }
        return null
    }

    // --- VIN (21 87) -----------------------------------------------------------
    // 11 znakow ASCII + 3 bajty numeru seryjnego pokazywane jako hex (NIE ASCII).
    override suspend fun getVIN(): String? {
        if (!securityAccess(SESSION_DIAGNOSTIC, SEED_DIAGNOSTIC)) return null
        val message = kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x87.toByte()), 250)
        try {
            if (message.isValid) {
                val answer = message.answer
                return String(answer.copyOfRange(6, 17)) +
                        (17..19).joinToString("") { "%02X".format(answer[it]) }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getVIN], $ex, $message" }
        }
        return null
    }

    override suspend fun getOdometer(): String? = null

    // --- Bledy (18 00 00) ------------------------------------------------------
    // Odpowiedz: 58 <N> [Hi Lo Status] x N. Typ usterki = status & 0x0F.
    override suspend fun getAllErrors(): List<ModuleError>? {
        if (!securityAccess(SESSION_DIAGNOSTIC, SEED_DIAGNOSTIC)) return null
        val message = kwp!!.write(moduleAddress, 0x18.toByte(), byteArrayOf(0x00, 0x00), 250)
        try {
            if (message.isValid) {
                val answer = message.answer
                val errors = ArrayList<ModuleError>()
                for (i in 0 until (answer[5].toInt() and 0xFF)) {
                    val rawCode = ((answer[6 + 3 * i].toInt() and 0xFF) shl 8) +
                            (answer[7 + 3 * i].toInt() and 0xFF)
                    errors.add(
                        ModuleError(
                            ModuleAddress.MODULE_GATEWAY,
                            "%04X".format(rawCode),
                            ArrayList()
                        )
                    )
                }
                return errors
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getAllErrors], $ex, $message" }
        }
        return null
    }

    // --- Kasowanie bledow (14 00 00) -------------------------------------------
    override suspend fun clearAllErrors(): Boolean {
        if (!securityAccess(SESSION_DIAGNOSTIC, SEED_DIAGNOSTIC)) return false
        return kwp!!.write(moduleAddress, 0x14.toByte(), byteArrayOf(0x00, 0x00), 1500).isValid
    }

    override suspend fun reset(): Boolean =
        kwp!!.write(moduleAddress, 0x11.toByte(), byteArrayOf(0x01)).isValid

    // --- Live data (21 01) -----------------------------------------------------
    // Wymaga wyzszego poziomu dostepu niz reszta funkcji (sesja 0x86).
    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean =
        securityAccess(SESSION_LIVE_DATA, SEED_LIVE_DATA)

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val message = kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x01), 250)
        try {
            if (message.isValid) {
                val data = message.answer.copyOfRange(6, message.length - 1)
                // Cisnienie oleju i immobiliser: 0x00 = pierwszy stan, kazda inna wartosc = drugi.
                // Normalizacja do 0/1, zeby mapa wartosci z JSON pokrywala caly zakres.
                if (data.size > OIL_PRESSURE_BYTE && data[OIL_PRESSURE_BYTE].toInt() != 0)
                    data[OIL_PRESSURE_BYTE] = 1.toByte()
                if (data.size > IMMOBILISER_BYTE && data[IMMOBILISER_BYTE].toInt() != 0)
                    data[IMMOBILISER_BYTE] = 1.toByte()
                return mapOf(0 to data)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readLiveData], $ex, $message" }
        }
        return null
    }

    // --- Dostep ----------------------------------------------------------------
    /**
     * Otwiera sesje [session] i wykonuje SecurityAccess: zadanie seeda trybem [seedMode],
     * odeslanie klucza trybem [seedMode]+1.
     */
    private suspend fun securityAccess(session: Int, seedMode: Int): Boolean {
        var message = kwp!!.write(moduleAddress, 0x10.toByte(), byteArrayOf(session.toByte()), 500)
        try {
            if (!message.isValid) return false
            message = kwp!!.write(moduleAddress, 0x27.toByte(), byteArrayOf(seedMode.toByte()), 500)
            if (!message.isValid) return false
            val seed = ((message.answer[6].toInt() and 0xFF) shl 8) +
                    (message.answer[7].toInt() and 0xFF)
            val key = securityAccessCalculator(seed, session)
            val data = byteArrayOf(
                (seedMode + 1).toByte(),
                ((key shr 8) and 0xFF).toByte(),
                (key and 0xFF).toByte()
            )
            return kwp!!.write(moduleAddress, 0x27.toByte(), data, 500).isValid
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [securityAccess], $ex, $message" }
        }
        return false
    }

    /**
     * Seed -> key Gatewaya X12. Liczba przebiegow LFSR zalezy od poziomu, dla 0x81 liczona
     * ze starszego bajtu seeda, dla 0x84/0x86 z mlodszego. Na koniec zamiana bajtow.
     * Zweryfikowane: 0x84 seed 1234 -> 2841, 0x86 seed 1234 -> D048.
     */
    private fun securityAccessCalculator(seed: Int, level: Int): Int {
        val seedHigh = (seed shr 8) and 0xFF
        val seedLow = seed and 0xFF
        val passes = when (level) {
            0x81 -> ((seedHigh xor 0x73) and 0x1C) + 4
            0x84 -> ((seedLow xor 0x73) and 0x2E) + 6
            0x86 -> ((seedLow xor 0x73) and 0x31) + 1
            else -> return 0
        }
        var key = seed and 0xFFFF
        repeat(passes) {
            val feedback = ((key shr 15) xor (key shr 10) xor (key shr 8) xor
                    (key shr 7) xor (key shr 6)) and 0x01
            key = ((key shl 1) and 0xFFFF) or feedback
        }
        return ((key shl 8) or (key shr 8)) and 0xFFFF
    }

    private fun Byte.bcd(): Int {
        val value = toInt() and 0xFF
        return (value shr 4) * 10 + (value and 0x0F)
    }

    companion object {
        /** Sesja + tryb seeda dla identyfikacji, VIN i bledow. */
        private const val SESSION_DIAGNOSTIC = 0x84
        private const val SEED_DIAGNOSTIC = 0x05

        /** Sesja + tryb seeda dla live data. */
        private const val SESSION_LIVE_DATA = 0x86
        private const val SEED_LIVE_DATA = 0x09

        private const val OIL_PRESSURE_BYTE = 5
        private const val IMMOBILISER_BYTE = 6

        /**
         * Kody, dla ktorych etykieta pokazywana przez tester rozni sie od surowych bajtow w ramce.
         * Pozostale kody wyswietlane sa wprost jako wartosc szesnastkowa.
         */
    }
}
