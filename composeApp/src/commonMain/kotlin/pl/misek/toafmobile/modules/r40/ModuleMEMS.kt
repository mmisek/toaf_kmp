package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CancellationException
import pl.misek.toafmobile.coding.ZcsCoding
import pl.misek.toafmobile.control.ControlElement
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
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val tag = "ModuleMEMS"
class ModuleMEMS(private var kwp: ProtocolKWP) : IModuleZCS, IModuleFlashProgramming, IModule,
    IModuleControl,
    IModuleLiveData {
    private val moduleAddress = ModuleAddress.MODULE_MEMS.address
    private val _programmingState =  MutableSharedFlow<FlashProgrammingState>(replay = 10, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val programmingState = _programmingState.asSharedFlow()
    private val liveDataBlocksToRead: MutableList<Byte> =
        ArrayList()
    override fun getKoinName() = KoinNames.ModuleR40Mems

    override fun getModuleProtocol() = kwp

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

        for (i in 0..2) if (securityAccess(0xA0)) return true
        return false
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        run {
            for (b in liveDataBlocksToRead) {
                val message = kwp.write(moduleAddress, 0x21.toByte(), byteArrayOf(b), 250)
                try {
                    if (message.isValid) {
                        result[b.toInt()] = message.answer.copyOfRange(6, message.length - 1)
                    } else return null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex : Exception)
                {
                    Logger.e {"$tag[ readLiveData], $ex, $message"}
                }
            }
        }
        return result
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        var result = false
        for (i in 0..2) {
            result = securityAccess(0xA0)
            if (result) break
        }
        if (result) {
            if (controlElement.block == 0x31 && controlElement.byteInBlock == 0xC5) {
                val x = controlElement.controlSetValue!!.value.toShort()
                result = kwp.write(
                    moduleAddress,
                    controlElement.block.toByte(),
                    byteArrayOf(
                        controlElement.byteInBlock.toByte(),
                        ((x.toInt() and 0xFF00) shr 8).toByte(),
                        (x.toInt() and 0xFF).toByte()
                    )
                ).isValid
            } else {
                result = kwp.write(
                    moduleAddress,
                    controlElement.block.toByte(),
                    byteArrayOf(controlElement.byteInBlock.toByte(), controlElement.value.toByte())
                ).isValid
            }
        }
        endDiagnose()
        return result
    }
    
    override fun getImageResource() = Res.drawable.module_engine
    
    override fun getModuleDescription() = Res.string.module_description_mems

    override fun getModuleName() =  ModuleAddress.MODULE_MEMS.showName

    override fun getModuleAddress() = ModuleAddress.MODULE_MEMS

    override suspend fun clearAllErrors(): Boolean {
        return kwp.write(moduleAddress, 0x14.toByte(), byteArrayOf(0x00, 0x00), 1000).isValid
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        if (securityAccess(0xA0)) {
            val message =
                kwp.write(moduleAddress, 0x18.toByte(), byteArrayOf(0x00, 0x00, 0x00), 1000)
            try {
                if (message.isValid) {
                    for (i in 0 until (message.answer[5].toInt() and 0xFF)) {
                        val errorCode =
                            ((message.answer[6 + 3 * i].toInt() and 0xFF) shl 8) + (message.answer[7 + 3 * i].toInt() and 0xFF)
                        if (result == null) result = ArrayList()
                        result.add(
                            ModuleError(
                                ModuleAddress.MODULE_MEMS_ERROR,
                                String.format("%04X", errorCode).uppercase(
                                    Locale.getDefault()
                                ),
                                ArrayList()
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
            endDiagnose()
        }
        return result
    }

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        var message = kwp.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x9A.toByte()), 250)
        try{
            if (message.isValid) {
                info = ModuleInfo()
                info.additionalInfo = ArrayList()
                info.id1 = String(message.answer.copyOfRange(6, 9)) + String.format(
                    "%02X%02X%02X",
                    message.answer[9].toInt() and 0xFF,
                    message.answer[10].toInt() and 0xFF,
                    message.answer[11].toInt() and 0xFF
                )
            }
            message = kwp.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x9B.toByte()), 1000)
            if (message.isValid) {
                if (info == null) {
                    info = ModuleInfo()
                    info.additionalInfo = ArrayList()
                }
                info.ci = String.format("%02X", message.answer[6].toInt() and 0xFF)
            }
            message = kwp.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x9C.toByte()), 1000)
            if (message.isValid) {
                if (info == null) {
                    info = ModuleInfo()
                    info.additionalInfo = ArrayList()
                }

                info.di = String.format("%02X", message.answer[6].toInt() and 0xFF)
            }
            message = kwp.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x9E.toByte()), 1000)
            if (message.isValid) {
                if (info == null) {
                    info = ModuleInfo()
                    info.additionalInfo = ArrayList()
                }

                info.producer = ModulesFunction.getProducer(message.answer[6].toInt())
            }

            message = kwp.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x87.toByte()))
            if (message.isValid) {
                try {
                    if (info == null) {
                        info = ModuleInfo()
                        info.additionalInfo = ArrayList()
                    }

                    info.id2 = String(message.answer.copyOfRange(26,29)
                    ) + String.format(
                        "%02X%02X%02X",
                        message.answer[29].toInt() and 0xFF,
                        message.answer[30].toInt() and 0xFF,
                        message.answer[31].toInt() and 0xFF
                    )
                    info.additionalInfo.add(
                        Pair(
                            Res.string.module_info_programing_date,
                            String.format(
                                "%02X/%02X/%04X",
                                message.answer[22].toInt() and 0xFF,
                                message.answer[23].toInt() and 0xFF,
                                ((message.answer[24].toInt() and 0xFF) shl 8) + (message.answer[25].toInt() and 0xFF)
                            )
                        )
                    )

                    info.serial = String(message.answer.copyOfRange( 36, 46))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Logger.e{"$tag : ${ex.toString()}"}
                }
            }
            message = kwp.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x32.toByte()), 1000)
            if (message.isValid) {
                if (info == null) {
                    info = ModuleInfo()
                    info.additionalInfo = ArrayList()
                }

                info.additionalInfo.add(
                    Pair(
                        Res.string.module_info_application_ident,
                        String(message.answer.copyOfRange(6, 14))
                    )
                )
                info.additionalInfo.add(
                    Pair(
                        Res.string.module_info_calibration_ident,
                        String(message.answer.copyOfRange(14, 22))
                    )
                )
            }
            message = kwp.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x33.toByte()), 1000)
            if (message.isValid) {
                if (info == null) {
                    info = ModuleInfo()
                    info.additionalInfo = ArrayList()
                }

                info.additionalInfo.add(
                    Pair(
                        Res.string.module_info_boot_ident,
                        String(message.answer.copyOfRange( 14, 22))
                    )
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

    override suspend fun getVIN(): String? {
        val message = kwp.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x87.toByte()), 1000)
        try {
            return if (message.isValid) {
                String(message.answer.copyOfRange(6, 17)) +
                        String.format(
                            "%02X%02X%02X",
                            message.answer[17].toInt() and 0xFF,
                            message.answer[18].toInt() and 0xFF,
                            message.answer[19].toInt() and 0xFF
                        )
            } else {
                null
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getVIN], $ex,$message"}
            return null
        }
    }

    override suspend fun getOdometer(): String? {
        return null
        /*
        OBDMessage message = kwp.Write(MODULE_ADDRESS,(byte)0x2C, new byte[] {0x10,0x0F,(byte)0xD0});
        if (message.isValid())
        {
            return String.format("%d",(message.answer[6] >> 8) + message.answer[7]*10);
        }
        else
            return "";
         */
    }

    private suspend fun endDiagnose(): Boolean {
        return kwp.write(moduleAddress, 0x3E.toByte(), byteArrayOf()).isValid
    }

    private suspend fun securityAccess(level: Int): Boolean {
        var result = false
        var message = OBDMessage()
        try {
            message.isValid = true
            if (level != 0) message =
                kwp.write(moduleAddress, 0x10.toByte(), byteArrayOf(level.toByte()), 500)
            if (message.isValid) {
                message = kwp.write(moduleAddress, 0x27.toByte(), byteArrayOf(0x01.toByte()), 500)
                if (message.isValid) {
                    val seed =
                        ((message.answer[6].toInt() and 0xFF) shl 8) + (message.answer[7].toInt() and 0xFF)
                    if (seed == 0x0000) result = true
                    else {
                        val key = securityAccessCalc(seed)
                        message = kwp.write(
                            moduleAddress,
                            0x27.toByte(),
                            byteArrayOf(
                                (0x02).toByte(),
                                ((key and 0xFF00) shr 8).toByte(),
                                (key and 0x00FF).toByte()
                            ),
                            1000
                        )
                        result = message.isValid
                    }
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex :Exception)
        {
            Logger.e {"$tag [securityAccess], $ex, $message"}
        }
        return result
    }

    private fun securityAccessCalc(inSeed: Int): Int {
        var seed = inSeed
        var n = 1
        if ((seed and 0x8000) == 0x8000) n += 8
        if ((seed and 0x0080) == 0x0080) n += 4
        if ((seed and 0x0010) == 0x0010) n += 2
        if ((seed and 0x0001) == 0x0001) n += 1

        var key = 0x0000
        for (i in 0 until n) {
            var x = 0
            if ((seed and 0x2000) == 0x2000) x = 1
            x = if ((seed and 0x0008) == 0x0008) x and 1
            else 0
            x = x.inv() and 0x01
            key = seed shr 1
            key = key and 0xFFFE
            key = key xor x
            key = key and 0x7FFF
            x = 0
            if ((seed and 0x0200) == 0x0200) x = x xor 1
            if ((seed and 0x0100) == 0x0100) x = x xor 1
            if ((seed and 0x0004) == 0x0004) x = x xor 1
            if ((seed and 0x0002) == 0x0002) x = x xor 1
            if (x == 1) key = key or 0x8000
            seed = key
        }
        return key
    }

    override suspend fun readZCS(): ZcsCoding? {
        var result: ZcsCoding? = null
        if (securityAccess(0x85)) {
            val message = kwp.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x8A.toByte()), 1000)
            try {
                if (message.isValid) {
                    try {
                        result = ZcsCoding(message.answer.copyOfRange(6, 26))
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Logger.e { "$tag : ${ex.toString()}" }
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [readZCS], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun writeZCS(data: ZcsCoding): Boolean {
        if (securityAccess(0x85)) {
            val message = kwp.write(
                moduleAddress,
                0x3B.toByte(),
                byteArrayOf(0x8A.toByte()) + data.bytes)
            return message.isValid
        }
        return false
    }

    override suspend fun writeFlash(data: ByteArray) {
        var blockSize = 0xF8
        val steps = 0x3B
        val startTime = Calendar.getInstance().time

        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                Res.string.activity_flash_programming_file_size,
                data.size.toString()))
        if (data.size != 0x3FFE) {
            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_wrong_file_size))
        } else {
            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_read_identification))
            val moduleInfo = getIdentification()
            val vin = getVIN()
            if (moduleInfo != null) {
                _programmingState.tryEmit(FlashProgrammingState.Message("ID1 : " + moduleInfo.id1))
                _programmingState.tryEmit(FlashProgrammingState.Message("ID2 : " + moduleInfo.id2))
            }
            _programmingState.tryEmit(FlashProgrammingState.Message("VIN : $vin"))
            val m0 = kwp.write(
                moduleAddress,
                0x10.toByte(),
                byteArrayOf(0x85.toByte(), 0x01, 0x00, 0x25, 0x80.toByte())
            )
            if (m0.isValid && securityAccess(0)) {
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_security_access,
                        Res.string.activity_flash_programming_ok)
                    )
                kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0x01), 500)
                if (m0.isValid) {
                    try {
                        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                Res.string.activity_flash_programming_diagnostic_session,
                                Res.string.activity_flash_programming_ok)
                            )
                           var m1 = kwp.write(
                                moduleAddress,
                                0x1A.toByte(),
                                byteArrayOf(0x9A.toByte()),
                                250
                            )
                            var m2 = kwp.write(
                                moduleAddress,
                                0x10.toByte(),
                                byteArrayOf(0x85.toByte(), 0x01, 0x00, 0x25, 0x80.toByte())
                            )
                            var m3 = securityAccess(0)
                            var m4 = kwp.write(
                                moduleAddress,
                                0x34.toByte(),
                                byteArrayOf(
                                    0x13.toByte(),
                                    0xC0.toByte(),
                                    0xF8.toByte(),
                                    0x00,
                                    0x30,
                                    0xB0.toByte()
                                )
                            )
                            if (m1.isValid && m2.isValid && m3 && m4.isValid) {
                                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                        Res.string.activity_flash_programming_flash_erasing,
                                        Res.string.activity_flash_programming_ok)
                                    )
                                delay(200)

                                var progress = 0
                                _programmingState.tryEmit(FlashProgrammingState.Progress(progress))
                                var x = 0xf8
                                while (x < 0x3928) {
                                    val dataToWrite = data.copyOfRange(x, x + blockSize)
                                    if (writeFlashData(dataToWrite)) {
                                        _programmingState.tryEmit(FlashProgrammingState.Progress(progress++ * 100 / steps))
                                    } else {
                                        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_write_error))
                                        break
                                    }
                                    x += blockSize
                                }
                                var writeOk = true
                                blockSize = 0x80
                                var dataToWrite =
                                    data.copyOfRange(0x3928, 0x3928 + blockSize)
                                if (writeFlashData(dataToWrite)) {
                                    _programmingState.tryEmit(FlashProgrammingState.Progress(progress++ * 100 / steps))
                                } else {
                                    _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_write_error))
                                    writeOk = false
                                }
                                if (writeOk) {
                                    m1 = kwp.write(moduleAddress, 0x37.toByte(), byteArrayOf())
                                    m2 = kwp.write(
                                        moduleAddress,
                                        0x34.toByte(),
                                        byteArrayOf(
                                            0x13.toByte(),
                                            0xC0.toByte(),
                                            0x00,
                                            0x00,
                                            0x00,
                                            0x80.toByte()
                                        )
                                    )
                                    if (m1.isValid && m2.isValid) {
                                        dataToWrite = data.copyOfRange(0x00, 0xF8)
                                        if (writeFlashData(dataToWrite)) {
                                            _programmingState.tryEmit(FlashProgrammingState.Progress(progress++ * 100 / steps))
                                        } else {
                                            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_write_error))
                                        }
                                    }
                                }
                                m1 = kwp.write(moduleAddress, 0x37.toByte(), byteArrayOf())
                                m2 = kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0x02))
                                m4 = kwp.write(
                                    moduleAddress,
                                    0x1A.toByte(),
                                    byteArrayOf(0x9A.toByte())
                                )
                                val m5 = kwp.write(
                                    moduleAddress,
                                    0x10.toByte(),
                                    byteArrayOf(0xA0.toByte())
                                )
                                m3 = securityAccess(0)
                                val m6 = kwp.write(
                                    moduleAddress,
                                    0x11.toByte(),
                                    byteArrayOf(0x01.toByte())
                                )
                                val m7 = kwp.write(
                                    moduleAddress,
                                    0x1A.toByte(),
                                    byteArrayOf(0x9A.toByte())
                                )
                                if (m1.isValid && m2.isValid and m4.isValid && m3 && m5.isValid && m6.isValid && m7.isValid)
                                    _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                        Res.string.activity_flash_programming_flash_writing_end,
                                        Res.string.activity_flash_programming_ok)
                                    )
                                else _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                        Res.string.activity_flash_programming_flash_writing_end,
                                        Res.string.activity_flash_programming_failed)
                                    )
                                val endTime = Calendar.getInstance().time
                                val totalMillis = endTime.time - startTime.time
                                val seconds = (totalMillis / 1000).toInt() % 60
                                val minutes = ((totalMillis / 1000).toInt() / 60) % 60
                                val hours = (totalMillis / 1000).toInt() / 3600
                                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                        Res.string.activity_flash_programming_write_time,
                                        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                                    ))
                                _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_ecu_reset))
                            } else {
                                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                        Res.string.activity_flash_programming_flash_erasing,
                                        Res.string.activity_flash_programming_failed)
                                    )
                            }
                        }
                    catch (ex: CancellationException) {
                        throw ex
                    }
                    catch (ex: Exception) {
                        Logger.e{"$tag [writaFlash]: ${ex.toString()}"}
                    }
                } else _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_diagnostic_session,
                        Res.string.activity_flash_programming_failed)
                    )
            } else _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                    Res.string.activity_flash_programming_security_access,
                    Res.string.activity_flash_programming_failed)
                )
        }
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_end))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_close))
    }

    override suspend fun readFlash() {
        val startAddress: Long = 0x13C000
        val size: Long = 0x3FFE
        var result = ByteArray(0)
        var blockSize = 0xFE
        val endAddress = startAddress + size
        val steps = size.toInt() / blockSize
        val startTime = Calendar.getInstance().time
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_read_identification))

        val moduleInfo = getIdentification()
        val vin = getVIN()

        if (moduleInfo != null) {
            _programmingState.tryEmit(FlashProgrammingState.Message("ID1 : " + moduleInfo.id1))
            _programmingState.tryEmit(FlashProgrammingState.Message("ID2 : " + moduleInfo.id2))
        }
        _programmingState.tryEmit(FlashProgrammingState.Message("VIN : $vin"))

        val securityAccessResult = securityAccess(0xA0)
        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                   Res.string.activity_flash_programming_security_access,
                   if (securityAccessResult) Res.string.activity_flash_programming_ok else Res.string.activity_flash_programming_failed
             )
        )
        if (!securityAccessResult)
            return
        var x = 0f
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_flash_reading))

        var i = startAddress
        while (i < endAddress) {
            _programmingState.tryEmit(FlashProgrammingState.Progress(((x++ * 100) / steps).toInt()))
            if (i == 0x13FF80L) blockSize = 0x7E
            val data = readFlashData(i, blockSize)
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
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            ))

        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_end))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_close))

        try {
            result = result.copyOfRange(0, (endAddress - startAddress).toInt())
            _programmingState.tryEmit(FlashProgrammingState.FlashData(result.toList(),moduleInfo,vin))
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e{"$tag [readFlash]: ${ex.toString()}"}
        }
    }

    private suspend fun readFlashData(address: Long, size: Int): ByteArray {
        val message = kwp.write(
            moduleAddress, 0x23.toByte(),
            byteArrayOf(
                ((address and 0xFF0000L) shr 16).toByte(),
                ((address and 0x00FF00L) shr 8).toByte(),
                ((address and 0x0000FFL)).toByte(),
                ((size and 0xFF)).toByte()
            ),
            50
        )
        var result = ByteArray(0)
        try {
            result = message.answer.copyOfRange(3, message.length - 1)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception) {
            Logger.e{"$tag [readFlash]: ${ex.toString()}, $message"}
            return result
        }
        return result
    }

    private suspend fun writeFlashData(data: ByteArray): Boolean {
        return kwp.write(
            moduleAddress,
            0x36.toByte(),
            data,
            20
        ).isValid
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.R40, CarType.RJO)
    }
}
