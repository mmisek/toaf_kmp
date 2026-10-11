package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.FlashProgrammingState
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleFlashProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.activity_flash_programming_close
import toafmobile.composeapp.generated.resources.activity_flash_programming_diagnostic_session
import toafmobile.composeapp.generated.resources.activity_flash_programming_ecu_reset
import toafmobile.composeapp.generated.resources.activity_flash_programming_end
import toafmobile.composeapp.generated.resources.activity_flash_programming_failed
import toafmobile.composeapp.generated.resources.activity_flash_programming_file_size
import toafmobile.composeapp.generated.resources.activity_flash_programming_flash_erasing
import toafmobile.composeapp.generated.resources.activity_flash_programming_flash_reading
import toafmobile.composeapp.generated.resources.activity_flash_programming_flash_writing_end
import toafmobile.composeapp.generated.resources.activity_flash_programming_ok
import toafmobile.composeapp.generated.resources.activity_flash_programming_read_error
import toafmobile.composeapp.generated.resources.activity_flash_programming_read_time
import toafmobile.composeapp.generated.resources.activity_flash_programming_security_access
import toafmobile.composeapp.generated.resources.activity_flash_programming_start
import toafmobile.composeapp.generated.resources.activity_flash_programming_write_error
import toafmobile.composeapp.generated.resources.activity_flash_programming_write_time
import toafmobile.composeapp.generated.resources.activity_flash_programming_wrong_file_size
import toafmobile.composeapp.generated.resources.activity_flash_read_identification
import toafmobile.composeapp.generated.resources.module_description_jatco
import toafmobile.composeapp.generated.resources.module_gearbox
import java.util.Calendar
import java.util.Locale
import kotlin.collections.plus
import kotlin.time.Duration.Companion.milliseconds

private const val tag = "ModuleJATCO"
class ModuleJATCO(private var kwp: ProtocolKWP?) : IModule, IModuleControl, IModuleLiveData, IModuleVIN, IModuleFlashProgramming {
    private val moduleAddress = ModuleAddress.MODULE_JATCO.address
    private val liveDataBlocksToRead: MutableList<Byte> = ArrayList()
    private val _programmingState =  MutableSharedFlow<FlashProgrammingState>(replay = 10, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val programmingState = _programmingState.asSharedFlow()

    override fun getKoinName() = KoinNames.ModuleR40Jatco

    override fun getModuleProtocol(): IProtocol {
        return kwp!!
    }

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol as ProtocolKWP
    }

    override fun getModuleName(): String {
        return ModuleAddress.MODULE_JATCO.showName
    }

    override fun getModuleAddress(): ModuleAddress {
        return ModuleAddress.MODULE_JATCO
    }

    override fun getImageResource() = Res.drawable.module_gearbox
    
