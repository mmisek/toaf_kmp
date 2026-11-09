package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import pl.misek.toafmobile.activities.composeModels.activities.AdvanceControlAbsBleeding
import pl.misek.toafmobile.modules.enums.BrakeBleedingWheel
import pl.misek.toafmobile.modules.interfaces.AdvanceControlElement
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceControl
import pl.misek.toafmobile.modules.interfaces.IModuleBrakeBleeding
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val tag = "ModuleABS"
class ModuleABS( protocolDS2: ProtocolDS2?) :ModuleDS2(protocolDS2, ModuleAddress.MODULE_ABS, listOf(CarType.R40)), IModuleLiveData,
    IModuleAdvanceControl, IModuleBrakeBleeding
{
    private companion object {
        /** DOWNLOAD_STELLGLIED (ASC57R75) - program sterowania stellgliedami, wysylany przed kazdym TEST_D_STELLGLIED */
        val DOWNLOAD_STELLGLIED: ByteArray = ("31 04 00 00 BB 07 07 64 07 00 B1 02 24 A3 01 A2 20 20 A2 79 22 A2 21 1E 88 00 " +
                "1E DF 34 51 FE 1E 1D 78 23 1D D7 EF 51 F8 1F 1C 08 02 1C 67 01 AC 20 1C A2 1C 1C 38 1E 16 A1 01 01 20 " +
                "71 07 1F 09 1F 20 92 1C 20 3A 22 03 94 21 20 B0 20 22 C6 1C 22 E0 24 B9 07 40 9A 40 00 DF 15 A2 79 7C " +
                "88 00 7C D7 04 07 40 27 A3 6B 01 A6 20 7C D9 02 07 40 F0")
            .split(" ").map { it.toInt(16).toByte() }.toByteArray()
        /** TEST_D_STELLGLIED: [BEFEHL_1, ST_1, BEFEHL_2, ST_2, W_ZEIT lo, W_ZEIT hi, BEFEHL_3, ST_3, BEFEHL_4, ST_4] */
        const val TEST_D_STELLGLIED: Byte = 0x32
        const val ON: Byte = 0xFF.toByte()
        const val OFF: Byte = 0x00
        // STG_TABELLE
        const val MRA: Byte = 0x22   // silnik pompy powrotnej
        const val ST_62: Byte = 0x62 // brak w STG_TABELLE, wysylane przez T4 razem z MRA
        const val SILGK: Byte = 0x64
        const val BLEEDING_CYCLES = 16
        /** W_ZEIT - czas otwarcia zaworow kola w jednym cyklu */
        const val BLEEDING_VALVE_TIME_MS = 1000
        const val BLEEDING_CYCLE_MIN_MS = 1100L
        const val TEST_TIMEOUT_MS = 2000L
    }

    /**
     * Zawory (wylotowy AV, wlotowy EV) wg logu T4 R40 (odpowietrzanie.log).
     * Uwaga: nazwy w STG_TABELLE SGBD (AVVL=0x32 ... AVHL=0x3E) wskazuja odwrotne przypisanie po przekatnej.
     */
    private fun BrakeBleedingWheel.valves(): Pair<Byte, Byte> = when (this) {
        BrakeBleedingWheel.FrontLeft -> Pair(0x3A.toByte(), 0x38.toByte())
        BrakeBleedingWheel.FrontRight -> Pair(0x3E.toByte(), 0x3C.toByte())
        BrakeBleedingWheel.RearLeft -> Pair(0x36.toByte(), 0x34.toByte())
        BrakeBleedingWheel.RearRight -> Pair(0x32.toByte(), 0x30.toByte())
    }

    override fun getKoinName() = KoinNames.ModuleR40Abs

    override fun getImageResource() = Res.drawable.module_abs

    override fun getModuleDescription() = Res.string.module_description_abs

    override fun getAdvanceControlElements() = listOf(
        AdvanceControlElement(Res.string.advance_control_abs_bleeding_title, isNew = true) { userInfo, onShowResult ->
            AdvanceControlAbsBleeding(userInfo = userInfo, onShowResult = onShowResult)
        }
    )

    override val brakeBleedingCycles = BLEEDING_CYCLES

    // Kolejnosc jak w T4 - "Modulator"
    override val modulatorBleedingOrder = listOf(
        BrakeBleedingWheel.RearRight,
        BrakeBleedingWheel.RearLeft,
        BrakeBleedingWheel.FrontLeft,
        BrakeBleedingWheel.FrontRight
    )

    override suspend fun startBrakeBleeding(): Boolean {
        getIdentification()
        return controlActuators(byteArrayOf(ON, MRA, ON, ST_62), 0, byteArrayOf(OFF, SILGK, OFF, 0x00))
    }

    override suspend fun bleedWheelCycle(wheel: BrakeBleedingWheel): Boolean {
        val (outlet, inlet) = wheel.valves()
        val start = TimeSource.Monotonic.markNow()
        val result = controlActuators(byteArrayOf(ON, outlet, ON, inlet), BLEEDING_VALVE_TIME_MS, byteArrayOf(OFF, outlet, OFF, inlet))
        // Sterownik trzyma zawory przez W_ZEIT - kolejny cykl dopiero po jego uplywie
        val remaining = BLEEDING_CYCLE_MIN_MS - start.elapsedNow().inWholeMilliseconds
        if (result && remaining > 0)
            delay(remaining.milliseconds)
        return result
    }

    override suspend fun stopBrakeBleeding() =
        controlActuators(byteArrayOf(OFF, MRA, OFF, ST_62), 0, byteArrayOf(ON, SILGK, OFF, 0x00))

    /** DOWNLOAD_STELLGLIED + TEST_D_STELLGLIED: stellglieder 1,2 -> W_ZEIT [ms] -> stellglieder 3,4 */
    private suspend fun controlActuators(first: ByteArray, waitTimeMs: Int, second: ByteArray): Boolean {
        if (!interfaceWrite(DOWNLOAD_STELLGLIED).isValid) {
            Logger.e { "$tag [controlActuators]: download failed" }
            return false
        }
        val message = interfaceWrite(
            byteArrayOf(TEST_D_STELLGLIED) + first +
                    byteArrayOf((waitTimeMs and 0xFF).toByte(), ((waitTimeMs shr 8) and 0xFF).toByte()) + second,
            TEST_TIMEOUT_MS
        )
        if (!message.isValid)
            Logger.e { "$tag [controlActuators]: test failed, $message" }
        return message.isValid
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        getIdentification()
        val message =  interfaceWrite(byteArrayOf(0x04, 0x01))
        if (message.isValid && message.answer.size > 3) {
            try {
            result = ArrayList()
            for (i in 0 until (message.answer[3].toInt() and 0xFF)) {
                val errorCode = String.format("%02X", message.answer[4 + 4 * i])
                val errorDescription: MutableList<Pair<StringResource,Any?>> = ArrayList()
                errorDescription.add(ResourceHelper.getErrorDescriptionCount(message.answer[6 + 4 * i].toInt() and 0xFF))
                errorDescription.add(ResourceHelper.getErrorDescriptionSpeed(
                    ((message.answer[7 + 4 * i].toInt() and 0xFF) shl 8) + 0.0625 * (message.answer[8 + 4 * i].toInt() and 0xFF)))
                if ((message.answer[5 + 4 * i].toInt() and 0x10) == 0x10)
                    errorDescription.add(Pair(Res.string.abs_error_detail_00,null))
                else
                    errorDescription.add(Pair(Res.string.abs_error_detail_01, null))
                if ((message.answer[5 + 4 * i].toInt() and 0x20) == 0x20)
                    errorDescription.add(Pair(Res.string.abs_error_detail_02, null))
                else
                    errorDescription.add(Pair(Res.string.abs_error_detail_03,null))
                if ((message.answer[5 + 4 * i].toInt() and 0x40) == 0x40) errorDescription.add(
                    Pair(Res.string.abs_error_detail_04, null))
                else
                    errorDescription.add(Pair(Res.string.abs_error_detail_05,null))
                if ((message.answer[5 + 4 * i].toInt() and 0x80) == 0x80) errorDescription.add(
                    Pair(Res.string.abs_error_detail_06,null))
                else
                    errorDescription.add(Pair(Res.string.abs_error_detail_07,null))
                result.add(ModuleError(ModuleAddress.MODULE_ABS, errorCode, errorDescription))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch ( ex : Exception)
        {
            Logger.e {"$tag [getAllErrors()]: $ex, $message"}
            return result
        }
        }

        return result
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        if (interfaceWrite(byteArrayOf(0x00)).isValid) {
            val message = interfaceWrite(byteArrayOf(0x0B))
            if (message.isValid) {
                try {
                    val res = message.answer.copyOfRange(3, message.answer.size - 1)

                    var i = 0
                    while (i < 8) {
                        val a = res[i]
                        val b = res[i + 1]
                        res[i] = b
                        res[i + 1] = a
                        i += 2
                    }
                    val c = res[0x0E]
                    val d = res[0x0F]
                    res[0x0E] = d
                    res[0x0F] = c

                    result[0x00] = res
                    return result
                } catch (ex: CancellationException) {
                    throw ex
                } catch ( ex : Exception)
                {
                    Logger.e {"$tag [readLiveData()]: $ex , $message"}
                    return null
                }
            } else return null
        } else return null
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) = true

    override suspend fun getModuleVoltage() = null
}
