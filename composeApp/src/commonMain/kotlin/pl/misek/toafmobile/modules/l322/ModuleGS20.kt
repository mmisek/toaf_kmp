package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.containers.GS20FreezeFrameData
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.FlashProgrammingState
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleFlashProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Calendar
import java.util.Locale

class ModuleGS20(val protocolDS2: ProtocolDS2?) :ModuleDS2( protocolDS2, ModuleAddress.MODULE_GS20, listOf(CarType.L322))
    , IModuleLiveData
    , IModuleFlashProgramming
    , IModuleControl
    , IModuleEepromProgramming
{
    private var tag = "ModuleGS20"
    private val _programmingState =  MutableSharedFlow<FlashProgrammingState>(replay = 10, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val programmingState = _programmingState.asSharedFlow()
    private val booleanMap: Map<Int, StringResource> = mapOf(
        0x00 to Res.string.coding_not_active,
        0x01 to Res.string.coding_active
    )

    private val gearMap: Map<Int, StringResource> = mapOf(
        0x20 to Res.string.gs20_live_data_value_1_gear,
        0x40 to Res.string.gs20_live_data_value_2_gear,
        0x60 to Res.string.gs20_live_data_value_3_gear,
        0x80 to Res.string.gs20_live_data_value_4_gear,
        0xA0 to Res.string.gs20_live_data_value_5_gear
    )

    private val lastChangeMap: Map<Int, StringResource> = mapOf(
        0x14 to Res.string.gs20_live_data_value_5_to_4,
        0x15 to Res.string.gs20_live_data_value_4_to_3,
        0x16 to Res.string.gs20_live_data_value_3_to_2,
        0x17 to Res.string.gs20_live_data_value_2_to_1,
        0x20 to Res.string.gs20_live_data_value_1_to_2,
        0x21 to Res.string.gs20_live_data_value_2_to_3,
        0x22 to Res.string.gs20_live_data_value_3_to_4,
        0x23 to Res.string.gs20_live_data_value_4_to_5,
        0x48 to Res.string.gs20_live_data_value_pn_to_d,
        0x49 to Res.string.gs20_live_data_value_r_to_d,
        0x4A to Res.string.gs20_live_data_value_pn_to_r,
        0x4B to Res.string.gs20_live_data_value_d_to_r
    )

    private val checksumErrorMap: Map<Int, StringResource> = mapOf(
        0x00 to Res.string.gs20_live_data_value_rom,
        0x01 to Res.string.gs20_live_data_value_program,
        0x02 to Res.string.gs20_live_data_value_data
    )

    private val errorFreezeFrame: Map<Int, GS20FreezeFrameData> = mapOf(
        0x01 to GS20FreezeFrameData("", 0.toByte(), 1f, 0f, 0f, null),
        0x02 to GS20FreezeFrameData("%", 0.toByte(), 100f, 255f, 0f, null),
        0x03 to GS20FreezeFrameData("%", 0.toByte(), 100f, 255f, 0f, null),
        0x04 to GS20FreezeFrameData("rpm", 0.toByte(), 16f, 1f, 0f, null),
        0x05 to GS20FreezeFrameData("rpm", 0.toByte(), 32f, 1f, 0f, null),
        0x06 to GS20FreezeFrameData("rpm", 0.toByte(), 32f, 1f, 0f, null),
        0x07 to GS20FreezeFrameData("ºC", 0.toByte(), 1f, 1f, -40f, null),
        0x08 to GS20FreezeFrameData("ºC", 0.toByte(), 1f, 1f, -40f, null),
        0x09 to GS20FreezeFrameData("V", 0.toByte(), 1f, 10f, 0f, null),
        0x0A to GS20FreezeFrameData("km", 0.toByte(), 1f, 1f, 0f, null),
        0x0B to GS20FreezeFrameData("", 0x80.toByte(), 1f, 1f, 0f, booleanMap),
        0x0C to GS20FreezeFrameData("", 0x40.toByte(), 1f, 1f, 0f, booleanMap),
        0x0D to GS20FreezeFrameData("", 0x20.toByte(), 1f, 1f, 0f, booleanMap),
        0x0E to GS20FreezeFrameData("", 0x02.toByte(), 1f, 1f, 0f, booleanMap),
        0x0F to GS20FreezeFrameData("", 0x01.toByte(), 1f, 1f, 0f, booleanMap),
        0x10 to GS20FreezeFrameData("", 0x80.toByte(), 1f, 1f, 0f, booleanMap),
        0x11 to GS20FreezeFrameData("", 0x40.toByte(), 1f, 1f, 0f, booleanMap),
        0x12 to GS20FreezeFrameData("", 0x20.toByte(), 1f, 1f, 0f, booleanMap),
        0x13 to GS20FreezeFrameData("", 0x10.toByte(), 1f, 1f, 0f, booleanMap),
        0x14 to GS20FreezeFrameData("", 0x08.toByte(), 1f, 1f, 0f, booleanMap),
        0x15 to GS20FreezeFrameData("", 0x04.toByte(), 1f, 1f, 0f, booleanMap),
        0x16 to GS20FreezeFrameData("", 0x02.toByte(), 1f, 1f, 0f, booleanMap),
        0x17 to GS20FreezeFrameData("", 0x01.toByte(), 1f, 1f, 0f, booleanMap),
        0x18 to GS20FreezeFrameData("", 0xE0.toByte(), 1f, 1f, 0f, gearMap),
        0x19 to GS20FreezeFrameData("", 0x10.toByte(), 1f, 1f, 0f, booleanMap),
        0x1A to GS20FreezeFrameData("", 0x08.toByte(), 1f, 1f, 0f, booleanMap),
        0x1B to GS20FreezeFrameData("", 0x02.toByte(), 1f, 1f, 0f, booleanMap),
        0x1C to GS20FreezeFrameData("", 0x01.toByte(), 1f, 1f, 0f, booleanMap),
        0x1D to GS20FreezeFrameData("", 0x80.toByte(), 1f, 1f, 0f, booleanMap),
        0x1E to GS20FreezeFrameData("", 0x40.toByte(), 1f, 1f, 0f, booleanMap),
        0x21 to GS20FreezeFrameData("", 0x7F.toByte(), 1f, 1f, 0f, lastChangeMap),
        0x22 to GS20FreezeFrameData("", 0x07.toByte(), 1f, 1f, 0f, checksumErrorMap),
        0x23 to GS20FreezeFrameData("", 0x04.toByte(), 1f, 1f, 0f, booleanMap),
        0x24 to GS20FreezeFrameData("rpm", 0.toByte(), 32f, 1f, 0f, null),
        0x25 to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, null)
    )

    private val errorMatrix: Map<Int, Array<ByteArray>> = mapOf(
        0x01 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x04 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x10 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x11 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x12 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x13 to arrayOf(byteArrayOf(0x0B, 0x0C, 0x0D, 0x23, 0x0E, 0x0F),byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x20 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x24),byteArrayOf(0x0A)),
        0x21 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x05),byteArrayOf(0x0A)),
        0x22 to arrayOf(byteArrayOf(0x06), byteArrayOf(0x07),byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x30 to arrayOf(byteArrayOf(0x25),byteArrayOf(0x08),byteArrayOf(0x0B, 0x0C, 0x0D, 0x23, 0x0E, 0x0F),byteArrayOf(0x0A)),
        0x32 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x34 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x35 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x36 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x37 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x3B to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x02),byteArrayOf(0x0A)),
        0x3C to arrayOf(byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),byteArrayOf(0x05),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x3D to arrayOf(byteArrayOf(0x06), byteArrayOf(0x07), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x3F to arrayOf(byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),byteArrayOf(0x05),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x50 to arrayOf(byteArrayOf(0x06), byteArrayOf(0x22), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x51 to arrayOf(byteArrayOf(0x09), byteArrayOf(0x09), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x52 to arrayOf(byteArrayOf(0x09), byteArrayOf(0x09), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x53 to arrayOf(byteArrayOf(0x09), byteArrayOf(0x09), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x54 to arrayOf(byteArrayOf(0x09), byteArrayOf(0x09), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x60 to arrayOf(byteArrayOf(0x06), byteArrayOf(0x07), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x61 to arrayOf(byteArrayOf(0x09), byteArrayOf(0x09), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x70 to arrayOf(byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),byteArrayOf(0x06),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x71 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x02),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x72 to arrayOf(byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),byteArrayOf(0x25),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x73 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x06),byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),byteArrayOf(0x0A)),
        0x74 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x06),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x75 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x06),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x76 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x06),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x77 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x06),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x78 to arrayOf(byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),byteArrayOf(0x06),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x79 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x05),byteArrayOf(0x0A)),
        0x80 to arrayOf(byteArrayOf(0x05),byteArrayOf(0x08),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x81 to arrayOf(byteArrayOf(0x06), byteArrayOf(0x05), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x82 to arrayOf(byteArrayOf(0x06), byteArrayOf(0x05), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x83 to arrayOf(byteArrayOf(0x06), byteArrayOf(0x05), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x90 to arrayOf(byteArrayOf(0x01), byteArrayOf(0x08), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x91 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x05),byteArrayOf(0x03),byteArrayOf(0x0A)),
        0x92 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x06),byteArrayOf(0x02),byteArrayOf(0x0A)),
        0x93 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x06),byteArrayOf(0x05),byteArrayOf(0x0A)),
        0x94 to arrayOf(byteArrayOf(0x06), byteArrayOf(0x08), byteArrayOf(0x09), byteArrayOf(0x0A)),
        0x95 to arrayOf(byteArrayOf(0x01D, 0x21),byteArrayOf(0x05),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x96 to arrayOf(byteArrayOf(0x01D, 0x21),byteArrayOf(0x05),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x97 to arrayOf(byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),byteArrayOf(0x05),byteArrayOf(0x09),byteArrayOf(0x0A))
    )

    /** Flagi rodzaju bledu (gorny polbajt), dolny polbajt to wartosc z tabeli FARTTEXTE */
    private val errorTypeFlags = intArrayOf(0x10, 0x20, 0x40, 0x80)

    override fun getKoinName() = KoinNames.ModuleL322Gs20

    override fun getImageResource() = Res.drawable.module_gearbox

    override fun getModuleDescription() = Res.string.module_description_gs20

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        /*
        val fakeAnswer = byteArrayOf(
            0x32,0x24,0xA0.toByte(),0x00,0x00,0x00,0x00,0x00,0x00,0x00,
            0x00,0x64,0xA0.toByte(),0x00,0x00,0x00,0x00,0x00,0x00,0x00,
            0x00,0x00,0x00,0x80.toByte(),0x4b,0x00,0x00,0x00,0xFF.toByte(),
            0x02,0xA4.toByte(),0x22,0x0C,0x35,0x7C.toByte(),0x9B.toByte())
        */
        val result: MutableMap<Int, ByteArray> = HashMap()
        val message = interfaceWrite(byteArrayOf(0x0B, 0x03))
        //message.valid = true;
        //message.answer = fakeAnswer;
        try {
            if (message.isValid) {
                result[0x00] = message.answer.copyOfRange(3, message.answer.size)
                return result
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e {"$tag [readLiveData], $ex, $message"}
        }
        return null
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean  = true

    /**
     * Blok 0x00 - STEUERN_STELLGLIED (SGBD GS20): 0C PIN TASTVERHAELTNIS 00, PIN z tabeli STELLGLIEDER,
     * wartosc (jak w SGBD GS 8.60.1): 0x00 - nie steruj, 0x01-0xFE - takt sekundowy, 0xFF - sterowanie ciagle.
     * Odpowiedz [4] - STAT_ANSTEUERUNG (0 - sterowanie poprawne).
     * Blok 0x01 - RESET_ADAPTION: 43 00.
     */
    override suspend fun control(controlElement: ControlElement): Boolean {
        try {
            when (controlElement.block) {
                0x00 -> {
                    val setValue = controlElement.controlSetValue ?: return false
                    val message = interfaceWrite(
                        byteArrayOf(0x0C, controlElement.byteInBlock.toByte(), setValue.value.coerceIn(0, 0xFF).toByte(), 0x00)
                    )
                    if (!message.isValid)
                        return false
                    // [0] adres, [1] dlugosc, [2] status, [3] PIN, [4] STAT_ANSTEUERUNG, [5] suma kontrolna
                    return message.answer.size < 6 || message.answer[4].toInt() == 0x00
                }

                0x01 -> return interfaceWrite(byteArrayOf(0x43, 0x00)).isValid
                else -> {}
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e {"$tag [control], $ex"}
        }
        return false
    }

    override suspend fun getIdentification(): ModuleInfo? {
        val mi = ModuleInfo()
        val message = interfaceWrite(byteArrayOf(0x00),200)
        try {
            if (message.isValid) {
                val answer = message.answer
                mi.id1 = String(answer.copyOfRange(3, 6)) + String.format(
                    "%02X%02X%02X",
                    answer[6],
                    answer[7],
                    answer[8]
                )
                mi.hw = String(answer.copyOfRange(10, 12))
                mi.ci = String(answer.copyOfRange(12, 14))
                mi.di = String(answer.copyOfRange(14, 16))
                mi.bi = String(answer.copyOfRange(16, 18))
                mi.date =
                    String(answer.copyOfRange(18, 20)) + "/" + String(answer.copyOfRange(20, 22))
                mi.serial = String(answer.copyOfRange(22, 32))
                mi.sw = String(answer.copyOfRange(33, 35))
                val ai = Pair(
                    Res.string.module_info_change_type,
                    String(answer.copyOfRange(35, 37))
                )
                val prod = Pair(
                    Res.string.module_info_product_number,
                    String(answer.copyOfRange(37, 46))
                )
                mi.additionalInfo = ArrayList()
                mi.additionalInfo.add(ai)
                mi.additionalInfo.add(prod)
            }
            if (mi.ci.isEmpty()) {
                reset()
                delay(1000)
                return ModulesFunction.getModuleInfo(interfaceWrite(byteArrayOf(0x00)))
            }
        }catch (ex: CancellationException) {
            throw ex
        } catch ( ex : Exception)
        {
            Logger.e {"$tag [getIdentification], $ex, $message"}
        }
        return mi
    }

    /**
     * FS_LESEN (SGBD GS20): 04 01, answer[3] - liczba bledow (max 5), rekordy po 0x13 bajtow od answer[4]:
     * +0 kod bledu, +1 rodzaj (dolny polbajt - wartosc z FARTTEXTE, bity 0x10..0x80 - flagi),
     * +2 licznik wystapien, +3 licznik CARB, +4.. 3 zestawy danych zamrozonych po 5 bajtow
     * (3 warunki + km 16 bit big endian), wazne tylko min(licznik wystapien, 3) zestawow.
     */
    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        val message = interfaceWrite(byteArrayOf(0x04, 0x01))
        try {
            if (message.isValid) {
                result = ArrayList()
                val answer = message.answer
                val errorCount = minOf(answer[3].toInt() and 0xFF, MAX_ERRORS)
                for (n in 0 until errorCount) {
                    val start = 4 + n * ERROR_RECORD_SIZE
                    if (start + ERROR_RECORD_SIZE > answer.size)
                        break
                    val error = answer[start].toInt() and 0xFF
                    val errorType = answer[start + 1].toInt() and 0xFF
                    val frequency = answer[start + 2].toInt() and 0xFF
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()

                    // Wartosc 0x00 to "allg. Fehler" - tekst trzymany pod kluczem FE
                    val errorTypeValue = errorType and 0x0F
                    ResourceMapper
                        .stringResource(String.format("gs20_error_detail_%02X", if (errorTypeValue == 0) 0xFE else errorTypeValue))
                        ?.let { errorDescription.add(Pair(it, null)) }
                    for (flag in errorTypeFlags) {
                        if ((errorType and flag) == flag)
                            ResourceMapper
                                .stringResource(String.format("gs20_error_detail_%02X", flag))
                                ?.let { errorDescription.add(Pair(it, null)) }
                    }
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount(frequency))
                    errorDescription.add(
                        ResourceHelper.getErrorDescriptionLogisticCounter(answer[start + 3].toInt() and 0xFF)
                    )

                    val localErrorMatrix = errorMatrix[error]
                    if (localErrorMatrix != null) {
                        for (m in 0 until minOf(frequency, FREEZE_FRAME_SETS)) {
                            errorDescription.add(
                                Pair(
                                    Res.string.dde_error_freeze_frame_number,
                                    (m + 1).toString()
                                )
                            )
                            for (x in localErrorMatrix.indices) {
                                val currentByte = start + 4 + (5 * m) + x
                                var b = answer[currentByte].toInt() and 0xFF
                                for (y in localErrorMatrix[x].indices) {
                                    val frameId = localErrorMatrix[x][y].toInt() and 0xFF
                                    val freezeFrameData = errorFreezeFrame[frameId] ?: continue
                                    if (frameId == 0x0A) {
                                        b = (answer[currentByte].toInt() and 0xFF) * 256
                                        b += answer[currentByte + 1].toInt() and 0xFF
                                    }
                                    val name = ResourceMapper
                                        .stringResource(String.format("gs20_error_detail_freeze_frame_%02X", frameId))
                                        ?: continue
                                    if (freezeFrameData.mask.toInt() != 0x00) {
                                        val c = b and (freezeFrameData.mask.toInt() and 0xFF)
                                        freezeFrameData.values?.get(c)?.let { errorDescription.add(Pair(name, it)) }
                                    } else {
                                        val c = (b.toFloat() * freezeFrameData.multiplier / freezeFrameData.divider + freezeFrameData.adder).toDouble()
                                        errorDescription.add(
                                            Pair(
                                                name,
                                                String.format(Locale.getDefault(), "%.2f %s", c, freezeFrameData.unit)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    result.add(
                        ModuleError(
                            ModuleAddress.MODULE_GS20,
                            String.format("%02X", error),
                            errorDescription
                        )
                    )
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch ( ex : Exception)
        {
            Logger.e {"$tag [getAllErrors], $ex, $message"}
        }
        return result
    }

    private companion object {
        const val MAX_ERRORS = 5
        const val ERROR_RECORD_SIZE = 0x13
        const val FREEZE_FRAME_SETS = 3
        const val MEMORY_DEFAULT = 0x00
        const val MEMORY_EEPROM = 0x03
        const val FLASH_BLOCK_SIZE = 0x80
        const val MAX_BAUD_RATES = 8
        const val DEFAULT_BAUD_RATE = 9600L
        const val FAST_BAUD_RATE = 125000L
        // Rozmiar nie jest podany w SGBD - do potwierdzenia odczytem w aucie
        const val EEPROM_SIZE = 0x100
        const val EEPROM_BLOCK_SIZE = 0x10
    }

    /**
     * SGBD GS20 nie zawiera programowania flash - SPEICHER_SCHREIBEN pozwala zapisywac tylko
     * EEPROM / RAM, brak kasowania i trybu bootloadera (procedura jest w plikach WinKFP).
     */
    override suspend fun writeFlash(data: ByteArray) {
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_main_function_not_supported_yet))
    }

    override suspend fun readFlash() {
        val startAddress: Long = 0x090000
        val size: Long = 0x010000
        var result = ByteArray(0)
        val startTime = Calendar.getInstance().time
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_read_identification))

        val moduleInfo = getIdentification()
        if (moduleInfo != null) {
            _programmingState.tryEmit(FlashProgrammingState.Message("ID: ${moduleInfo.id1}"))
        }

        val pointers = interfaceWrite(byteArrayOf(0x0D))
        val blockSize = readMaxDataBlockLength(pointers)
        val baudRates = readSupportedBaudRates(pointers)
        _programmingState.tryEmit(FlashProgrammingState.Message(String.format("Block: 0x%02X", blockSize)))
        if (baudRates != null)
            _programmingState.tryEmit(FlashProgrammingState.Message("Baud: " + baudRates.joinToString(", ")))

        val endAddress = startAddress + size
        val steps = (size.toInt() + blockSize - 1) / blockSize
        var x = 0f
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_flash_reading))
        // Gdy sterownik nie podal tablicy predkosci - proba jak dotychczas, gdy podal - tylko jesli 125000 jest na liscie
        val fastBaudRate = baudRates == null || baudRates.contains(FAST_BAUD_RATE)
        if (!fastBaudRate || setEcuBaudRate(FAST_BAUD_RATE)) {
            var i = startAddress
            while (i < endAddress) {
                _programmingState.tryEmit(FlashProgrammingState.Progress(((x++ * 100) / steps).toInt()))
                val currentBlockSize = minOf(blockSize.toLong(), endAddress - i).toInt()
                val data = readFlashData(i, currentBlockSize)
                if (data.size == currentBlockSize) {
                    result += data
                } else {
                    _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_read_error))
                    if (fastBaudRate)
                        setEcuBaudRate(DEFAULT_BAUD_RATE)
                    return
                }
                i += currentBlockSize.toLong()
            }

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

            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_end))
            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_close))

            try {
                result = result.copyOfRange(0, (endAddress - startAddress).toInt())
                _programmingState.tryEmit(
                    FlashProgrammingState.FlashData(
                        result.toList(),
                        moduleInfo,
                        ""
                    )
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [readFlash]: ${ex.toString()}" }
            }
            if (fastBaudRate)
                setEcuBaudRate(DEFAULT_BAUD_RATE)
        }
        else
        {
            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_read_error))
            setEcuBaudRate(DEFAULT_BAUD_RATE)
            return
        }
    }

    private suspend fun readFlashData(address: Long, size: Int): ByteArray =
        readMemory(MEMORY_DEFAULT, address, size) ?: ByteArray(0)

    /**
     * SPEICHER_LESEN: 06 TYP A2 A1 A0 N, dane od answer[3] bez sumy kontrolnej.
     * Typy z tabeli SPEICHER: 00 DEFAULT, 03 EEPROM, 06 FLASH_EPROM, 07 LOG_ADR_RAM.
     */
    private suspend fun readMemory(memoryType: Int, address: Long, size: Int): ByteArray? {
        val message = interfaceWrite(
            byteArrayOf(
                0x06,
                memoryType.toByte(),
                ((address and 0xFF0000L) shr 16).toByte(),
                ((address and 0x00FF00L) shr 8).toByte(),
                ((address and 0x0000FFL)).toByte(),
                ((size and 0xFF)).toByte()
            )
        )
        if (message.isValid) {
            try {
                val data = message.answer.copyOfRange(3, message.answer.size - 1)
                if (data.size == size)
                    return data
                Logger.e { "$tag [readMemory]: size ${data.size} != $size, $message" }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [readMemory]: ${ex.toString()}, $message" }
            }
        }
        return null
    }

    /** Wskaznik 24 bit z odpowiedzi na 0D (tablica wskaznikow, indeksy jak w SGBD - od bajtu adresu) */
    private fun getPointer(pointers: OBDMessage, offset: Int): Long? {
        if (!pointers.isValid || pointers.answer.size < offset + 4)
            return null
        val answer = pointers.answer
        return ((answer[offset].toLong() and 0xFF) shl 16) or
                ((answer[offset + 1].toLong() and 0xFF) shl 8) or
                (answer[offset + 2].toLong() and 0xFF)
    }

    /**
     * MAX_BLOCKLAENGE: wskaznik 0D[0x42..0x44], 1 bajt - maksymalna dlugosc telegramu.
     * Dane w odpowiedzi = MBL - 4 (adres, dlugosc, status, suma kontrolna), max FLASH_BLOCK_SIZE.
     */
    private suspend fun readMaxDataBlockLength(pointers: OBDMessage): Int {
        val pointer = getPointer(pointers, 0x42) ?: return FLASH_BLOCK_SIZE
        val mbl = readMemory(MEMORY_DEFAULT, pointer, 1)?.get(0)?.toInt()?.and(0xFF) ?: return FLASH_BLOCK_SIZE
        return if (mbl > 0x14) minOf(mbl - 4, FLASH_BLOCK_SIZE) else FLASH_BLOCK_SIZE
    }

    /** BAUDRATE_LESEN: wskaznik 0D[0x48..0x4A], wpisy po 3 bajty (big endian) do FF FF FF */
    private suspend fun readSupportedBaudRates(pointers: OBDMessage): List<Long>? {
        val pointer = getPointer(pointers, 0x48) ?: return null
        val result = ArrayList<Long>()
        for (n in 0 until MAX_BAUD_RATES) {
            val entry = readMemory(MEMORY_DEFAULT, pointer + 3 * n, 3) ?: return null
            val baudRate = ((entry[0].toLong() and 0xFF) shl 16) or
                    ((entry[1].toLong() and 0xFF) shl 8) or
                    (entry[2].toLong() and 0xFF)
            if (baudRate == 0xFFFFFFL)
                break
            result.add(baudRate)
        }
        return result.ifEmpty { null }
    }

    override fun getEepromSize(): Int = EEPROM_SIZE

    override fun getEepromReadSize(): Int = EEPROM_BLOCK_SIZE

    override fun getEepromWriteSize(): Int = EEPROM_BLOCK_SIZE

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? =
        readMemory(MEMORY_EEPROM, address.toLong(), size)

    /**
     * Zapis EEPROM (SPEICHER_SCHREIBEN 07 03 A2 A1 A0 N dane, N w bajtach) celowo wylaczony
     * do czasu weryfikacji w aucie - po zapisie wymagana kontrola CODIER_CS_PRUEFEN (0A).
     */
    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean = false

    override val eepromWriteAvailable: Boolean = false

    override val flashProgrammingAvailable: Boolean = false

    private suspend fun setEcuBaudRate(baudRate : Long) : Boolean
    {
        if (protocolDS2 != null) {
            val message = interfaceWrite(byteArrayOf(0x91.toByte(),
                ((baudRate and 0xFF0000L) shr 16).toByte(),
                ((baudRate and 0x00FF00L) shr 8).toByte(),
                ((baudRate and 0x0000FFL)).toByte(),
                0x01))
            if (message.isValid) {
                protocolDS2.setSpeed(baudRate.toInt())
                return true
            }
        }
        return false
    }
}
