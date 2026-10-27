package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.containers.BCUKey
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.enums.ModuleFeature
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceCoding
import pl.misek.toafmobile.modules.interfaces.IModuleBcuKeys
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.bcu_alarm_mislock
import toafmobile.composeapp.generated.resources.bcu_alarm_trigger
import toafmobile.composeapp.generated.resources.module_bcu
import toafmobile.composeapp.generated.resources.module_description_bcu
import java.util.Arrays
import kotlin.experimental.inv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import pl.misek.toafmobile.modules.enums.FlashProgrammingState
import pl.misek.toafmobile.modules.interfaces.IModuleFlashProgramming
import toafmobile.composeapp.generated.resources.activity_flash_programming_close
import toafmobile.composeapp.generated.resources.activity_flash_programming_end
import toafmobile.composeapp.generated.resources.activity_flash_programming_failed
import toafmobile.composeapp.generated.resources.activity_flash_programming_flash_reading
import toafmobile.composeapp.generated.resources.activity_flash_programming_read_error
import toafmobile.composeapp.generated.resources.activity_flash_programming_read_time
import toafmobile.composeapp.generated.resources.activity_flash_programming_security_access
import toafmobile.composeapp.generated.resources.activity_flash_programming_start
import toafmobile.composeapp.generated.resources.activity_flash_read_identification
import java.util.Calendar
import java.util.Locale

