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
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.modules.interfaces.IModuleZCS
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private const val tag = "ModuleMEMS"
private const val MEMS_FLASH_PENDING_MS = 15000L   // flash erase / 31 02 (response pending) moga trwac kilka s
private const val AIF_BASE = 0x13A000L             // region rekordow AIF/VIN - sloty po 0x2E, nowy VIN do pierwszego pustego
private const val AIF_SLOT_SIZE = 0x2E             // 46 B - rozmiar rekordu AIF (jak EmsAif.RECORD_SIZE)
private const val AIF_SLOTS = 64                   // maks. liczba skanowanych slotow (stop na pierwszym pustym)
class ModuleMEMS(private var kwp: ProtocolKWP) : IModuleZCS, IModuleFlashProgramming, IModule,
    IModuleControl,
    IModuleLiveData, IModuleVIN {
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
        for ((_, _, _, block) in liveDataElements) liveDataBlocksToRead.add(block.toByte())

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
                    Logger.e{"$tag : $ex"}
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

    override fun getVinLength(): Int = 17

    /**
     * Zapis VIN przez AIF MEMS3. Rekord AIF (46 B, format jak EmsAif) jest w regionie 0x13A000
     * w 4 slotach po 0x2E. Nowy VIN zapisuje sie do PIERWSZEGO PUSTEGO (FF) slotu, bez kasowania -
     * sterownik czyta ostatni wazny rekord. Data programowania ustawiana na biezaca.
     */
    override suspend fun writeVIN(newVin: String): Boolean {
        val vin = newVin.uppercase()
        if (!securityAccess(0xA0)) {
            Logger.e { "$tag [writeVIN] : brak dostepu (A0) do odczytu AIF" }
            return false
        }
        // czytamy sloty po kolei az do pierwszego pustego (FF) - on jest celem zapisu,
        // a bieżący rekord to slot tuż przed nim
        var emptyIdx = -1
        var current: ByteArray? = null
        for (i in 0 until AIF_SLOTS) {
            val rec = readAifRecord(AIF_BASE + i * AIF_SLOT_SIZE)
            if (rec == null || rec.all { b -> b == 0xFF.toByte() }) { emptyIdx = i; break }
            current = rec
        }
        val currentRecord = current
        if (currentRecord == null) { Logger.e { "$tag [writeVIN] : brak biezacego rekordu AIF" }; return false }
        if (emptyIdx < 0) { Logger.e { "$tag [writeVIN] : brak wolnego slotu AIF - wymaga skasowania regionu" }; return false }
        Logger.i { "$tag [writeVIN] : biezacy slot ${emptyIdx - 1} ${EmsAif.parse(currentRecord)}" }
        val newRecord = EmsAif.withVin(currentRecord, vin, Calendar.getInstance()) ?: run {
            Logger.e { "$tag [writeVIN] : bledny VIN (wymagane 11 znakow + 6 cyfr)" }; return false
        }
        val slotAddr = AIF_BASE + emptyIdx * AIF_SLOT_SIZE
        Logger.i { "$tag [writeVIN] : zapis do slotu $emptyIdx @ %06X : ${EmsAif.parse(newRecord)}".format(slotAddr) }

        // programowanie: sesja 85, seed-key, download do pustego slotu (bez kasowania), 36 + rekord, 37
        val session = kwp.write(moduleAddress, 0x10.toByte(), byteArrayOf(0x85.toByte(), 0x01, 0x00, 0x25, 0x80.toByte()))
        if (!session.isValid || !securityAccess(0)) {
            Logger.e { "$tag [writeVIN] : sesja 85 / security nieudane" }; return false
        }
        val download = kwp.write(
            moduleAddress, 0x34.toByte(),
            byteArrayOf(
                ((slotAddr shr 16) and 0xFFL).toByte(),
                ((slotAddr shr 8) and 0xFFL).toByte(),
                (slotAddr and 0xFFL).toByte(),
                0x00, 0x00, AIF_SLOT_SIZE.toByte()
            )
        )
        if (!download.isValid) { Logger.e { "$tag [writeVIN] : request download nieudany" }; return false }
        val wrote = writeFlashData(newRecord)
        val exit = kwp.write(moduleAddress, 0x37.toByte(), byteArrayOf())
        kwp.write(moduleAddress, 0x10.toByte(), byteArrayOf(0xA0.toByte()))
        securityAccess(0)
        kwp.write(moduleAddress, 0x11.toByte(), byteArrayOf(0x01))   // reset
        val ok = wrote && exit.isValid
        Logger.i { "$tag [writeVIN] : ${if (ok) "********** VIN WRITE OK **********" else "FAILED - 36/37 odrzucone"}" }
        return ok
    }

    private suspend fun readAifRecord(address: Long): ByteArray? {
        val size = AIF_SLOT_SIZE
        val message = kwp.write(
            moduleAddress, 0x23.toByte(),
            byteArrayOf(
                ((address shr 16) and 0xFFL).toByte(),
                ((address shr 8) and 0xFFL).toByte(),
                (address and 0xFFL).toByte(),
                size.toByte()
            ),
            (size * 2 + 100).toLong()
        )
        val ans = message.answer
        if (ans.size < size + 1) return null
        return ans.copyOfRange(ans.size - 1 - size, ans.size - 1)
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
                        Logger.e { "$tag : $ex" }
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
                // kasowanie: 31 01 -> 7F 31 78 (pending) -> 71 01; flash erase trwa kilka s, czekamy na 71 01
                val erase = kwp.writeWithPending(moduleAddress, 0x31.toByte(), byteArrayOf(0x01), 1000L, MEMS_FLASH_PENDING_MS)
                if (erase.isValid) {
                    try {
                        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                Res.string.activity_flash_programming_diagnostic_session,
                                Res.string.activity_flash_programming_ok)
                            )
                            // po erase od razu download (jak referencja - bez ponownej sesji/ident)
                            var m1 = erase
                            var m2 = erase
                            var m3 = true
                            // download #1: 34 addr 13C0F8 size 36B0 (14000 B) - jak referencja
                            var m4 = kwp.write(
                                moduleAddress,
                                0x34.toByte(),
                                byteArrayOf(
                                    0x13.toByte(),
                                    0xC0.toByte(),
                                    0xF8.toByte(),
                                    0x00,
                                    0x36,
                                    0xB0.toByte()
                                )
                            )
                            if (m4.isValid) {
                                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                        Res.string.activity_flash_programming_flash_erasing,
                                        Res.string.activity_flash_programming_ok)
                                    )
                                delay(200.milliseconds)

                                var progress = 0
                                _programmingState.tryEmit(FlashProgrammingState.Progress(progress))
                                var writeOk = true
                                // download #1: dane data[0xF8 .. 0x37A8] = 14000 B, bloki 0xF8 (ostatni 112 B)
                                var x = 0xF8
                                while (x < 0x37A8) {
                                    val n = minOf(blockSize, 0x37A8 - x)
                                    if (writeFlashData(data.copyOfRange(x, x + n))) {
                                        _programmingState.tryEmit(FlashProgrammingState.Progress(progress++ * 100 / steps))
                                    } else {
                                        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_write_error))
                                        writeOk = false
                                        break
                                    }
                                    x += n
                                }
                                if (writeOk) {
                                    m1 = kwp.write(moduleAddress, 0x37.toByte(), byteArrayOf())
                                    // download #2: 34 addr 13C000 size 00F8 (248 B) - naglowek na koniec
                                    m2 = kwp.write(
                                        moduleAddress,
                                        0x34.toByte(),
                                        byteArrayOf(
                                            0x13.toByte(),
                                            0xC0.toByte(),
                                            0x00,
                                            0x00,
                                            0x00,
                                            0xF8.toByte()
                                        )
                                    )
                                    if (m1.isValid && m2.isValid) {
                                        if (writeFlashData(data.copyOfRange(0x00, 0xF8))) {
                                            _programmingState.tryEmit(FlashProgrammingState.Progress(progress++ * 100 / steps))
                                        } else {
                                            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_write_error))
                                        }
                                    }
                                }
                                m1 = kwp.write(moduleAddress, 0x37.toByte(), byteArrayOf())
                                // 31 02 - sprawdzenie sumy, response pending (7F 31 78 -> 71 02)
                                m2 = kwp.writeWithPending(moduleAddress, 0x31.toByte(), byteArrayOf(0x02), 1000L, MEMS_FLASH_PENDING_MS)
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
                                // po 11 01 sterownik sie resetuje - odczekaj, zanim ponowny odczyt (inaczej 1A 9A nie odpowie)
                                try { delay(2000.milliseconds) } catch (ex: CancellationException) { throw ex } catch (_: Exception) {}
                                val m7 = kwp.write(
                                    moduleAddress,
                                    0x1A.toByte(),
                                    byteArrayOf(0x9A.toByte())
                                )
                                Logger.i { "$tag [writeFlash] : reset ${m6.isValid}, ident po resecie ${m7.isValid}" }
                                // sukces = faktyczny zapis: wszystkie bloki OK, 37 OK, 31 02 (suma) OK
                                if (writeOk && m1.isValid && m2.isValid)
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
                        Logger.e{"$tag [writaFlash]: $ex"}
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
            Logger.e{"$tag [readFlash]: $ex"}
        }
    }

    private suspend fun readFlashData(address: Long, size: Int): ByteArray {
        // timeout musi objac transmisje calego bloku (size bajtow @ 9600 ~ size*1.05 ms);
        // zbyt krotki ucina odpowiedz i size!=blockSize -> blad odczytu
        val message = kwp.write(
            moduleAddress, 0x23.toByte(),
            byteArrayOf(
                ((address and 0xFF0000L) shr 16).toByte(),
                ((address and 0x00FF00L) shr 8).toByte(),
                ((address and 0x0000FFL)).toByte(),
                ((size and 0xFF)).toByte()
            ),
            (size * 2 + 100).toLong()
        )
        var result = ByteArray(0)
        try {
            result = message.answer.copyOfRange(3, message.length - 1)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception) {
            Logger.e{"$tag [readFlash]: $ex, $message"}
            return result
        }
        return result
    }

    private suspend fun writeFlashData(data: ByteArray): Boolean {
        return kwp.write(
            moduleAddress,
            0x36.toByte(),
            data,
            (data.size * 2 + 100).toLong()
        ).isValid
    }

    override val flashProgrammingAvailable: Boolean = true

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.R40, CarType.RJO)
    }
}
