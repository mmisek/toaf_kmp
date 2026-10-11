package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.generated.ResourceMapperGs8601
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
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
import toafmobile.composeapp.generated.resources.*
import java.util.Calendar
import java.util.Locale

class ModuleGS8601(val protocolDS2: ProtocolDS2?) :
    ModuleDS2( protocolDS2, ModuleAddress.MODULE_GS8601, listOf(CarType.L322)),
    IModuleLiveData,
    IModuleEepromProgramming,
    IModuleFlashProgramming,
    IModuleControl {
    private var tag = "ModuleGS8601"
    private val _programmingState =  MutableSharedFlow<FlashProgrammingState>(replay = 10, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val programmingState = _programmingState.asSharedFlow()
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
        0x0A to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, null),
        0x0B to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, null),
        0x0C to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, null),
        0x0D to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, null),
        0x0E to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, null),
        0x0F to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, null),
        0x10 to GS20FreezeFrameData("", 0.toByte(), 1f, 1f, 0f, null),
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

    private val errorTypeFlags = intArrayOf(0x10, 0x20, 0x40, 0x80)

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

                    val errorTypeValue = errorType and 0x0F
                    ResourceMapper
                        .stringResource(String.format("gs8601_error_detail_%02X", if (errorTypeValue == 0) 0xFE else errorTypeValue))
                        ?.let { errorDescription.add(Pair(it, null)) }
                    for (flag in errorTypeFlags) {
                        if ((errorType and flag) == flag)
                            ResourceMapper
                                .stringResource(String.format("gs8601_error_detail_%02X", flag))
                                ?.let { errorDescription.add(Pair(it, null)) }
                    }
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount(frequency))
                    errorDescription.add(
                        ResourceHelper.getErrorDescriptionLogisticCounter(answer[start + 3].toInt() and 0xFF)
                    )

                    val localErrorMatrix = errorMatrix[error]
                    for (m in 0 until minOf(frequency, FREEZE_FRAME_SETS)) {
                        val setStart = start + 4 + 5 * m
                        errorDescription.add(
                            Pair(
                                Res.string.dde_error_freeze_frame_number,
                                (m + 1).toString()
                            )
                        )
                        localErrorMatrix?.forEachIndexed { i, frameId ->
                            val eff = errorFreezeFrame[frameId] ?: return@forEachIndexed
                            val detailName = String.format("gs8601_error_detail_freeze_frame_%02X", frameId).lowercase()
                            val name = ResourceMapperGs8601.stringResource(detailName) ?: return@forEachIndexed
                            val rawValue = answer[setStart + i].toInt() and 0xFF
                            val value = rawValue.toFloat() * eff.multiplier / eff.divider + eff.adder
                            errorDescription.add(
                                Pair(
                                    name,
                                    if (eff.unit.isEmpty())
                                        rawValue.toString()
                                    else
                                        String.format(Locale.getDefault(), "%.2f %s", value, eff.unit)
                                )
                            )
                        }
                        val odometer = ((answer[setStart + 3].toInt() and 0xFF) shl 8) +
                                (answer[setStart + 4].toInt() and 0xFF)
                        errorDescription.add(
                            Pair(
                                Res.string.dde_error_ff_odometer,
                                String.format(Locale.getDefault(), "%d km", odometer)
                            )
                        )
                    }
                    result.add(ModuleError(ModuleAddress.MODULE_GS8601, String.format("%02X", error), errorDescription))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getAllErrors], $ex, $message" }
        }
        return result
    }

    override fun getEepromSize(): Int  = 256

    override fun getEepromReadSize(): Int  = 0x10

    override fun getEepromWriteSize(): Int  = 0x10

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? =
        readMemory(MEMORY_EEPROM, address.toLong(), size)

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val dataToWrite =
            byteArrayOf(
                0x07,
                MEMORY_EEPROM.toByte(),
                ((address shr 16) and 0xFF).toByte(),
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte(),
                data.size.toByte()) + data
        val message = interfaceWrite(dataToWrite)
        if (!message.isValid)
            return false
        val answer = message.answer
        if (answer.size < 10) {
            Logger.e { "$tag [writeEeprom], short answer, $message" }
            return false
        }
        return (answer[7].toInt() and 0xFF) == data.size && answer[8].toInt() == 0x01
    }

    override val eepromWriteAvailable: Boolean = false

    override suspend fun control(controlElement: ControlElement): Boolean {
        try {
            when (controlElement.block) {
                0x00 -> {
                    val setValue = controlElement.controlSetValue ?: return false
                    val message = interfaceWrite(
                        byteArrayOf(0x0C, controlElement.byteInBlock.toByte(), setValue.value.coerceIn(0, 0xFF).toByte(), 0x00)
                    )
                    return message.isValid && message.answer.size < 6 || message.answer[4].toInt() == 0x00
                }

                0x01 -> return interfaceWrite(byteArrayOf(0x43, 0x00)).isValid
                else -> {}
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [control], $ex" }
        }
        return false
    }

    override suspend fun writeFlash(data: ByteArray) {
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_main_function_not_supported_yet))
    }

    override suspend fun readFlash() {
        val startAddress: Long = 0x000000
        val size: Long = 0x040000
        var result = ByteArray(0)
        val startTime = Calendar.getInstance().time
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_read_identification))

        val moduleInfo = getIdentification()
        val vin = getVIN()

        if (moduleInfo != null) {
            _programmingState.tryEmit(FlashProgrammingState.Message("ID : " + moduleInfo.id1))
        }
        _programmingState.tryEmit(FlashProgrammingState.Message("VIN : $vin"))

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
        val fastBaudRate = baudRates != null && baudRates.contains(FAST_BAUD_RATE) && setEcuBaudRate(FAST_BAUD_RATE)

        var i = startAddress
        while (i < endAddress) {
            _programmingState.tryEmit(FlashProgrammingState.Progress(((x++ * 100) / steps).toInt()))
            val currentBlockSize = minOf(blockSize.toLong(), endAddress - i).toInt()
            val data = readMemory(MEMORY_FLASH, i, currentBlockSize)
            if (data != null) {
                result += data
            } else {
                _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_read_error))
                if (fastBaudRate)
                    setEcuBaudRate(DEFAULT_BAUD_RATE)
                return
            }
            i += currentBlockSize.toLong()
        }
        if (fastBaudRate)
            setEcuBaudRate(DEFAULT_BAUD_RATE)

        val endTime = Calendar.getInstance().time
        val totalMillis = endTime.time - startTime.time
        val seconds = (totalMillis / 1000).toInt() % 60
        val minutes = ((totalMillis / 1000).toInt() / 60) % 60
        val hours = (totalMillis / 1000).toInt() / 3600

        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
            Res.string.activity_flash_programming_read_time,
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        ))

        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_end))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_close))
        if (result.size.toLong() == size) {
            try {
                _programmingState.tryEmit(FlashProgrammingState.FlashData(result.toList(), moduleInfo, vin))
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [readFlash]: $ex" }
            }
        }
    }

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

    private fun getPointer(pointers: OBDMessage, offset: Int): Long? {
        if (!pointers.isValid || pointers.answer.size < offset + 4)
            return null
        val answer = pointers.answer
        return ((answer[offset].toLong() and 0xFF) shl 16) or
                ((answer[offset + 1].toLong() and 0xFF) shl 8) or
                (answer[offset + 2].toLong() and 0xFF)
    }

    private suspend fun readMaxDataBlockLength(pointers: OBDMessage): Int {
        val pointer = getPointer(pointers, 0x42) ?: return FLASH_BLOCK_SIZE
        val mbl = readMemory(MEMORY_DEFAULT, pointer, 1)?.get(0)?.toInt()?.and(0xFF) ?: return FLASH_BLOCK_SIZE
        return if (mbl > 0x14) minOf(mbl - 4, FLASH_BLOCK_SIZE) else FLASH_BLOCK_SIZE
    }

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

    override val flashProgrammingAvailable: Boolean = false

    private companion object {
        const val MAX_ERRORS = 5
        const val ERROR_RECORD_SIZE = 0x13
        const val FREEZE_FRAME_SETS = 3
        const val MEMORY_DEFAULT = 0x00
        const val MEMORY_EEPROM = 0x03
        const val MEMORY_FLASH = 0x06
        const val FLASH_BLOCK_SIZE = 0x80
        const val MAX_BAUD_RATES = 8
        const val DEFAULT_BAUD_RATE = 9600L
        const val FAST_BAUD_RATE = 125000L
    }
}
