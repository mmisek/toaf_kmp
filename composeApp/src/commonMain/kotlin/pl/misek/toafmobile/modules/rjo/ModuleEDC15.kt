package pl.misek.toafmobile.modules.rjo

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.FlashProgrammingState
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleFlashProgramming
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolEDC15
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ModuleEDC15( protocol: ProtocolEDC15?) : IModuleFlashProgramming, IModule,
    IModuleControl {
        private var tag = "ModuleEDC15"
        private var kwp: ProtocolEDC15? = null
        private val moduleAddress = ModuleAddress.MODULE_EDC15.address

    init {
        if (protocol != null) kwp = protocol
    }
    private val _programmingState =  MutableSharedFlow<FlashProgrammingState>()
    override val programmingState = _programmingState.asSharedFlow()
    
    override fun getKoinName() = KoinNames.ModuleRJOEdc15

    override fun getModuleProtocol(): IProtocol {
        return kwp!!
    }

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol as ProtocolEDC15
    }

    private suspend fun moduleInit(): Boolean {
        return kwp!!.write(moduleAddress, 0x81.toByte(), byteArrayOf()).isValid
    }

    private suspend fun closeConnection(): Boolean {
        return kwp!!.write(moduleAddress, 0x82.toByte(), byteArrayOf()).isValid
    }

    override fun getModuleName(): String {
        return "EDC15"
    }

    override fun getModuleAddress(): ModuleAddress {
        return ModuleAddress.MODULE_EDC15
    }

    override fun getImageResource() =Res.drawable.module_engine

    override fun getModuleDescription() = Res.string.module_description_edc15

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        try {
            if (moduleInit()) {
                var message =
                    kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x80.toByte()), 0)
                if (message.isValid) {
                    info = ModuleInfo()
                    info.id1 = String(message.answer.copyOfRange(5,8)) +
                        String.format(
                            "%02d%02d%02d",
                            message.answer[8].toInt() and 0xFF,
                            message.answer[9].toInt() and 0xFF,
                            message.answer[10].toInt() and 0xFF)
                    info.ci = (message.answer[11].toInt() and 0xFF).toHexString()
                    info.di = (message.answer[12].toInt() and 0xFF).toHexString()
                    info.producer = ModulesFunction.getProducer(message.answer[13].toInt())
                }
                message =
                    kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x87.toByte()), 500)
                if (message.isValid && info != null) {
                    info.id2 = String(message.answer.copyOfRange(25, 28)) + String.format(
                        "%02X%02x%02X",
                        message.answer[28], message.answer[29], message.answer[30]
                    )
                }
            }
        } catch (ex: Exception) {
            Logger.e{"$tag : ${ex.toString()}"}
        }
        return info
    }

    private suspend fun getSerialNumber(): String {
        if (moduleInit()) {
            val message =
                kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x87.toByte()), 500)
            if (message.isValid) {
                return String.format(
                    "%02x%02X%02X%02X",
                    message.answer[5].toInt() and 0xFF,
                    message.answer[6].toInt() and 0xFF,
                    message.answer[7].toInt() and 0xFF,
                    message.answer[8].toInt() and 0xFF
                )
            }
        }
        return ""
    }

    override suspend fun getVIN(): String? {
        if (moduleInit()) {
            val message =
                kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x87.toByte()), 500)
            if (message.isValid) {
                return String(message.answer.copyOfRange(5, 16)) + String.format(
                    "%02X%02X%02X",
                    message.answer[16].toInt() and 0xFF,
                    message.answer[17].toInt() and 0xFF,
                    message.answer[18].toInt() and 0xFF
                )
            }
        }
        return null
    }

    override suspend fun getOdometer(): String? {
        return null
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        for (x in 0..4) {
            if (moduleInit() && startDiagnosticSession(0x83.toByte()) && securityAccess(0x05.toByte())) {
                val message = kwp!!.write(
                    moduleAddress,
                    0x18.toByte(),
                    byteArrayOf(0x00.toByte(), 0x00, 0x00),
                    500
                )
                if (message.isValid) {
                    val n = message.answer[4].toInt()
                    for (i in 0 until n) {
                        val errorCode =
                            ((message.answer[5 + 3 * i].toInt() and 0xFF) shl 8) + (message.answer[6 + 3 * i].toInt() and 0xFF)
                        val error = ModuleError(
                            ModuleAddress.MODULE_EDC15_ERROR,
                            String.format("%04X", errorCode),
                            ArrayList()
                        )
                        if (error.description != null) {
                            if (result == null) result = ArrayList()
                            result.add(error)
                        }
                    }
                    return result
                }
            }
        }
        return null
    }

    override suspend fun clearAllErrors(): Boolean {
        for (i in 0..4) {
            if (moduleInit() && startDiagnosticSession(0x83.toByte()) && securityAccess(0x05.toByte())) {
                val message = kwp!!.write(
                    moduleAddress,
                    0x14.toByte(),
                    byteArrayOf(0x00.toByte(), 0x00),
                    1500
                )
                if (message.isValid) return true
            }
        }
        return false
    }

    override suspend fun reset(): Boolean {
        return kwp!!.write(moduleAddress, 0x11.toByte(), byteArrayOf(0x01)).isValid
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        if (moduleInit() && startDiagnosticSession(0x83.toByte()) && securityAccess(0x05.toByte())) {
            val data = byteArrayOf(
                controlElement.block.toByte(),
                0x07,
                controlElement.byteInBlock.toByte(),
                controlElement.value.toByte()
            )
            val message = kwp!!.write(moduleAddress, 0x30.toByte(), data)
            return message.isValid
        }
        return false
    }

    private suspend fun securityAccess(mode: Byte): Boolean {
        val message = kwp!!.write(moduleAddress, 0x27.toByte(), byteArrayOf(mode), 3000)
        if (message.isValid) {
            val code =
                ((message.answer[5].toInt() and 0xFF) shl 8) + (message.answer[6].toInt() and 0xFF)
            val seed = securityAccessCalculator(mode.toInt(), code)
            val data = byteArrayOf(
                (mode + 1).toByte(),
                ((seed and 0xFF00) shr 8).toByte(),
                (seed and 0xFF).toByte()
            )
            return kwp!!.write(moduleAddress, 0x27.toByte(), data, 500).isValid
        }
        return false
    }

    private suspend fun securityAccessFlash(): Boolean {
        val message = kwp!!.write(moduleAddress, 0x27.toByte(), byteArrayOf(0x07), 3000)
        if (message.isValid) {
            val code =
                ((message.answer[5].toInt() and 0xFF) shl 8) + (message.answer[6].toInt() and 0xFF)
            val seed = securityAccessCalculator(0x08, code)
            val data = byteArrayOf(
                (8).toByte(),
                ((seed and 0xFF00) shr 8).toByte(),
                (seed and 0xFF).toByte()
            )
            return kwp!!.write(moduleAddress, 0x27.toByte(), data, 500).isValid
        }
        return false
    }

    private suspend fun startDiagnosticSession(mode: Byte): Boolean {
        return kwp!!.write(moduleAddress, 0x10.toByte(), byteArrayOf(mode)).isValid
    }

    private fun securityAccessCalculator(mode: Int, inKey: Int): Int {
        var key = inKey
        var andValue = 0x00
        var addValue = 0x00
        when (mode) {
            5 -> {
                andValue = 0x1E
                addValue = 0x02
            }

            7 -> {
                andValue = 0x0F
                addValue = 0x23
            }
        }

        val n = ((((key and 0xFF00) shr 8) xor 0x75) and andValue) + addValue
        for (i in 0 until n) {
            var x = 0
            if ((key and 0x0800) == 0x0800) x = x xor 1
            if ((key and 0x0400) == 0x0400) x = x xor 1
            if ((key and 0x0200) == 0x0200) x = x xor 1
            if ((key and 0x0008) == 0x0008) x = x xor 1
            if ((key and 0x0001) == 0x0001) x = x xor 1
            key = key shl 1
            key += x
            key = key and 0xFFFF
        }
        key = ((key and 0xFF) shl 8) + ((key and 0xFF00) shr 8)
        return key
    }

    private suspend fun readFlashData(address: Long): ByteArray {
        val size = 32.toByte()
        val data = byteArrayOf(
            ((address and 0xFF0000L) shr 16).toByte(),
            ((address and 0xFF00L) shr 8).toByte(),
            (address and 0xFFL).toByte(),
            size
        )
        val message = kwp!!.write(moduleAddress, 0x23.toByte(), data)
        if (message.isValid) {
            return message.answer.copyOfRange(4, message.length - 1)
        }
        return byteArrayOf()
    }

    override suspend fun readFlash()  {
        val startAddress: Long = 0x38000
        val size: Long = 0x8000
        var result = ByteArray(0)
        val blockSize = 32
        val endAddress = startAddress + size
        val steps = size.toInt() / blockSize
        val vin: String?
        val moduleInfo: ModuleInfo?
        val startTime = Calendar.getInstance().time
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        try {
            moduleInfo = getIdentification()
            vin = getVIN()
            if (moduleInfo != null) {
                _programmingState.tryEmit(FlashProgrammingState.Message("ID1 : " + moduleInfo.id1))
                _programmingState.tryEmit(FlashProgrammingState.Message("ID2 : " + moduleInfo.id2))
            }
            _programmingState.tryEmit(FlashProgrammingState.Message("VIN : $vin"))
            var continueProcess = true
            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_ecu_reset))
            reset()
            delay(10000)
            if (!moduleInit()) {
            _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                    Res.string.activity_flash_programming_ecu_init,
                    Res.string.activity_flash_programming_failed)
                )
            continueProcess = false
        }
            delay(200)
            if (continueProcess) if (!closeConnection()) continueProcess =
                false
            delay(200)
            if (continueProcess) if (!moduleInit()) {
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_ecu_init,
                        Res.string.activity_flash_programming_failed)
                    )
                continueProcess = false
            }
            delay(200)
            if (continueProcess) if (!startDiagnosticSession(0x85.toByte())) {
                continueProcess = false
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_diagnostic_session,
                        Res.string.activity_flash_programming_failed))
            } else _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                    Res.string.activity_flash_programming_diagnostic_session,
                    Res.string.activity_flash_programming_ok))
            delay(200)
            if (continueProcess) if (!closeConnection()) continueProcess = false
            delay(200)
            if (continueProcess) if (!moduleInit()) {
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_ecu_init,
                        Res.string.activity_flash_programming_failed))
                continueProcess = false
            }
            delay(200)
            if (continueProcess) if (!kwp!!.write(
                    moduleAddress,
                    0x10.toByte(),
                    byteArrayOf(0x85.toByte(), 0x00)
                ).isValid
            ) continueProcess = false
            delay(200)
            if (continueProcess) for (i in 0..99) if (!securityAccess(
                    0x07.toByte()
                )
            ) {
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_security_access,
                        Res.string.activity_flash_programming_failed))
                delay(5000)
            } else {
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_security_access,
                        Res.string.activity_flash_programming_ok))
                break
            }

            //if (!SecurityAccessFlash())
            /*
                if (!SecurityAccess((byte)0x07))
                {
                    _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_security_access, Res.string.activity_flash_programming_failed)));
                    continueProcess = false;
                }
                else
                    _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_security_access,Res.string.activity_flash_programming_ok)));
             */
            delay(1000)
            continueProcess = true
            var x = 0
            _programmingState.tryEmit(FlashProgrammingState.Progress(0))
            var i = startAddress
            while (i < endAddress) {
                _programmingState.tryEmit(FlashProgrammingState.Progress(x++ * 100 / steps))
                val data = readFlashData(i)
                if (data.size == blockSize) {
                    result += data
                } else {
                    _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_read_error))
                    continueProcess = false
                    break
                }
                i += blockSize.toLong()
            }
            val endTime = Calendar.getInstance().time
            val totalMillis = endTime.time - startTime.time
            val seconds = (totalMillis / 1000).toInt() % 60
            val minutes = ((totalMillis / 1000).toInt() / 60) % 60
            val hours = (totalMillis / 1000).toInt() / 3600

            _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                    Res.string.activity_flash_programming_read_time,
                    String.format("%02i:%02i:%02i", hours, minutes, seconds)))
            if (continueProcess) {
                try {
                    result = result.copyOfRange(0, (endAddress - startAddress).toInt())
                } catch (ex: Exception) {
                    Logger.e{"$tag : ${ex.toString()}"}
                }
                try {
                    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                    _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                            Res.string.activity_flash_programming_read_size,
                            result.size.toString()))
                    String.format("%s_%s.bin", vin, simpleDateFormat.format(endTime))
                        .let {
                            _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                    Res.string.activity_flash_programming_written_to,
                                    it))
                        }
                } catch (ex: Exception) {
                    _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_write_data_to_file_error))
                }
                try {
                    val jsonObject = buildJsonObject {
                        put("vin", vin)
                        put("module", getModuleName())
                        put("id1", moduleInfo?.id1 ?: "")
                        put("id2", moduleInfo?.id2 ?: "")
                    }
                    _programmingState.tryEmit(FlashProgrammingState.FlashData(result.toList(),null,null))
                    _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                            Res.string.activity_flash_programming_backup_flash,
                            Res.string.activity_flash_programming_ok))
                } catch (ex: Exception) {
                    Logger.e{"$tag : ${ex.toString()}"}
                    _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                            Res.string.activity_flash_programming_backup_flash,
                            Res.string.activity_flash_programming_failed))
                }
            }
        }catch (_: Exception) {
        }
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_end))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_close))
    }

    override suspend fun writeFlash(data: ByteArray) {
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_main_function_not_supported_yet))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_close))
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.RJO)
    }
}