private const val tag = "ModuleBCU"
// EEPROM BCU: fizycznie 0x0D80-0x0FFF (640 B). Adres logiczny 0 -> fizyczny EEPROM_BASE.
private const val EEPROM_BASE = 0x0D80
private const val EEPROM_SIZE = 0x0280
private const val EEPROM_READ_CHUNK = 0x20
class ModuleBCU(protocolDS2: ProtocolDS2?) : ModuleDS2(protocolDS2, ModuleAddress.MODULE_BCU, listOf(CarType.R40)),
    IModuleControl, IModuleCoding, IModuleAdvanceCoding, IModuleLiveData, IModuleVIN, IModuleBcuKeys, IModuleEepromProgramming,
        IModuleFlashProgramming
{
    private var _programmingState = MutableSharedFlow<FlashProgrammingState>(replay = 10, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val programmingState =_programmingState.asSharedFlow()

    override val flashProgrammingAvailable = false

    override fun getKoinName() = KoinNames.ModuleR40Bcu

    override fun getImageResource() = Res.drawable.module_bcu

    override fun getModuleDescription() = Res.string.module_description_bcu

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val message = interfaceWrite(byteArrayOf(0x0B, 0x00))
        if (message.isValid) {
            try {
                result[0x00] = message.answer.copyOfRange(3, message.answer.size)
                return result
            }
            catch (ex: CancellationException) {
                throw ex
            }
            catch (ex: Exception)
            {
                Logger.e {"$tag [readLiveData()] , $ex, $message"}
            }
        }
        return null
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        if (!securityAccess()) return false
        val data = byteArrayOf(0x0C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        data[controlElement.byteInBlock + 2] = controlElement.value.toByte()
        return interfaceWrite(data).isValid
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) = securityAccess()

    override suspend fun getIdentification(): ModuleInfo? {
        if (!securityAccess()) return null
        return ModulesFunction.getModuleInfo(interfaceWrite(byteArrayOf(0x00)))
    }

    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        if (!securityAccess()) return null
        for (i in 0..0x8) {
            val message = interfaceWrite(byteArrayOf(0x08, i.toByte()), 300)
            if (message.isValid)
                try {
                    result.add(message.answer.copyOfRange(3, message.answer.size - 1))
                }
                catch (ex: CancellationException) {
                    throw ex
                }
                catch (ex : Exception)
                {
                    Logger.e {"$tag [readCoding()], $ex, $message"}
                    return null
                }
            else
                return null
        }
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        if (dataToSave.size != 0x09) return false
        val dataToSaveWithCrc = calculateCodingChecksum(dataToSave)
        if (!securityAccess()) return false
        for (i in dataToSaveWithCrc.indices) {
            val data = byteArrayOf(0x09,i.toByte()) + dataToSaveWithCrc[i]
            val message = interfaceWrite(data, 150)
            if (!message.isValid) return false
        }
        reset()
        return true
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        if (!securityAccess()) return null

        for (j in 0..4) {
            val message = interfaceWrite(byteArrayOf(0x04, j.toByte()),200L)
            if (message.isValid) {
                try{
                if (result == null)
                    result = ArrayList()
                val n = if (j ==0x04) 5 else 8
                for (i in 0 until n) {
                    val code = message.answer[3 + 3 * i].toInt() and 0xFF
                    if (code != 0xFF) {
                        val errorCode = String.format("%02x",code)
                        val errorDescription: MutableList<Pair<StringResource,Any?>> = ArrayList()
                        errorDescription.add(ResourceHelper.getErrorDescriptionCount(message.answer[4 + 2 * i].toInt() and 0x1F))
                        errorDescription.add(ResourceHelper.getErrorDescriptionIsStatic((message.answer[4 + 2 * i].toInt() and 0x20) == 0x20))
                        result.add(
                            ModuleError(
                                ModuleAddress.MODULE_BCU,
                                errorCode,
                                errorDescription
                            )
                        )
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getAllErrors()] : $ex, $message"}
            }
            }
        }
        //alarm mislock errors
        if (!securityAccess()) return null

        var message = interfaceWrite(byteArrayOf(0x04, 0x05))
        if (message.isValid) {
            try{
            if (result == null) result = ArrayList()
            for (i in 0 until 6) {
                if ((message.answer[3 + 2 * i].toInt() and 0xFF) != 0xFF) {
                    val errorCode = String.format("%02X", message.answer[3 + 2 * i])
                    val errorDescription: MutableList<Pair<StringResource,Any?>> = ArrayList()
                    errorDescription.add(Pair(Res.string.bcu_alarm_mislock, null))
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[4 + 2 * i].toInt() and 0x1F)))
                    errorDescription.add(ResourceHelper.getErrorDescriptionIsStatic((message.answer[4 + 2 * i].toInt() and 0x20) == 0x20))
                    result.add(
                        ModuleError(
                            ModuleAddress.MODULE_BCU_ALARM,
                            errorCode,
                            errorDescription
                        )
                    )
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex :Exception)
            {
            Logger.e {"$tag [getAllErrors() -alarm misslock], $ex , $message"}
        }
        }
        if (!securityAccess()) return null
        message = interfaceWrite(byteArrayOf(0x04, 0x06))
        if (message.isValid) {
            try {
                if (result == null)
                    result = ArrayList()
                for (i in 0..6) {
                    if ((message.answer[3 + 2 * i].toInt() and 0xFF) != 0xFF) {
                        val errorCode = String.format("%02X", message.answer[3 + 2 * i])
                        val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                        errorDescription.add(Pair(Res.string.bcu_alarm_trigger, null))
                        errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[4 + 2 * i].toInt() and 0x1F)))
                        errorDescription.add(ResourceHelper.getErrorDescriptionIsStatic((message.answer[4 + 2 * i].toInt() and 0x20) == 0x20))
                        result.add(
                            ModuleError(
                                ModuleAddress.MODULE_BCU_ALARM,
                                errorCode,
                                errorDescription
                            )
                        )
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception)
            {
                Logger.e {"$tag [getAllErrors() - BCU alarm] $ex, $message"}
            }

        }
        return result
    }

    override suspend fun clearAllErrors(): Boolean {
        if (!securityAccess()) return false
        for (i in 0..3) {
            if (!interfaceWrite(byteArrayOf(0x05, i.toByte())).isValid)
                return false
        }
        return true
    }

    override suspend fun getVIN(): String? {
        if (!securityAccess()) return null
        val message = interfaceWrite(byteArrayOf(0x02), 500)
        if (message.isValid) {
            try {
                return String.format(
                    "%s%02X%02X%02X",
                    String(message.answer.copyOfRange(3, 14)),
                    message.answer[14], message.answer[15], message.answer[16]
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getVIN()], $ex, $message"}
            }
        }
        return null
    }

    override fun getVinLength(): Int = 17

    override suspend fun writeVIN(newVin: String): Boolean {
        if (newVin.length != 17) return false
        if (!securityAccess()) return false
        val data =
            newVin.substring(0, 11).toByteArray() +
            byteArrayOf(
                newVin.substring(11, 13).toInt(16).toByte(),
                newVin.substring(13, 15).toInt(16).toByte(),
                newVin.substring(15).toInt(16).toByte())
        return interfaceWrite(byteArrayOf(0x03) + data).isValid
    }

    private suspend fun securityAccess(): Boolean {
        val message = interfaceWrite(byteArrayOf(0x0E), 500)
        if (!message.isValid) return false
        try {
            val seed =
                ((message.answer[3].toInt() and 0xFF) shl 8) or (message.answer[4].toInt() and 0xFF)
            val key = calculateSeed(seed)
            val data =
                byteArrayOf(0x64, ((key and 0xFF00) shr 8).toByte(), (key and 0xFF).toByte())
            return interfaceWrite(data, 500).isValid
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [securityAccess()], $ex, $message"}
            return false
        }
    }

    override suspend fun removeKeyAt(position: Int): Boolean {
        val coding = readCoding()
        coding?.let {
            val data = coding[position]
            Arrays.fill(data, 0x00.toByte())
            it.toMutableList()[position] = data
            return writeCoding(coding)
        }
        return false
    }

    override suspend fun addKeyAt(position: Int, keyCode: String): Boolean {
        val coding = readCoding()?.toMutableList()
        if (coding != null) {
            val code = byteArrayOf(
                keyCode.substring(0, 2).toInt(16).toByte(),
                keyCode.substring(2, 4).toInt(16).toByte(),
                keyCode.substring(4).toInt(16).toByte(),
            )
            val codeInverted = byteArrayOf(
                code[0].inv(),
                code[1].inv(),
                code[2].inv())

            val data = byteArrayOf(
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                codeInverted[0],
                codeInverted[1],
                codeInverted[2],
                0x00,
                0x00,
                0x00,
                code[0],
                code[1],
                code[2],
                0x01
            )
            coding[position] = data
            return writeCoding(coding)
        }
        return false
    }

    override suspend fun readKeys(): List<BCUKey> {
        val result = ArrayList<BCUKey>()
        for (i in 0..4) {
            result.add(BCUKey(i, " "))
        }

        if (securityAccess()) {
            val coding = readCoding()
            if (coding != null) {
                for (i in 0..4) {
                    val keyCode = coding[i].copyOfRange( 11, 14)
                    if (!keyCode.contentEquals(byteArrayOf(0x00, 0x00, 0x00))) result[i] =
                        BCUKey(
                            i,
                            keyCode.toHexString()
                        )
                }
            }
        }
        return result
    }

    private fun calculateSeed(inKey: Int): Int {
        var key = inKey
        val n = ((key shr 8) and 0x07) + 1
        for (i in 0 until n) {
            var x = 0
            if ((key and 0x2000) == 0x2000) x = x xor 1
            if ((key and 0x0800) == 0x0800) x = x xor 1
            if ((key and 0x0010) == 0x0010) x = x xor 1
            if ((key and 0x0002) == 0x0002) x = x xor 1
            if ((key and 0x0001) == 0x0001) x = x xor 1
            key = ((key shl 1) and 0xFFFE) xor x
        }
        return key
    }

    override fun getNewFeatures() = super.getNewFeatures() + ModuleFeature.EepromProgramming + ModuleFeature.FlashProgramming

    override suspend fun beforeRead() { securityAccess() }

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? {
        val physical = EEPROM_BASE + address
        return readMemoryBlock(physical,size)
    }

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val physical = EEPROM_BASE + address
        for (i in data.indices) {
            val a = physical + i
            val message = interfaceWrite(byteArrayOf(0x07, ((a shr 8) and 0xFF).toByte(), (a and 0xFF).toByte(), data[i]))
            if (!message.isValid) {
                Logger.e { "$tag [writeEeprom], addr ${a.toString(16)}, $message" }
                return false
            }
        }
        return true
    }
    override val eepromWriteAvailable: Boolean = false

    override fun getEepromSize(): Int = EEPROM_SIZE
    override fun getEepromReadSize(): Int = EEPROM_READ_CHUNK
    override fun getEepromWriteSize(): Int = 1

    override suspend fun readFlash() {
        val startAddress = 0x8000;
        val flashSize = 0x7FFF
        val blockSize = 0x20
        var result = ByteArray(0)
        val startTime = Calendar.getInstance().time
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_security_access))
        if (!securityAccess()) {
            _programmingState.tryEmit((FlashProgrammingState.Message(Res.string.activity_flash_programming_failed)))
            return
        }
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
            _programmingState.tryEmit(FlashProgrammingState.Progress(((x++ * 100)/steps).toInt()))
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

    private suspend fun readMemoryBlock(address: Int, size : Int) : ByteArray?
    {
        val message = interfaceWrite(
            byteArrayOf(0x06, size.toByte(), ((address shr 8) and 0xFF).toByte(), (address and 0xFF).toByte()),
            (350 + size * 3).toLong())
        try {
            if (!message.isValid) return null
            val data = message.answer.copyOfRange(3, message.answer.size - 1)
            if (data.size != size) {
                Logger.e { "$tag [readMemoryBlock], size ${data.size} != $size, $message" }
                return null
            }
            return data
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readMemoryBlock], $ex, $message" }
            return null
        }
    }
    private fun calculateCodingChecksum(data: List<ByteArray>): List<ByteArray> {
        var cs = 0x00
        for (i in data.indices) for (j in data[i].indices) {
            cs = cs xor data[i][j].toInt()
            if (((i < 5) && (j in 8..10))) cs = cs xor data[i][j].toInt()
        }
        cs = cs xor data[data.size - 1][data[data.size - 1].size - 3].toInt()
        cs = cs xor data[data.size - 1][data[data.size - 1].size - 1].toInt()
        data[data.size - 1][data[data.size - 1].size - 1] = cs.toByte()
        return data
    }
}
