package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.activities.composeModels.activities.AdvanceControlServotronic
import pl.misek.toafmobile.modules.containers.ServotronicMap
import pl.misek.toafmobile.modules.containers.ServotronicPoint
import pl.misek.toafmobile.modules.interfaces.AdvanceControlElement
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceControl
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleServotronic
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import pl.misek.toafmobile.modules.enums.FlashProgrammingState
import pl.misek.toafmobile.modules.interfaces.IModuleFlashProgramming
import java.util.Calendar
import java.util.Locale
import kotlin.collections.plus

private const val tag = "ModuleBCU - L322"
class ModuleBCU(protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_BCU_L322, listOf(CarType.L322)),
    IModuleCoding, IModuleEepromProgramming, IModuleAdvanceControl, IModuleServotronic,
    IModuleFlashProgramming /*, IModuleLiveData*/ {
    override fun getKoinName() = KoinNames.ModuleL322Bcu

    override fun getImageResource()  = Res.drawable.module_bcu

    override fun getModuleDescription() = Res.string.module_description_bcu

    override fun getAdvanceControlElements() = listOf(
        AdvanceControlElement(Res.string.advance_control_servotronic_title, isNew = true) { userInfo, onShowResult ->
            AdvanceControlServotronic(userInfo = userInfo, onShowResult = onShowResult)
        }
    )

    private var _programmingState = MutableSharedFlow<FlashProgrammingState>(replay = 10, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val programmingState =_programmingState.asSharedFlow()
    override val flashProgrammingAvailable = false

    private val speedMap05Tune = listOf(0, 2, 10, 20, 30, 40, 50, 60, 70, 80, 90, 110, 130, 150, 160, 250)
    private val currentMap05Tune = listOf(243, 191, 172, 163, 154, 146, 139, 131, 122, 116, 110, 95, 76, 56, 45, 29)

    override val defaultMaps = listOf(
        createServotronicMap(
            codingIndex = "04",
            name = "default",
            condition = "L30",
            speeds = speedMap05Tune,
            currents = currentMap05Tune
        ),
        createServotronicMap(
            codingIndex = "10",
            name = "05_tune",
            condition = "L30+!(MY06,MY07,MY08,MY09)",
            speeds = speedMap05Tune,
            currents = currentMap05Tune
        ),
        createServotronicMap(
            codingIndex = "10",
            name = "06_tune",
            condition = "L30+(MY06,MY07,MY08,MY09)",
            speeds = listOf(0, 2, 10, 20, 30, 40, 50, 60, 70, 80, 90, 110, 130, 150, 200, 210),
            currents = listOf(243, 198, 177, 157, 141, 128, 117, 109, 101, 92, 85, 70, 56, 41, 0, 0)
        )
    )

    private fun createServotronicMap(codingIndex: String, name: String, condition: String, speeds: List<Int>, currents: List<Int>) =
        ServotronicMap(
            codingIndex = codingIndex,
            name = name,
            condition = condition,
            points = speeds.zip(currents) { speed, current -> ServotronicPoint(speed = speed, rawDec = current) }
        )

    override suspend fun readCodingIndex(): String? = getIdentification()?.ci?.takeIf { it.isNotEmpty() }

    private data class ServotronicLayout(val speedBlock: Int, val currentBlock: Int, val offset: Int, val size: Int)

    private val servotronicLayouts = mapOf(
        "04" to ServotronicLayout(speedBlock = 1, currentBlock = 2, offset = 0, size = 16),
        "10" to ServotronicLayout(speedBlock = 1, currentBlock = 2, offset = 0, size = 16)
    )

    private suspend fun readServotronicLayout(): ServotronicLayout? {
        val codingIndex = readCodingIndex()
        val layout = servotronicLayouts[codingIndex?.uppercase()]
        if (layout == null)
            Logger.w { "$tag: servotronic map not supported for CI=$codingIndex" }
        return layout
    }

    private fun ServotronicLayout.isBlockValid(block: ByteArray?) = block != null && block.size > offset + size

    override suspend fun readServotronicPoints(): List<ServotronicPoint>? {
        val layout = readServotronicLayout() ?: return null
        val coding = readCoding() ?: return null
        val speedBlock = coding.getOrNull(layout.speedBlock)
        val currentBlock = coding.getOrNull(layout.currentBlock)
        if (!layout.isBlockValid(speedBlock) || !layout.isBlockValid(currentBlock)) {
            Logger.e { "$tag [readServotronicPoints]: invalid coding blocks" }
            return null
        }
        return (0 until layout.size).map { i ->
            ServotronicPoint(
                speed = speedBlock!![layout.offset + i].toInt() and 0xFF,
                rawDec = currentBlock!![layout.offset + i].toInt() and 0xFF
            )
        }
    }

    override suspend fun writeServotronicPoints(points: List<ServotronicPoint>): Boolean {
        val layout = readServotronicLayout() ?: return false
        if (points.size != layout.size || points.any { it.speed !in 0..0xFF || it.rawDec !in 0..0xFF }) {
            Logger.e { "$tag [writeServotronicPoints]: invalid points $points" }
            return false
        }
        val coding = readCoding()?.map { it.copyOf() } ?: return false
        val speedBlock = coding.getOrNull(layout.speedBlock)
        val currentBlock = coding.getOrNull(layout.currentBlock)
        if (!layout.isBlockValid(speedBlock) || !layout.isBlockValid(currentBlock)) {
            Logger.e { "$tag [writeServotronicPoints]: invalid coding blocks" }
            return false
        }
        coding.forEachIndexed { i, block ->
            if (!isCodingChecksumValid(block))
                Logger.w { "$tag [writeServotronicPoints]: block $i checksum invalid - will be corrected" }
        }
        points.forEachIndexed { i, point ->
            speedBlock!![layout.offset + i] = point.speed.toByte()
            currentBlock!![layout.offset + i] = point.rawDec.toByte()
        }
        return writeCoding(coding)
    }

    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        for (i in 0..0x2) {
            val message = interfaceWrite(byteArrayOf(0x08, i.toByte()), 300)
            try {
                if (!message.isValid) {
                    Logger.e { "$tag [readCoding] : block $i read failed, $message" }
                    return null
                }
                val block = message.answer.copyOfRange(3, message.answer.size - 1)
                if (block.size < 2) {
                    Logger.e { "$tag [readCoding] : block $i too short, $message" }
                    return null
                }
                result.add(block)
            }
            catch (ex: CancellationException) {
                throw ex
            }
            catch (ex : Exception)
            {
                Logger.e {"$tag [readCoding] : $ex, $message"}
                return null
            }
        }
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        val dataToSaveWithCRC = calculateCodingChecksum(dataToSave)
        run {
            for (i in dataToSaveWithCRC.indices) {
                val message = interfaceWrite(
                    arrayOf(
                        byteArrayOf(
                            0x09,
                            i.toByte()
                        ), dataToSaveWithCRC[i]
                    ), 500)
                if (!message.isValid) return false
            }
            reset()
        }
        return true
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        var message = interfaceWrite(byteArrayOf(0x04, 0x00), 500)
        try {
            if (message.isValid && (message.answer[3].toInt() and 0xFF) == 0) {
                return ArrayList()
            } else if (message.isValid && (message.answer[3].toInt() and 0xFF) > 0) {
                for (j in 1..2) {
                    message = interfaceWrite(byteArrayOf(0x04, j.toByte()), 500)
                    if (message.isValid && message.answer.size > 4) {
                        for (i in 0 until (message.answer.size - 4) / 2) {
                            if ((message.answer[3 + 2 * i].toInt() and 0xFF) != 0xFF) {
                                val errorCode = String.format("%02X", message.answer[3 + 2 * i])
                                val errorDescription: MutableList<Pair<StringResource, Any?>> =
                                    ArrayList()
                                errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[4 + 2 * i].toInt() and 0x1F)))
                                errorDescription.add(ResourceHelper.getErrorDescriptionIsStatic((message.answer[4 + 2 * i].toInt() and 0x20) == 0x20))
                                if (result == null) result = ArrayList()
                                result.add(
                                    ModuleError(
                                        ModuleAddress.MODULE_BCU_L322,
                                        errorCode,
                                        errorDescription
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex :Exception)
        {
            Logger.e {"$tag [getAllErrors], $ex, $message"}
        }
        return result
    }

    private fun isCodingChecksumValid(block: ByteArray): Boolean {
        if (block.size < 2) return false
        var cs = 0x00
        for (i in 0 until block.size - 1) cs = cs xor (block[i].toInt() and 0xFF)
        return cs == (block[block.size - 1].toInt() and 0xFF)
    }

    private fun calculateCodingChecksum(data: List<ByteArray>): List<ByteArray> {
        for (x in data.indices) {
            var cs = 0x00
            for (i in 0 until data[x].size - 1) cs = cs xor data[x][i].toInt()
            data[x][data[x].size - 1] = cs.toByte()
        }

        return data
    }

    private suspend fun readMemoryBlock(address: Int, size: Int) : ByteArray?
    {
        val message = interfaceWrite(
            byteArrayOf(
                0x06,
                0x03,
                0x00,
                0x00,
                0x00,
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte(),
                size.toByte()
            )
        )
        try {
            return if (message.isValid) message.answer.copyOfRange(0x03, 3 + size) else null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readEeprom]: $ex, $message"}
            return null
        }
    }

    override suspend fun readEeprom(address: Int, size: Int) = readMemoryBlock(0x0D00 + address, size)

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val adr  = address + 0x0D00
        val dataToWrite =
            byteArrayOf(
                0x07,
                0x03,
                0x00,
                0x00,
                0x00,
                ((adr shr 8) and 0xFF).toByte(),
                (adr and 0xFF).toByte(),
                (data.size).toByte())  + data

        return interfaceWrite(dataToWrite).isValid
    }

    override fun getEepromSize(): Int = 0x300

    override fun getEepromReadSize(): Int = 0x10

    override fun getEepromWriteSize(): Int  = 0x10 / 2

    override suspend fun readFlash() {
        val startAddress = 0x8000;
        val flashSize = 0x7FFF
        val blockSize = 0x20
        var result = ByteArray(0)
        val startTime = Calendar.getInstance().time
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_read_identification))
        val ident = getIdentification()
        if (ident == null){
            _programmingState.tryEmit((FlashProgrammingState.Message(Res.string.activity_flash_programming_failed)))
            return
        }
        _programmingState.tryEmit(FlashProgrammingState.Message("ID : ${ident.id1}"))
        val vin = getVIN()
        if (!vin.isNullOrEmpty()) _programmingState.tryEmit(FlashProgrammingState.Message("VIN: $vin"))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_flash_reading))
        var x = 0;
        val endAddress = startAddress + flashSize
        val steps = (flashSize + blockSize -1)/blockSize
        var  i = startAddress
        while (i < endAddress)
        {
            _programmingState.tryEmit(FlashProgrammingState.Progress(((x++ * 100)/steps)))
            val data = readMemoryBlock(i,blockSize)
            if (data == null || data.size != blockSize)
            {
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
            _programmingState.tryEmit(
                FlashProgrammingState.FlashData(
                    result.toList(),
                    ident,
                    vin
                )
            )
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readFlash]: $ex" }
        }
    }

    override suspend fun writeFlash(data: ByteArray) {}
}
