package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class ModuleDSC(protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_DSC, listOf(CarType.L322)),
    /*IModuleAdvanceControl,*/ IModuleCoding, IModuleLiveData {
    private val tag ="ModuleDSC"

    private companion object {
        const val ANSWER_BUSY: Byte = 0xA1.toByte()
        const val MAX_BUSY_RETRIES = 10
        const val BUSY_RETRY_DELAY_MS = 200L
        const val ERROR_RECORD_SIZE = 5
        const val CODING_FLAG = 0xA5
        const val CODING_BUSY_RETRIES = 10
        const val LWS_BUSY_RETRIES = 40
        const val LWS_SEQUENCE_NUMBER = 0x01
    }

    override fun getKoinName() = KoinNames.ModuleL322Dsc

    override fun getImageResource() = Res.drawable.module_abs

    override fun getModuleDescription() = Res.string.module_description_dsc

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null

        interfaceWrite(byteArrayOf(0x00))
        var message = interfaceWrite(byteArrayOf(0x04, 0x01))
        var busyRetries = 0
        while (message.answerCode == ANSWER_BUSY && busyRetries < MAX_BUSY_RETRIES) {
            delay(BUSY_RETRY_DELAY_MS.milliseconds)
            message = interfaceWrite(byteArrayOf(0x04, 0x01))
            busyRetries++
        }
        try {
            if (message.isValid && message.answer.size > 3) {
                result = ArrayList()
                val errorDetails =
                    arrayOf(
                        // [bit set, bit clear] per status bit 0..7
                        intArrayOf(0x08, 0x09), // bit0 ACB
                        intArrayOf(0x0A, 0x0B), // bit1 HBA
                        intArrayOf(0x0C, 0x0D), // bit2 ECD
                        intArrayOf(0x0E, 0x0F), // bit3 HDC
                        intArrayOf(0x00, 0x01), // bit4 ASC passive
                        intArrayOf(0x02, 0x03), // bit5 ABS control
                        intArrayOf(0x04, 0x05), // bit6 stop light switch
                        intArrayOf(0x06, 0x07)  // bit7 ASC control
                    )
                // Record layout: [code, status, count, speedHi, speedLo]
                for (i in 0 until (message.answer[3].toInt() and 0xFF)) {
                    val offset = 4 + ERROR_RECORD_SIZE * i
                    if (offset + ERROR_RECORD_SIZE > message.answer.size - 1) break
                    val errorCode = String.format("%02X", message.answer[offset])
                    val status = message.answer[offset + 1].toInt() and 0xFF
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount(message.answer[offset + 2].toInt() and 0xFF))
                    val rawSpeed = ((message.answer[offset + 3].toInt() and 0xFF) shl 8) +
                            (message.answer[offset + 4].toInt() and 0xFF)
                    errorDescription.add(ResourceHelper.getErrorDescriptionSpeed(rawSpeed * 0.0625))
                    for (j in errorDetails.indices) {
                        val bitSet = (status and (0x01 shl j)) != 0
                        val detail = if (bitSet) errorDetails[j][0] else errorDetails[j][1]
                        ResourceMapper
                            .stringResource(String.format("dsc_error_detail_%02X", detail))
                            ?.let { errorDescription.add(Pair(it, null)) }
                    }
                    result.add(ModuleError(ModuleAddress.MODULE_DSC, errorCode, errorDescription))
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

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (j in 0..3) {
            val message = interfaceWrite(byteArrayOf(0x0B, j.toByte()))
            try {
                if (message.isValid) {
                    result[j] = message.answer.copyOfRange(3, message.answer.size - 1)
                } else {
                    return null
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e{"$tag [readLiveData], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun readCoding(): List<ByteArray>? {
        interfaceWrite(byteArrayOf(0x00))
        val result: MutableList<ByteArray> = ArrayList()
        val message = interfaceWrite(byteArrayOf(0x08, 0x00, 0x06), 300)
        try {
            if (message.isValid)
                result.add(message.answer.copyOfRange(3, message.answer.size - 1))
            else
                return null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readCoding], $ex, $message"}
            return null
        }
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        interfaceWrite(byteArrayOf(0x00))
        if (dataToSave.isEmpty() || dataToSave[0].size < 4) {
            Logger.e { "$tag [writeCoding]: invalid coding data" }
            return false
        }
        if (interfaceWrite(byteArrayOf(0x90.toByte())).isValid) {
            val dataToSaveWithChecksum = calculateCodingChecksum(dataToSave)
            for (i in dataToSaveWithChecksum.indices) {
                if (!writeCodingBlock(i, dataToSaveWithChecksum[i])) {
                    Logger.e { "$tag [writeCoding]: write block $i failed" }
                    reset()
                    return false
                }
            }
            val checkData = readCoding()
            reset()
            val written = checkData?.firstOrNull()
            return written != null && written.size >= 4 &&
                    written.copyOfRange(0, 4).contentEquals(dataToSaveWithChecksum[0])
        } else return false
    }

    private suspend fun writeCodingBlock(address: Int, data: ByteArray): Boolean {
        var message = interfaceWrite(arrayOf(byteArrayOf(0x09, address.toByte()), data))
        var busyRetries = 0
        while (message.answerCode == ANSWER_BUSY && busyRetries < CODING_BUSY_RETRIES) {
            delay(BUSY_RETRY_DELAY_MS.milliseconds)
            message = interfaceWrite(arrayOf(byteArrayOf(0x09, address.toByte()), data))
            busyRetries++
        }
        return message.isValid
    }

    private fun calculateCodingChecksum(data: List<ByteArray>): List<ByteArray> {
        val result = data.map { it.copyOf() }.toMutableList()
        val block = result[0]
        block[0] = CODING_FLAG.toByte()
        block[1] = ((0xFF - (block[2].toInt() and 0xFF) - (block[3].toInt() and 0xFF)) and 0xFF).toByte()
        result[0] = block.copyOfRange(0, 4)
        return result
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean = interfaceWrite(byteArrayOf(0x00)).isValid

    suspend fun initLwsCalibration() : Boolean { 
        return interfaceWrite(initBlock).isValid
    }

    suspend fun writeLwsCalibration() : Boolean
    {
        val data = byteArrayOf(0x32, LWS_SEQUENCE_NUMBER.toByte(), 0x86.toByte(), 0xFF.toByte(), 0x88.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        var message = interfaceWrite(data)
        var busyRetries = 0
        while (message.answerCode == ANSWER_BUSY && busyRetries < LWS_BUSY_RETRIES) {
            delay(BUSY_RETRY_DELAY_MS.milliseconds)
            message = interfaceWrite(data)
            busyRetries++
        }
        return message.isValid
    }
    
    val initBlock = byteArrayOf(
        0x31.toByte(), 0x05.toByte(), 0x00.toByte(), 0x00.toByte(), 0xBB.toByte(), 0x07.toByte(), 0x07.toByte(), 0x89.toByte(), 0x07.toByte(), 0x00.toByte(), 0xB1.toByte(),
        0x02.toByte(), 0x24.toByte(), 0xA1.toByte(), 0xA4.toByte(), 0x20.toByte(), 0x2C.toByte(), 0xA1.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x2E.toByte(), 0xE8.toByte(), 0x2C.toByte(), 0x28.toByte(),
        0xE9.toByte(), 0x2C.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x2A.toByte(), 0xA2.toByte(), 0x79.toByte(), 0x22.toByte(), 0xE8.toByte(), 0x28.toByte(), 0x1E.toByte(), 0x65.toByte(),
        0x02.toByte(), 0x00.toByte(), 0x28.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x2A.toByte(), 0x88.toByte(), 0x00.toByte(), 0x1E.toByte(), 0xDF.toByte(), 0x46.toByte(), 0x51.toByte(), 0xFE.toByte(),
        0x1E.toByte(), 0x1D.toByte(), 0x78.toByte(), 0x23.toByte(), 0x1D.toByte(), 0xD7.toByte(), 0xE8.toByte(), 0x51.toByte(), 0xF8.toByte(), 0x1F.toByte(), 0x1C.toByte(), 0x08.toByte(), 0x02.toByte(),
        0x1C.toByte(), 0xA1.toByte(), 0xB4.toByte(), 0x20.toByte(), 0x2C.toByte(), 0xA1.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x2E.toByte(), 0xE8.toByte(), 0x2C.toByte(), 0x28.toByte(), 0xE9.toByte(),
        0x2C.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x2A.toByte(), 0x64.toByte(), 0x1C.toByte(), 0x28.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x2A.toByte(), 0xE8.toByte(), 0x28.toByte(),
        0x1C.toByte(), 0x38.toByte(), 0x1E.toByte(), 0x16.toByte(), 0xA1.toByte(), 0x01.toByte(), 0x01.toByte(), 0x20.toByte(), 0x71.toByte(), 0x07.toByte(), 0x1F.toByte(), 0x09.toByte(), 0x1F.toByte(),
        0x20.toByte(), 0x92.toByte(), 0x1C.toByte(), 0x20.toByte(), 0x3A.toByte(), 0x22.toByte(), 0x03.toByte(), 0x94.toByte(), 0x21.toByte(), 0x20.toByte(), 0xB0.toByte(), 0x20.toByte(), 0x22.toByte(),
        0xC6.toByte(), 0x1C.toByte(), 0x22.toByte(), 0xE0.toByte(), 0x24.toByte(), 0x94.toByte(), 0x07.toByte(), 0x40.toByte(), 0x9A.toByte(), 0x40.toByte(), 0x00.toByte(), 0xDF.toByte(), 0x19.toByte(),
        0xA2.toByte(), 0x79.toByte(), 0x60.toByte(), 0x88.toByte(), 0x00.toByte(), 0x60.toByte(), 0xD7.toByte(), 0x04.toByte(), 0x07.toByte(), 0x40.toByte(), 0x27.toByte(), 0x7E.toByte(), 0xE9.toByte(),
        0x00.toByte(), 0xAA.toByte(), 0x20.toByte(), 0xFF.toByte(), 0x62.toByte(), 0x68.toByte(), 0x62.toByte(), 0x60.toByte(), 0xD9.toByte(), 0x02.toByte(), 0x07.toByte(), 0x40.toByte(), 0xF0.toByte()
    )
}