    override fun getModuleDescription()  = Res.string.module_description_jatco

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        var message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x80.toByte()))
        try {
            if (message.isValid) {
                info = ModuleInfo()
                val answer = message.answer
                info.id1 = String(answer.copyOfRange(6, 9)) + answer.copyOfRange(9, 12)
                    .joinToString("") { "%02X".format(it) }
                info.hw = (answer[13].toInt() and 0xFF).toByte().toHexString()
                info.ci = (answer[14].toInt() and 0xFF).toString()
                info.di = (answer[15].toInt() and 0xFF).toByte().toString()
                info.bi = (answer[16].toInt() and 0xFF).toByte().toHexString()
                info.sw = (answer[22].toInt() and 0xFF).toByte().toHexString()
                info.producer = ModulesFunction.getProducer(answer[21].toInt())
                info.date = String.format(
                    "%02X/%02X/%02X%02X",
                    answer[20].toInt() and 0xFF,
                    answer[19].toInt() and 0xFF,
                    answer[17].toInt() and 0xFF,
                    answer[18].toInt() and 0xFF
                )
            }
            message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x87.toByte()))
            if (message.isValid && info != null) {
                info.id2 = String(message.answer.copyOfRange(26, 29)) +
                        message.answer.copyOfRange(29, 32).joinToString("") { "%02X".format((it)) }
            }
            if (info != null) {
                info.serial = getSerialNumber()
            }
            info?.let {
                var message = kwp!!.write(moduleAddress, 0x1A, byteArrayOf(0x92.toByte()))
                if (message.isValid)
                    it.additionalInfo.add(
                        Pair(
                            Res.string.module_description_jatco,
                            String(message.answer.copyOfRange(6, message.answer.size - 1))
                        )
                    )
                message = kwp!!.write(moduleAddress, 0x1A, byteArrayOf(0x94.toByte()))
                if (message.isValid)
                    it.additionalInfo.add(
                        Pair(
                            Res.string.module_description_jatco,
                            String(message.answer.copyOfRange(6, message.answer.size - 1))
                        )
                    )
                message = kwp!!.write(moduleAddress, 0x1A, byteArrayOf(0x89.toByte()))
                if (message.isValid)
                    it.additionalInfo.add(
                        Pair(
                            Res.string.module_description_jatco,
                            String(message.answer.copyOfRange(6, 9)) + message.answer.copyOfRange(
                                9,
                                12
                            ).joinToString("") { x -> "%02X".format(x) })
                    )
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getIdentification], $ex, $message"}
        }
        return info
    }

    private suspend fun getSerialNumber(): String {
        val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x94.toByte()))
        try {
            if (message.isValid) {
                return String.format(
                    "%02X%02X",
                    message.answer[4].toInt() and 0xFF,
                    message.answer[5].toInt() and 0xFF
                )
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getSErialNumber], $ex, $message"}
        }
        return ""
    }

    override suspend fun getVIN(): String? {
        val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x87.toByte()))
        try {
            if (message.isValid) return String(
                message.answer.copyOfRange(
                    6,
                    17
                )
            ) + message.answer.copyOfRange(17, 20).toHexString()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getVIN], $ex,$message"}
        }

        return null
    }

    override suspend fun getOdometer(): String? {
        return null
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        if (securityAccess(0x81,0x03)) {
            val message = kwp!!.write(moduleAddress, 0x18.toByte(), byteArrayOf(0x00, 0x00, 0x00), 500)
            try {
                if (message.isValid) {
                    result = mutableListOf()
                    for (i in 0 until (message.answer[5].toInt() and 0xFF)) {
                        val errorCode =
                            ((message.answer[6 + 3 * i].toInt() and 0xFF) shl 8) + (message.answer[7 + 3 * i].toInt() and 0xFF)
                        val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                        for (j in 0..7) {
                            if ((message.answer[8 + 3 * i].toInt() and (0x01 shl j)) == (0x01 shl j)) {
                                ResourceMapper.stringResource(
                                    String.format(
                                        "jatco_error_description_%02X",
                                        0x01 shl j
                                    )
                                )
                                    ?.let { errorDescription.add(Pair(it, null)) }
                            }
                        }
                        if ((message.answer[8 + 3 * i].toInt() and 0xFE) == 0xFE) {
                            ResourceMapper.stringResource("jatco_error_description_FE")
                                ?.let { errorDescription.add(Pair(it, null)) }
                        }
                        result.add(
                            ModuleError(
                                ModuleAddress.MODULE_JATCO,
                                String.format("%04X", errorCode),
                                errorDescription
                            )
                        )
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getAllErros], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun clearAllErrors(): Boolean {
        if (securityAccess(0x81,0x03)) return kwp!!.write(
            moduleAddress,
            0x14.toByte(),
            byteArrayOf(0x00, 0x00)
        ).isValid
        return false
    }

    override suspend fun reset(): Boolean {
        return kwp!!.write(moduleAddress, 0x11.toByte(), byteArrayOf(0x01)).isValid
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (b in liveDataBlocksToRead) {
            val message = kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(b), 100)
            try {
                if (message.isValid)
                    result[b.toInt()] = message.answer.copyOfRange(6, message.length - 1)
                else return null
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [readLiveData], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        if (securityAccess(0x81,0x03)) {
            val message = kwp!!.write(
                moduleAddress,
                0x3B.toByte(),
                byteArrayOf(
                    0x0C.toByte(),
                    ((controlElement.value and 0xFF00) shr 8).toByte(),
                    (controlElement.value and 0xFF).toByte()
                )
            )
            return message.isValid
        }
        return false
    }

    private suspend fun testerPresent(): Boolean {
        return kwp!!.write(moduleAddress, 0x3E.toByte(), byteArrayOf()).isValid
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        liveDataBlocksToRead.clear()
        for ((_, _, _, block) in liveDataElements) liveDataBlocksToRead.add(block.toByte())
        return securityAccess(0x81,0x03)
    }

    private suspend fun securityAccess(diagnosticSession : Int, level : Int, useTesterPresent : Boolean = true): Boolean {
        if (useTesterPresent)
            if (!testerPresent())
                return false
        var message = kwp!!.write(moduleAddress, 0x10.toByte(), byteArrayOf(diagnosticSession.toByte()))
        if (message.isValid) {
            message = kwp!!.write(moduleAddress, 0x27.toByte(), byteArrayOf(level.toByte()), 1000)
            try{
                if (message.isValid) {
                    val code =
                        ((message.answer[6].toInt() and 0xFF) shl 8) + (message.answer[7].toInt() and 0xFF)
                    if (code == 0x0000) return true
                    else {
                        val seed = if (level == 0x07) securityAccessCalculatorFlash(level,code) else securityAccessCalculator(level, code)
                        val data = byteArrayOf(
                            (level + 1).toByte(),
                            ((seed and 0xFF00) shr 8).toByte(), (seed and 0xFF).toByte()
                        )
                        return kwp!!.write(moduleAddress, 0x27.toByte(), data, 1000).isValid
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [securityAccess], $ex, $message"}
            }
        }
        return false
    }

    private fun securityAccessCalculator(level : Int, inKey: Int) :Int {
        var key = inKey
        val hiByte = (inKey shr 8) and 0xFF
        var n = when (level) {
            0x01 -> 2
            0x03 -> ((hiByte xor 0xFE) and 0x1D) + 2
            0x05 -> ((hiByte xor 0xFE) and 0x1D) + 3
            else -> 2
        }
        while (n > 0)
        {
            val b10 = (key shr 10) and 0x01
            val b5 = (key shr 5) and 0x01
            val b3 = (key shr 3) and 0x01
            val b1 = (key shr 1) and 0x01
            val b0 = key and 0x01
            val b = b10 xor b5 xor b3 xor b1 xor b0
            key = ((key shl 1) and 0xFFFF) or b
            n--
        }
        key = ((key shl 8) and 0xFF00) or ((key shr 8) and 0x00FF)
        return key
    }

    private fun securityAccessCalculatorFlash(level: Int,seed :Int): Int {
        val maskedSeed = seed and 0xFFFF
        val seedHi = (maskedSeed shr 8) and 0xFF

        val rounds = when (level) {
            0x01 -> 2
            0x03 -> ((seedHi xor 0x7E) and 0x1D) + 2
            0x05 -> ((seedHi xor 0x7E) and 0x1D) + 3
            0x07 -> 3
            else -> throw IllegalArgumentException("unsupported level")
        }

        var value = maskedSeed
        for (i in 0 until rounds) {
            val feedback = ((value shr 0) and 1) xor
                    ((value shr 10) and 1) xor
                    ((value shr 5) and 1) xor
                    ((value shr 3) and 1) xor
                    ((value shr 1) and 1)
            value = (((value shl 1) and 0xFFFE) or feedback) and 0xFFFF
        }

        return (((value shl 8) and 0xFF00) or (value shr 8)) and 0xFFFF
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.R40)
    }

    override fun getVinLength(): Int = 17

    override suspend fun writeVIN(newVin: String): Boolean {
        try {
            val message = kwp!!.write(moduleAddress,0x1A,byteArrayOf(0x87.toByte()))
            if (message.isValid) {
                if (securityAccess(0x83, 0x05)) {
                    val answer = message.answer.copyOfRange(6,message.answer.size - 1)
                    val vinArray = newVin.toByteArray().copyOfRange(0, newVin.length -6)
                    val vinNumber =newVin.subSequence(newVin.length-6, newVin.length).toString()
                    val vinArrayEndHex = vinNumber.toBcdByteArray()
                    vinArray.copyInto(answer,0,0,vinArray.size)
                    vinArrayEndHex.copyInto(answer,newVin.length - 6,0,vinArrayEndHex.size)
                    val result =  kwp!!.write(
                        moduleAddress,
                        0x3B.toByte(),
                        byteArrayOf(0x87.toByte()) + answer,
                        500
                    )
                    return result.isValid
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e{"$tag [writeVin]: ${ex.toString()}"}
        }
        return false
    }

    override suspend fun writeFlash(data: ByteArray)  {
        //var inData = data
        val blockSize = 0x60
        val steps = data.size / blockSize
        val startTime = Calendar.getInstance().time

        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
            Res.string.activity_flash_programming_file_size,
            data.size.toString()))
        if (data.size != 0xC000) {
            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_wrong_file_size))
        } else {
            //inData = data.copyOfRange(0, (data.size / blockSize) * blockSize)
            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_read_identification))
            val moduleInfo = getIdentification()
            val vin = getVIN()
            _programmingState.tryEmit(FlashProgrammingState.Message("ID1 : " + moduleInfo?.id1))
            _programmingState.tryEmit(FlashProgrammingState.Message("ID2 : " + moduleInfo?.id2))
            _programmingState.tryEmit(FlashProgrammingState.Message("VIN : $vin"))
            //endDiagnosticSession()
            try {
                delay(1000.milliseconds)
            } catch (ex: CancellationException) {
                throw ex
            } catch (_ : Exception) {
            }
            if (securityAccess(0x83,0x05,false)) {
                testerPresent()
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                    Res.string.activity_flash_programming_security_access,
                    Res.string.activity_flash_programming_ok))
                var requestErase = true
                var requestNumber = 0
                while(requestErase && requestNumber < 30)
                {
                    val eraseFlash =
                        kwp!!.write(moduleAddress, 0x31.toByte(), byteArrayOf(0x07), 10000)
                    requestErase =  eraseFlash.answer[eraseFlash.answer.size - 3] == 0x71.toByte() &&eraseFlash.answer[eraseFlash.answer.size - 2] == 0x02.toByte()
                    requestNumber++
                    delay(1000.milliseconds)
                }
                if (!requestErase)
                {
                    //B8 19 F7 09 34 03 00 00 00 00 00 C0 00 A8
                    if (kwp!!.write(
                            moduleAddress,
                            0x34.toByte(),
                            byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x00,0x00,0xC0.toByte(),0x00)
                        ).isValid
                    ) {
                        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                            Res.string.activity_flash_programming_flash_erasing,Res.string.activity_flash_programming_ok)
                        )

                        var progress = 0

                        _programmingState.tryEmit(FlashProgrammingState.Progress(progress))
                        try {
                            var x = 0
                            while (x < data.size) {
                                val dataToWrite = data.copyOfRange(x, if (((x + blockSize) < data.size)) x + blockSize else data.size)
                                if (dataToWrite.size == blockSize) {
                                    if (writeFlashData(dataToWrite)) {
                                        _programmingState.tryEmit(FlashProgrammingState.Progress(progress++ * 100 / steps))
                                    } else {
                                        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_write_error))
                                        break
                                    }
                                }
                                x += blockSize
                            }
                            kwp!!.write(moduleAddress, 0x37.toByte(), byteArrayOf(0x01), 2000)
                            _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                Res.string.activity_flash_programming_flash_writing_end,
                                Res.string.activity_flash_programming_ok))
                            val endTime = Calendar.getInstance().time
                            val totalMillis = endTime.time - startTime.time
                            val seconds = (totalMillis / 1000).toInt() % 60
                            val minutes = ((totalMillis / 1000).toInt() / 60) % 60
                            val hours = (totalMillis / 1000).toInt() / 3600
                            _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                Res.string.activity_flash_programming_write_time,
                                String.format(Locale.getDefault(),"%02d:%02d:%02d", hours, minutes, seconds)))
                            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_ecu_reset))
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Logger.e{"$tag [WriteFlash]: ${ex.toString()}"}
                        }
                    } else {
                        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                            Res.string.activity_flash_programming_flash_erasing,
                            Res.string.activity_flash_programming_failed)
                        )
                    }
                } else _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                    Res.string.activity_flash_programming_diagnostic_session,
                    Res.string.activity_flash_programming_failed))
            } else _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                Res.string.activity_flash_programming_security_access,
                Res.string.activity_flash_programming_failed))
        }
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_end))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_close))
    }

    override suspend fun readFlash()  {
        val startAddress: Long = 0x030000
        val size: Long = 0xC000
        var result = ByteArray(0)
        val blockSize = 0x80
        val endAddress = startAddress + size
        val steps = size.toInt() / blockSize
        val startTime = Calendar.getInstance().time
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_read_identification))
        val moduleInfo = getIdentification()
        val vin = getVIN()
        _programmingState.tryEmit(FlashProgrammingState.Message("ID1 : " + moduleInfo?.id1))
        _programmingState.tryEmit(FlashProgrammingState.Message("ID2 : " + moduleInfo?.id2))
        _programmingState.tryEmit(FlashProgrammingState.Message("VIN : $vin"))

        val securityAccessResult =securityAccess(0x86,0x07, false)
        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
            Res.string.activity_flash_programming_security_access,
            if (securityAccessResult) Res.string.activity_flash_programming_ok else Res.string.activity_flash_programming_failed))
        if (!securityAccessResult)
            return
        var x = 0f
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_flash_reading))

        var i = startAddress
        while (i < endAddress) {
            _programmingState.tryEmit(FlashProgrammingState.Progress(((x++ * 100) / steps).toInt()))
            val data = readFlashData(i)
            if (data.size == blockSize) {
                result += data
            } else {
                _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_read_error))
                return
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
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)))

        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_end))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_close))

        try {
            result = result.copyOfRange(0, (endAddress - startAddress).toInt())
            _programmingState.tryEmit(FlashProgrammingState.FlashData(result.toList(),moduleInfo,vin))
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e{"$tag [readFlash] : ${ex.toString()}"}
        }
    }
    private suspend fun readFlashData(address: Long): ByteArray {
        val size = 128
        val start = System.currentTimeMillis()
        val message = kwp!!.write(
            moduleAddress, 0x23.toByte(),
            byteArrayOf(
                ((address and 0xFF0000L) shr 16).toByte(),
                ((address and 0x00FF00L) shr 8).toByte(),
                ((address and 0x0000FFL)).toByte(),
                0x00,
                ((size and 0xFF)).toByte()
            ),
            250
        )
        Logger.i{"$tag: ${(System.currentTimeMillis() - start)}"}
        try {
            if (message.isValid) {
                return message.answer.copyOfRange(5, message.length - 1)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readFlashData], $ex, $message"}
        }
        return byteArrayOf()
    }

    override val flashProgrammingAvailable: Boolean = false

    private suspend fun writeFlashData(data: ByteArray): Boolean {
        return kwp!!.write(
            moduleAddress,
            0x36.toByte(),
            data,
            100
        ).isValid
    }

    fun String.toBcdByteArray(): ByteArray {
        val normalized = if (this.length % 2 != 0) "0$this" else this

        return normalized.chunked(2)
            .map { it.toInt().toBcd() }
            .toByteArray()
    }
    fun Int.toBcd(): Byte {
        require(this in 0..99) { "Must be in 0..99" }
        return (((this / 10) shl 4) or (this % 10)).toByte()
    }
}
