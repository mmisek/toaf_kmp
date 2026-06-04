package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
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
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Locale

class ModuleGS20( protocolDS2: ProtocolDS2?) :ModuleDS2( protocolDS2, ModuleAddress.MODULE_GS20, listOf(CarType.L322)),IModuleLiveData {
    private var tag = "ModuleGS20"
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
        0x0A to GS20FreezeFrameData("km", 0.toByte(), 10f, 1f, 0f, null),
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
        0x70 to arrayOf(byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),byteArrayOf(0x06),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x71 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x02),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x72 to arrayOf(byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),byteArrayOf(0x25),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x78 to arrayOf(byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17),byteArrayOf(0x06),byteArrayOf(0x09),byteArrayOf(0x0A)),
        0x80 to arrayOf(byteArrayOf(0x1D, 0x21),byteArrayOf(0x08),byteArrayOf(0x09),byteArrayOf(0x0A)),
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

    private val errorDetails = byteArrayOf(
        0x01,0x02,0x03,0x04,0x05,0x06,0x0A,0x0B,
        0x0C,0x0D,0x10,0x20,0x40,0x80.toByte(),0xFE.toByte()
    )

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
        } catch (ex: Exception) {
            Logger.e {"$tag [readLiveData], $ex, $message"}
        }
        return null
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean  = true

    override suspend fun getIdentification(): ModuleInfo? {
        val mi = ModuleInfo()
        val message = interfaceWrite(byteArrayOf(0x00))
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
        }catch ( ex : Exception)
        {
            Logger.e {"$tag [getIdentification], $ex, $message"}
        }
        return mi
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        val nextErrorOffset = 0x13
        var result: MutableList<ModuleError>? = null
        val message = interfaceWrite(byteArrayOf(0x04, 0x01))
        try {
            if (message.isValid) {
                result = ArrayList()
                for (n in 0 until (message.answer[3].toInt() and 0xFF)) {
                    val error = message.answer[4 + n * nextErrorOffset].toInt() and 0xFF
                    val errorCode = String.format("%02X", error)
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()

                    for (j in errorDetails.indices) {
                        if ((message.answer[5 + n * nextErrorOffset].toInt() and errorDetails[j].toInt()) == errorDetails[j].toInt()) {
                            String.format("gs20_error_detail_%02X", errorDetails[j])
                            ResourceMapper
                                .stringResource(
                                    String.format(
                                        "gs20_error_detail_%02X",
                                        errorDetails[j]
                                    )
                                )
                                ?.let { errorDescription.add(Pair(it, null)) }
                        }

                        errorDescription.add(
                            ResourceHelper.getErrorDescriptionCount(
                                (message.answer[6 + n * nextErrorOffset].toInt() and 0xFF)
                            )
                        )
                        errorDescription.add(
                            ResourceHelper.getErrorDescriptionLogisticCounter(
                                (message.answer[7 + n * nextErrorOffset].toInt() and 0xFF)
                            )
                        )

                        val localErrorMatrix =
                            errorMatrix[message.answer[4 + n * nextErrorOffset].toInt() and 0xFF]
                        if (localErrorMatrix != null) {
                            for (m in 0..2) {
                                errorDescription.add(
                                    Pair(
                                        Res.string.dde_error_freeze_frame_number,
                                        (m + 1).toString()
                                    )
                                )
                                for (x in localErrorMatrix.indices) {
                                    val currentByte = 8 + x + (n * nextErrorOffset) + (5 * m)
                                    var b = message.answer[currentByte].toInt() and 0xFF
                                    for (y in localErrorMatrix[x].indices) {
                                        val freezeFrameData =
                                            errorFreezeFrame[localErrorMatrix[x][y].toInt() and 0xFF]
                                        if ((localErrorMatrix[x][y].toInt() and 0xFF) == 0x0A) {
                                            b = (message.answer[currentByte].toInt() and 0xFF) * 256
                                            b += message.answer[currentByte + 1].toInt() and 0xFF
                                        }
                                        if (freezeFrameData != null) {
                                            if (freezeFrameData.mask.toInt() != 0x00) {
                                                val c = (b and freezeFrameData.mask.toInt())
                                                if (freezeFrameData.values?.containsKey(c) == true)
                                                    ResourceMapper.stringResource(
                                                        String.format(
                                                            "gs20_error_detail_freeze_frame_%02X",
                                                            localErrorMatrix[x][y].toInt() and 0xFF
                                                        )
                                                    )
                                                        ?.let {
                                                            errorDescription.add(
                                                                Pair(
                                                                    it,
                                                                    freezeFrameData.values.get(c)
                                                                )
                                                            )
                                                        }
                                            } else {
                                                val c =
                                                    (b.toFloat() * freezeFrameData.multiplier / freezeFrameData.divider + freezeFrameData.adder).toDouble()
                                                String.format(
                                                    "gs20_error_detail_freeze_frame_%02X",
                                                    localErrorMatrix[x][y].toInt() and 0xFF
                                                )
                                                ResourceMapper
                                                    .stringResource(
                                                        String.format(
                                                            "gs20_error_detail_freeze_frame_%02X",
                                                            localErrorMatrix[x][y].toInt() and 0xFF
                                                        )
                                                    )
                                                    ?.let {
                                                        errorDescription.add(
                                                            Pair(
                                                                it,
                                                                String.format(
                                                                    Locale.getDefault(),
                                                                    "%.2f %s",
                                                                    c,
                                                                    freezeFrameData.unit
                                                                )
                                                            )
                                                        )
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        result.add(
                            ModuleError(
                                ModuleAddress.MODULE_GS20,
                                errorCode,
                                errorDescription
                            )
                        )
                    }
                }
            }
        } catch ( ex : Exception)
        {
            Logger.e {"$tag [getAllErrors], $ex, $message"}
        }
        return result
    }
}
