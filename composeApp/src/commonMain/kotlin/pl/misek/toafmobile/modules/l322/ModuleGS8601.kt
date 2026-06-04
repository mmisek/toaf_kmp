package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.generated.ResourceMapperGs8601
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.containers.GS20FreezeFrameData
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Locale

class ModuleGS8601(protocolDS2: ProtocolDS2?) :ModuleDS2( protocolDS2, ModuleAddress.MODULE_GS8601, listOf(CarType.L322)),IModuleLiveData, IModuleEepromProgramming {
    private var tag = "ModuleGS8601"
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
        0x01 to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, null),
        0x02 to GS20FreezeFrameData("%", 0.toByte(), 100f, 255f, 0f, null),
        0x03 to GS20FreezeFrameData("%", 0.toByte(), 100f, 255f, 0f, null),
        0x04 to GS20FreezeFrameData("rpm", 0.toByte(), 16f, 1f, 0f, null),
        0x05 to GS20FreezeFrameData("rpm", 0.toByte(), 32f, 1f, 0f, null),
        0x06 to GS20FreezeFrameData("rpm", 0.toByte(), 32f, 1f, 0f, null),
        0x07 to GS20FreezeFrameData("ºC", 0.toByte(), 1f, 1f, -48f, null),
        0x08 to GS20FreezeFrameData("ºC", 0.toByte(), 1f, 1f, -40f, null),
        0x09 to GS20FreezeFrameData("V", 0.toByte(), 16f, 198f, 0f, null),
        0x0A to GS20FreezeFrameData("km", 0.toByte(), 10f, 1f, 0f, null),
        0x0B to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, booleanMap),
        0x0C to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, booleanMap),
        0x0D to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, booleanMap),
        0x0E to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, booleanMap),
        0x0F to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, booleanMap),
        0x10 to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, booleanMap),
        0x11 to GS20FreezeFrameData("rpm", 0.toByte(), 32f, 1f, 0f, null),
    )

    private val errorMatrix: Map<Int, Array<Int>> = mapOf(
        0x01 to arrayOf(0x0C, 0x08, 0x09),
        0x02 to arrayOf(0x0C, 0x08, 0x09),
        0x03 to arrayOf(0x0C, 0x08, 0x09),
        0x04 to arrayOf(0x0C, 0x08, 0x09),
        0x05 to arrayOf(0x0C, 0x08, 0x09),
        0x0F to arrayOf(0x0C, 0x08, 0x09),
        0x10 to arrayOf(0x0C, 0x08, 0x09),
        0x11 to arrayOf(0x0C, 0x08, 0x09),
        0x12 to arrayOf(0x0C, 0x08, 0x09),
        0x13 to arrayOf(0x0A, 0x0B, 0x09),
        0x20 to arrayOf(0x0C, 0x08, 0x04),
        0x21 to arrayOf(0x0C, 0x09, 0x05),
        0x22 to arrayOf(0x06, 0x07, 0x09),
        0x30 to arrayOf(0x0C, 0x08, 0x0E),
        0x31 to arrayOf(0x0C, 0x08, 0x05),
        0x32 to arrayOf(0x02, 0x04, 0x05),
        0x33 to arrayOf(0x02, 0x04, 0x05),
        0x34 to arrayOf(0x02, 0x04, 0x05),
        0x35 to arrayOf(0x02, 0x04, 0x05),
        0x36 to arrayOf(0x02, 0x04, 0x05),
        0x37 to arrayOf(0x02, 0x04, 0x05),
        0x38 to arrayOf(0x0C, 0x08, 0x05),
        0x39 to arrayOf(0x0C, 0x08, 0x05),
        0x3A to arrayOf(0x0C, 0x08, 0x05),
        0x3B to arrayOf(0x0C, 0x08, 0x02),
        0x3C to arrayOf(0x0B, 0x05, 0x09),
        0x3D to arrayOf(0x0C, 0x08, 0x05),
        0x3E to arrayOf(0x0C, 0x08, 0x04),
        0x40 to arrayOf(0x02, 0x08, 0x05),
        0x41 to arrayOf(0x02, 0x11, 0x05),
        0x50 to arrayOf(0x06, 0x10, 0x09),
        0x51 to arrayOf(0x09, 0x09, 0x09),
        0x52 to arrayOf(0x09, 0x09, 0x09),
        0x53 to arrayOf(0x09, 0x09, 0x09),
        0x60 to arrayOf(0x06, 0x07, 0x09),
        0x61 to arrayOf(0x09, 0x09, 0x09),
        0x70 to arrayOf(0x0B, 0x05, 0x09),
        0x71 to arrayOf(0x0C, 0x02, 0x09),
        0x72 to arrayOf(0x0B, 0x0D, 0x09),
        0x80 to arrayOf(0x05, 0x08, 0x09),
        0x81 to arrayOf(0x06, 0x05, 0x09),
        0x82 to arrayOf(0x0C, 0x06, 0x0B),
        0x84 to arrayOf(0x06, 0x05, 0x09),
        0x85 to arrayOf(0x0B, 0x06, 0x04),
        0x90 to arrayOf(0x01, 0x08, 0x09),
        0x91 to arrayOf(0x06, 0x0C, 0x09),
        0x93 to arrayOf(0x0C, 0x06, 0x05),
        0x95 to arrayOf(0x0C, 0x09, 0x05),
        0x97 to arrayOf(0x09, 0x05, 0x0B),
        0x98 to arrayOf(0x06, 0x05, 0x02),
        0x99 to arrayOf(0x0B, 0x05, 0x04),
        0xFF to arrayOf(0xFF, 0xFF, 0xFF),
    )

    private val errorDetails = byteArrayOf(
        0x01,0x02,0x03,0x04,0x05,0x06,0x0A,0x0B,
        0x0C,0x0D,0x10,0x20,0x40,0x80.toByte(),0xFE.toByte()
    )

    override fun getKoinName() = KoinNames.ModuleL322Gs8601

    override fun getImageResource() = Res.drawable.module_gearbox

    override fun getModuleDescription() = Res.string.module_description_gs8601

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
        if (message.isValid) {
            result[0x00] = message.answer.copyOfRange(3, message.answer.size)
            return result
        }
        return null
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean  = true

    override suspend fun getIdentification(): ModuleInfo? {
        val mi = ModuleInfo()
        val message = interfaceWrite(byteArrayOf(0x00))
        if (message.isValid) {
            val answer = message.answer
            mi.id1 = String(answer.copyOfRange(3, 10))
            mi.hw = String(answer.copyOfRange(10, 12))
            mi.ci = String(answer.copyOfRange(12, 14))
            mi.di = String(answer.copyOfRange(14, 16))
            mi.bi = String(answer.copyOfRange(16, 18))
            mi.date = String(answer.copyOfRange(18, 20)) + "/" + String(answer.copyOfRange(20, 22))
            mi.serial = String(answer.copyOfRange(22, 32))
            mi.sw = String(answer.copyOfRange(32, 34))
            val ai = Pair(
                Res.string.module_info_change_type,
                String(answer.copyOfRange(34, 36))
            )
            val prod = Pair(
                Res.string.module_info_product_number,
                String(answer.copyOfRange(36, 45))
            )
            mi.additionalInfo = ArrayList()
            mi.additionalInfo.add(ai)
            mi.additionalInfo.add(prod)
            return  mi
        }
        return null
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        val nextErrorOffset = 0x13
        var result: MutableList<ModuleError>? = null
        val message = interfaceWrite(byteArrayOf(0x04,0x01))
        if (message.isValid) {
            try {
                val errorCount = message.answer[3].toInt() and 0xFF
                result = ArrayList()
                for( n in 0..<errorCount)
                {
                    val error = message.answer[4 + n * nextErrorOffset].toInt() and 0xFF
                    val errorCode = String.format("%02X", error)
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()

                    for (j in errorDetails.indices) {
                        if ((message.answer[5 + n * nextErrorOffset].toInt() and errorDetails[j].toInt()) == errorDetails[j].toInt()) {
                            val errorDetailName = String.format("gs8601_error_detail_%02X", errorDetails[j])
                            ResourceMapper.stringResource(errorDetailName)?.let { errorDescription.add(Pair(it, null)) }
                        }
                    }

                    errorDescription.add(
                        ResourceHelper.getErrorDescriptionCount(
                            (message.answer[7 + n * nextErrorOffset].toInt() and 0xFF)
                        )
                    )

                    val freezeFrameCount = message.answer[6 + n * nextErrorOffset].toInt() and 0xFF
                    var freezeFrameNumber = 0
                    repeat(freezeFrameCount)
                    {
                        errorDescription.add(
                            Pair(
                                Res.string.dde_error_freeze_frame_number,
                                (freezeFrameNumber + 1).toString()
                            )
                        )
                        val localErrorMatrix = errorMatrix[error]
                        if (localErrorMatrix != null) {
                            for (i in 0..<localErrorMatrix.size) {
                                val eff = errorFreezeFrame[localErrorMatrix[i]]
                                if (eff != null) {
                                    val rawValue = (message.answer[(i + 8) + 5 * freezeFrameNumber + n * nextErrorOffset].toInt() and 0xFF)
                                    val value = rawValue.toFloat() * eff.multiplier / eff.divider + eff.adder
                                    val detailName =
                                        String.format("gs8601_error_detail_freeze_frame_%02X", localErrorMatrix[i]).lowercase()
                                    if (eff.unit.isEmpty()) {
                                        ResourceMapperGs8601.stringResource(detailName)?.let {
                                            errorDescription.add(
                                                Pair(
                                                    it,
                                                    String.format(
                                                        Locale.getDefault(),
                                                        "%.2f",
                                                        value
                                                    )
                                                )
                                            )
                                        }
                                    } else {
                                        ResourceMapperGs8601.stringResource(detailName)?.let {
                                            errorDescription.add(
                                                Pair(
                                                    it,
                                                    String.format(
                                                        Locale.getDefault(),
                                                        "%.2f %s",
                                                        value,
                                                        eff.unit
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            val odometer =
                                ((message.answer[11 + 5 * freezeFrameNumber + n * nextErrorOffset].toInt() and 0xFF) shl 8) + (message.answer[12 + 5 * freezeFrameNumber + n * nextErrorOffset].toInt() and 0xFF)
                            errorDescription.add(
                                Pair(
                                    Res.string.dde_error_ff_odometer,
                                    String.format(Locale.getDefault(), "%d km", odometer)
                                )
                            )
                        }
                        freezeFrameNumber++
                    }
                    result.add(ModuleError(ModuleAddress.MODULE_GS8601, errorCode, errorDescription))
                }
            } catch (ex: Exception) {
                Logger.e { "$tag [getIdentification], $ex, $message" }
            }
        }
        return result
    }
    override fun getEepromSize(): Int  = 256

    override fun getEepromReadSize(): Int  = 0x10

    override fun getEepromWriteSize(): Int  = 0x10

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? {
        val message = interfaceWrite(
            byteArrayOf(
                0x06,
                0x03,
                0x00,
                0x00,
                (address and 0xFF).toByte(),
                size.toByte()))
        try {
            return if (message.isValid) message.answer.copyOfRange(0x03, 3 + size) else null
        } catch (ex : Exception) {
            Logger.e {"$tag [readEeprom], $ex, $message"}
            return null
        }
    }

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val size = data.size
        val dataToWrite =
            byteArrayOf(
                0x07,
                0x03,
                0x00,
                0x00,
                (address and 0xFF).toByte(),
                (size / 2).toByte()) + data
        return interfaceWrite(dataToWrite).isValid
    }
}
