package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.coding.ZcsCoding
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
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
import pl.misek.toafmobile.modules.interfaces.IModuleZCS
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import pl.misek.toafmobile.tools.JsonSupport
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Calendar
import java.util.Locale

private const val tag = "ModuleME72"
class ModuleME72(private var kwp: ProtocolKWP) : IModuleZCS, IModuleFlashProgramming, IModule,
    IModuleControl,
    IModuleLiveData {
    private val moduleAddress = ModuleAddress.MODULE_ME72.address
    private val _programmingState =  MutableSharedFlow<FlashProgrammingState>(replay = 10, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val programmingState = _programmingState.asSharedFlow()
    private val liveDataBlocksToRead: MutableList<Byte> =
        ArrayList()

    override fun getKoinName() = KoinNames.ModuleMe72

    override fun getModuleProtocol(): IProtocol {
        return kwp
    }

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol as ProtocolKWP
    }

    override suspend fun reset(): Boolean {
        //return kwp.Write(MODULE_ADDRESS,MODULE_DDE,new byte[] {(byte)0x82}).isValid();
        return false
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        liveDataBlocksToRead.clear()
        for (el in liveDataElements) liveDataBlocksToRead.add(el.block.toByte())
        return securityAccess(0x03.toByte().toInt())
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (b in liveDataBlocksToRead) {
            val message = kwp.write(moduleAddress, 0x21.toByte(), byteArrayOf(b), 150)
            try {
                if (message.isValid) {
                    result[b.toInt()] =
                        message.answer.copyOfRange(6, message.length - 1)
                } else return null
            }catch (ex : Exception)
            {
                Logger.e {"$tag [readLiveData], $ex, $message"}
                return null
            }
        }
        return result
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        return if (securityAccess(3)) {
            kwp.write(
                moduleAddress,
                0x30.toByte(),
                byteArrayOf(controlElement.block.toByte(), controlElement.value.toByte())
            ).isValid
        } else false
    }

    override fun getImageResource() = Res.drawable.module_engine

    override fun getModuleDescription() = Res.string.module_description_ems

    override fun getModuleName(): String {
        return ModuleAddress.MODULE_EMS.showName
    }

    override fun getModuleAddress(): ModuleAddress {
        return ModuleAddress.MODULE_EMS
    }

    override suspend fun clearAllErrors(): Boolean {
        return kwp.write(moduleAddress, 0x14.toByte(), byteArrayOf(0x00, 0x00)).isValid
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        if (securityAccess(3)) {
            val message = kwp.write(moduleAddress, 0x18.toByte(), byteArrayOf(0x00, 0x00, 0x00))
            try {
                if (message.isValid) {
                    val freezeFrames = JsonSupport.getEMSErrorFreezeFrame()
                    for (i in 0 until (message.answer[5].toInt() and 0xFF)) {
                        val errorCode =
                            ((message.answer[6 + 3 * i].toInt() and 0xFF) shl 8) + (message.answer[7 + 3 * i].toInt() and 0xFF)
                        val message1 = kwp.write(
                            moduleAddress, 0x17.toByte(), byteArrayOf(
                                message.answer[6 + 3 * i], message.answer[7 + 3 * i]
                            )
                        )
                        val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                        if (message1.isValid) {
                            errorDescription.add(ResourceHelper.getErrorDescriptionCount(message1.answer[8].toInt() and 0xFF))
                            for (k in 0..7) {
                                val number = message1.answer[8].toInt() and (0x01 shl k)
                                if (number == (0x01 shl k)) {
                                    ResourceMapper.stringResource(
                                        String.format(
                                            "ems_error_detail_%02X",
                                            number
                                        )
                                    )
                                        ?.let { errorDescription.add(Pair(it, null)) }
                                }
                            }
                            if (freezeFrames != null) {
                                val messageFreezeFrame = kwp.write(
                                    moduleAddress, 0x12.toByte(), byteArrayOf(
                                        0x00, 0x04,
                                        message.answer[6 + 3 * i],
                                        message.answer[7 + 3 * i]
                                    )
                                )
                                if (messageFreezeFrame.isValid) {
                                    for (element in freezeFrames) {
                                        var value: Long = 0x00
                                        var k = 0
                                        var m = element.mask
                                        do {
                                            value = value shl 8
                                            value += (messageFreezeFrame.answer[element.byteInBlock + 6 + (k++)].toInt() and 0xFF).toLong()
                                            m = m shr 8
                                        } while (m > 0x00)

                                        if (element.u2) {
                                            if (element.mask == 0xFFL) value =
                                                value.toByte().toLong()
                                            else if (element.mask == 0xFFFFL) value =
                                                value.toShort().toLong()
                                        }
                                        value = value and element.mask

                                        var mask = element.mask
                                        while ((mask and 0x01L) != 0x01L) {
                                            mask = mask shr 0x01
                                            value = value shr 0x01
                                        }
                                        errorDescription.add(
                                            Pair(
                                                element.description!!,
                                                String.format(
                                                    Locale.getDefault(),
                                                    "%.2f %s",
                                                    (value * element.factorA + element.factorB).toDouble(),
                                                    element.unit
                                                )
                                            )
                                        )
                                    }
                                }
                            } else
                                Logger.e {
                                    "$tag : No freeze frame for code ${
                                        String.format(
                                            "%04X",
                                            errorCode
                                        )
                                    }"
                                }
                        }
                        if (result == null) result = ArrayList()
                        result.add(
                            ModuleError(
                                ModuleAddress.MODULE_EMS,
                                String.format("%04X", errorCode).uppercase(
                                    Locale.getDefault()
                                ),
                                errorDescription
                            )
                        )
                    }
                }
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getAllErros], $ex ,$message"}
            }
        }
        return result
    }

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        val message = kwp.write(moduleAddress, 0xA2.toByte(), byteArrayOf(), 300)
        val aif = readAIF()
        try {
            if (message.isValid) {
                try {
                    info = ModuleInfo()
                    val answer = message.answer
                    info.additionalInfo = ArrayList()
                    info.id1 = String(answer.copyOfRange(5, 12))
                    if (aif!= null)
                        info.id2 = aif.id2
                    info.hw = String(answer.copyOfRange(12, 14))
                    info.ci = String(answer.copyOfRange(14, 16))
                    info.di = String(answer.copyOfRange(16, 18))
                    info.bi = String(answer.copyOfRange(18, 20))
                    info.date = String(answer.copyOfRange(20, 22)) + "/" + String(answer.copyOfRange(22, 24))
                    info.serial = String(answer.copyOfRange(24,34))
                } catch (ex: Exception) {
                    Logger.e { "$tag : ${ex.toString()}" }
                }
            }
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getIdentification], $ex, $message"}
        }

        return info
    }
    private suspend fun readAIF() : Aif?
    {
        //var message = kwp.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x01))
        //if (message.isValid)
        //{
            //val baseAddressHi = message.answer[0x42].toInt() and 0xFF
            //val baseAddressMi = message.answer[0x43].toInt() and 0xFF
            //val baseAddressLo = message.answer[0x44].toInt() and 0xFF
            //var address = (baseAddressHi shl 16) + (baseAddressMi shl 8) + baseAddressLo
        var address = 0x81FD54
        var aif = Aif("","")
        for (x in 0..14) {
            address += 0x2E * x
            val data = byteArrayOf(
                ((address shr 16) and 0xFF).toByte(),
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte(),
                0x00,
                0x2E
            )
            val message = kwp.write(moduleAddress, 0x23, data)
            try {
                if (message.isValid && (message.length == 0x2F + 0x05)) {
                    if (message.answer[5] == 0xFF.toByte())
                        break
                    val vin = String(message.answer.copyOfRange(5, 16)) + hexExpand(message.answer, 16, 19)
                    val id2 = String(message.answer.copyOfRange(25, 28)) + hexExpand(message.answer, 28, 31)
                    aif = Aif(vin, id2)
                }
            } catch (ex: Exception) {
                Logger.e { "$tag [getAIF], $ex, $message" }
                return null
            }
        }
        return aif
        //}
        //return null
    }

    override suspend fun getVIN(): String?{
        val aif = readAIF()
        return aif?.vin
    }


    override suspend fun getOdometer(): String? {
        return null
    }

    private suspend fun startDiagnosticSession(level: Int): Boolean {
        //if (kwp is ProtocolKWPStarEMS2K) testerPresent()
        return kwp.write(moduleAddress, 0x10.toByte(), byteArrayOf(level.toByte()), 200).isValid
    }

    private suspend fun endDiagnosticSession(): Boolean {
        //if (kwp is ProtocolKWPStarEMS2K) testerPresent()
        return kwp.write(moduleAddress, 0x20.toByte(), byteArrayOf()).isValid
    }

    private suspend fun testerPresent(): Boolean {
        kwp.write(moduleAddress, 0x81.toByte(), byteArrayOf(), 500)
        return kwp.write(moduleAddress, 0x3E.toByte(), byteArrayOf(), 500).isValid
    }

    private suspend fun securityAccess(level: Int): Boolean {
        var result = false
        val message = kwp.write(moduleAddress, 0x27.toByte(), byteArrayOf(level.toByte()), 500)
        try {
            if (message.isValid) {
                val seed =
                //if (kwp is ProtocolKWPStarEMS2K)
                //        ((message.answer[5].toInt() and 0xFF) shl 8) + (message.answer[6].toInt() and 0xFF)
                    //else
                    ((message.answer[6].toInt() and 0xFF) shl 8) + (message.answer[7].toInt() and 0xFF)
                if (seed == 0x0000) result = true
                else {
                    val key = securityAccessCalc(seed, level)
                    result = kwp.write(
                        moduleAddress,
                        0x27.toByte(),
                        byteArrayOf(
                            (level + 1).toByte(),
                            ((key and 0xFF00) shr 8).toByte(),
                            (key and 0x00FF).toByte()
                        ),
                        200
                    ).isValid
                }
            }
        } catch (ex : Exception)
        {
            Logger.e {"$tag [securityAccess], $ex, $message"}
        }


        return result
    }

    private fun securityAccessCalc(seed: Int, level: Int): Int {
        Logger.i{"$tag : Security Access Seed - ${String.format("%04X", seed)}"}
        var key = seed
        val n = when (level) {
            0x01 -> 3
            0x03 -> ((((key shr 8) and 0xFF) xor 0x34) and 0x0F ) + 1
            0x05 -> (((key and 0xFF) xor 0x34) and 0x13) + 2
            0x07 -> ((((key and 0xFF00) shr 8 ) xor 0x34) and 0x0F) + 5
            0x09 -> (((key and 0xFF) xor 0x34) and 0x0B) +3
            else -> 0
        }
        for (i in 0 until n) {
            var x = 0
            if ((key and 0x4000) == 0x4000) x = x xor 1
            if ((key and 0x0040) == 0x0040) x = x xor 1
            if ((key and 0x0020) == 0x0020) x = x xor 1
            if ((key and 0x0002) == 0x0002) x = x xor 1
            if ((key and 0x0001) == 0x0001) x = x xor 1
            key = ((key shl 1) and 0xFFFE) xor x
        }
        return key
    }

    private suspend fun bootMode(): Boolean {
        return kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0xFB.toByte())).isValid
    }

    override suspend fun readZCS(): ZcsCoding? {
        var result: ZcsCoding? = null
        if (startDiagnosticSession(0x81)) {
            if (securityAccess(3)) {
                val message = kwp.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x8A.toByte()))
                try {
                    if (message.isValid) {
                        try {
                            result = ZcsCoding(message.answer.copyOfRange(6, 26))
                        } catch (_: Exception) {
                            return ZcsCoding()
                        }
                    }
                } catch (ex : Exception)
                {
                    Logger.e {"$tag [readZCS], $ex, $message"}
                }
            }
        }
        return result
    }

    override suspend fun writeZCS(data: ZcsCoding): Boolean {
        if (startDiagnosticSession(0x85)) {
            if (securityAccess(7)) {
                //if (BootMode())
                run {
                    return kwp.write(
                        moduleAddress,
                        0x3B.toByte(),
                        byteArrayOf(0x8A.toByte()) + data.bytes).isValid
                }
            }
        }
        return false
    }

    override suspend fun writeFlash(data: ByteArray)  {
        //var inData = data
        val blockSize = 0x5C
        val steps = data.size / blockSize
        val startTime = Calendar.getInstance().time

        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                Res.string.activity_flash_programming_file_size,
                data.size.toString()))
        if (data.size != 0x8000) {
            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_wrong_file_size))
        } else {
            //inData = data.copyOfRange(0, (data.size / blockSize) * blockSize)
            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_read_identification))
            val moduleInfo = getIdentification()
            val vin = getVIN()
            _programmingState.tryEmit(FlashProgrammingState.Message("ID1 : " + moduleInfo?.id1))
            _programmingState.tryEmit(FlashProgrammingState.Message("ID2 : " + moduleInfo?.id2))
            _programmingState.tryEmit(FlashProgrammingState.Message("VIN : $vin"))
            endDiagnosticSession()
            try {
                delay(1000)
            } catch (_ : Exception) {
            }
            if (startDiagnosticSession(0x85) && securityAccess(7)) {
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_security_access,
                        Res.string.activity_flash_programming_ok))
                val eraseFlash = kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0x01), 500)
                if (eraseFlash.wholeMessage!= null && eraseFlash.wholeMessage!!.size == 22 && eraseFlash.wholeMessage!![19].toInt() == 0x71)  //if (kwp.Write(MODULE_ADDRESS, (byte) 0x31, new byte[]{0x01},500).isValid())
                {
                    if (kwp.write(
                            moduleAddress,
                            0x34.toByte(),
                            byteArrayOf(0x10, 0x80.toByte(), 0x00, 0x00, 0x00, 0x7F, 0xF0.toByte())
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
                            val m1 = kwp.write(moduleAddress, 0x37.toByte(), byteArrayOf(), 2000)
                            val m2 = kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0x02), 2000)
                            val m3 = kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0x03), 2000)
                            val m4 = kwp.write(
                                moduleAddress,
                                0x11.toByte(),
                                byteArrayOf(0x01.toByte()),
                                2000
                            )
                            if (m1.isValid && (m2.wholeMessage != null && m2.wholeMessage!!.size == 22) and (m2.wholeMessage!![19].toInt() == 0x71) && m3.isValid && m4.isValid) _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                    Res.string.activity_flash_programming_flash_writing_end,
                                    Res.string.activity_flash_programming_ok))
                            else _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                    Res.string.activity_flash_programming_flash_writing_end,
                                    Res.string.activity_flash_programming_failed))
                            val endTime = Calendar.getInstance().time
                            val totalMillis = endTime.time - startTime.time
                            val seconds = (totalMillis / 1000).toInt() % 60
                            val minutes = ((totalMillis / 1000).toInt() / 60) % 60
                            val hours = (totalMillis / 1000).toInt() / 3600
                            _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                    Res.string.activity_flash_programming_write_time,
                                    String.format("%02i:%02i:%02i", hours, minutes, seconds)))
                            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_ecu_reset))
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
        val startAddress: Long = 0x108000
        val size: Long = 0x8000
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

        val startDiagnosticSessionResult = startDiagnosticSession(0x86)
        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
            Res.string.activity_flash_programming_diagnostic_session,
            if (startDiagnosticSessionResult) Res.string.activity_flash_programming_ok else Res.string.activity_flash_programming_failed))
        if (!startDiagnosticSessionResult)
            return
        val securityAccessResult =securityAccess(0x09)
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
        } catch (ex: Exception) {
            Logger.e{"$tag [readFlash] : ${ex.toString()}"}
        }
    }

    private suspend fun readFlashData(address: Long): ByteArray {
        val size = 128
        val start = System.currentTimeMillis()
        val message = kwp.write(
            moduleAddress, 0x23.toByte(),
            byteArrayOf(
                ((address and 0xFF0000L) shr 16).toByte(),
                ((address and 0x00FF00L) shr 8).toByte(),
                ((address and 0x0000FFL)).toByte(),
                ((size and 0xFF)).toByte()
            ),
            250
        )
        Logger.i{"$tag: ${(System.currentTimeMillis() - start)}"}
        try {
            if (message.isValid) {
                return message.answer.copyOfRange(5, message.length - 1)
            }
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readFlashData], $ex, $message"}
        }
        return byteArrayOf()
    }

    private suspend fun writeFlashData(data: ByteArray): Boolean {
        kwp.write(
            moduleAddress,
            0x36.toByte(),
            data,
            100
        )
        return true
        //return  message.isValid();
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.L322)
    }

    private fun hexExpand(data : ByteArray, fromIndex : Int, toIndex :Int) : String
    {
        return data.copyOfRange(fromIndex,toIndex).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }

    data class Aif ( val vin : String, val id2 : String)

}
