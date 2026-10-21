package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CancellationException
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
import pl.misek.toafmobile.activities.composeModels.activities.AdvanceControlEmsEwsSync
import pl.misek.toafmobile.modules.interfaces.AdvanceControlElement
import pl.misek.toafmobile.modules.interfaces.EwsSyncStep
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceControl
import pl.misek.toafmobile.modules.interfaces.IModuleAif
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEwsSync
import pl.misek.toafmobile.modules.interfaces.IModuleFlashProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.modules.interfaces.IModuleZCS
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import pl.misek.toafmobile.tools.JsonSupport
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private const val tag = "ModuleEMS"
private const val AIF_WRITE_TIMEOUT_MS = 2000L
private const val AIF_WRITE_SETTLE_MS = 1000L
private const val FLASH_PENDING_TIMEOUT_MS = 10_000L
/** Bit 6 statusu 31 03 - "ECU locked - seed_key procedure missing"; blokuje zapis AIF (7F 3B 22) */
private const val REPROG_STATUS_ECU_LOCKED = 0x0040
/** Flagi REPORT_REPROG_STATUS (31 03) z ems2k.b2v, bit -> opis */
private val REPROG_STATUS_FLAGS = listOf(
    "reference error boot software",
    "reference error ECU software",
    "reference error calibration data",
    "ECU software does not fit boot software",
    "calibration data does not fit ECU software",
    "system references fit together (OK)",
    "ECU locked - seed/key procedure missing",
    "ECU configuration OK",
    "ECU software OK",
    "ECU software security key OK",
    "ECU software security key not written",
    "checksum ECU software OK",
    "calibration data OK",
    "calibration data security key OK",
    "calibration data security key not written",
    "checksum calibration data OK"
)
class ModuleEMS(private var kwp: ProtocolKWP) : IModuleZCS, IModuleFlashProgramming, IModule,
    IModuleControl,
    IModuleLiveData, IModuleEwsSync, IModuleAif, IModuleAdvanceControl, IModuleVIN {
    private val moduleAddress = ModuleAddress.MODULE_EMS.address
    private val _programmingState =  MutableSharedFlow<FlashProgrammingState>(replay = 10, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val programmingState = _programmingState.asSharedFlow()
    private val liveDataBlocksToRead: MutableList<Byte> =
        ArrayList()

    override fun getKoinName() = KoinNames.ModuleR40Ems

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
        for ((_, _, _, block) in liveDataElements) liveDataBlocksToRead.add(block.toByte())
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
            }catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
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
                        try {
                        if (message1.isValid) {
                            // dlugosc odpowiedzi 17 bywa rozna (ten sterownik zwraca krotsza) - zabezpieczenie
                            if (message1.answer.size > 14)
                                errorDescription.add(ResourceHelper.getErrorDescriptionCount(message1.answer[14].toInt() and 0xFF))
                            if (message1.answer.size > 8) {
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
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Logger.e { "$tag [getAllErrors detail] $errorCode, $ex, $message1" }
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
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getAllErros], $ex ,$message"}
            }
        }
        return result
    }

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null

        /*
        if (kwp instanceof ProtocolKWPStarEMS2K)
        {
             if (StartDiagnosticSession(0x86))
                if (SecurityAccess(9))
                    //if (BootMode())
                    {
                        OBDMessage message = (OBDMessage)kwp.Write(MODULE_ADDRESS,(byte)0x31, new byte[]{(byte)0x80},1000);
                        Log.d("TOAF:", message.toString());
                        if (message.isValid())
                            new MyToast(context,"IMMO Learn OK", Toast.LENGTH_LONG);
                        else
                            new MyToast(context,"Immo Learn NOT OK",Toast.LENGTH_LONG);

                    }
        }
        */
        if (!startDiagnosticSession(0x81)) return null
        if (!securityAccess(3)) return null
        val message = kwp.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x80.toByte()), 300)
        try {
            if (message.isValid) {
                try {
                    info = ModuleInfo()
                    val answer = message.answer
                    info.additionalInfo = ArrayList()
                    info.id1 = String(answer.copyOfRange(147, 150)) + String.format(
                        "%02X%02X%02X",
                        answer[150].toInt() and 0xFF,
                        answer[151].toInt() and 0xFF,
                        answer[152].toInt() and 0xFF
                    )
                    info.id2 = String(answer.copyOfRange(27, 30)) + String.format(
                        "%02X%02X%02X",
                        answer[30].toInt() and 0xFF,
                        answer[31].toInt() and 0xFF,
                        answer[32].toInt() and 0xFF
                    )
                    info.ci = (answer[165].toInt() and 0xFF).toByte().toHexString()
                    info.di = (answer[166].toInt() and 0xFF).toByte().toHexString()
                    info.sw = (answer[0xAC].toInt() and 0xFF).toByte().toHexString()
                    info.date = String.format(
                        "%02X/%02X/%04X",
                        answer[167].toInt() and 0xFF,
                        answer[168].toInt() and 0xFF,
                        ((answer[169].toInt() and 0xFF) shl 8) + (answer[170].toInt() and 0xFF)
                    )
                    info.additionalInfo.add(
                        Pair(
                            Res.string.module_info_siemens_software_number,
                            String(answer.copyOfRange(153, 162))
                        )
                    )
                    EmsAif.parse(answer.copyOfRange(7, 7 + EmsAif.RECORD_SIZE))?.let {
                        info.additionalInfo.addAll(it.toAdditionalInfo())
                    }

                    info.serial = String(answer.copyOfRange(0x55, 0x5F)).trim()

                    info.producer = ModulesFunction.getProducer(answer[171].toInt())
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
            Logger.e {"$tag [getIdentification], $ex, $message"}
        }


        return info
    }

    override suspend fun getVIN(): String? {
        if (!startDiagnosticSession(0x81)) return null
        if (!securityAccess(3)) return null
        val message = kwp.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x80.toByte()), 300)
        try {
            return if (message.isValid) {
                String(message.answer.copyOfRange(7, 18)) +
                        String.format(
                            Locale.getDefault(),
                            "%02X%02X%02X",
                            message.answer[18].toInt() and 0xFF,
                            message.answer[19].toInt() and 0xFF,
                            message.answer[20].toInt() and 0xFF
                        )
            } else {
                null
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getVIN], $ex, $message"}
            return null
        }
    }

    override suspend fun getOdometer(): String? {
        return null
        /*
        OBDMessage message = (OBDMessage)kwp.Write(MODULE_ADDRESS,(byte)0x2C, new byte[] {0x10,0x0F,(byte)0xD0});
        if (message.isValid())
        {
            return String.format("%d",(message.answer[6] >> 8) + message.answer[7]*10);
        }
        else
            return "";
         */
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
                val seed = ((message.answer[6].toInt() and 0xFF) shl 8) + (message.answer[7].toInt() and 0xFF)
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
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [securityAccess], $ex, $message"}
        }


        return result
    }

    /**
     * 4-bajtowy SecurityAccess poziomu 0xFD/0xFE (seed-key EMS2000, jak Galletto).
     * 27 FD -> 67 FD [4 bajty seed]; klucz = Ems2kSeedKey; 27 FE [4 bajty] -> 67 FE 34.
     * To jest "seed_key procedure" zdejmujaca bit 6 statusu programowania - warunek zapisu AIF.
     */
    private suspend fun securityAccessFdFe(): Boolean {
        val message = kwp.write(moduleAddress, 0x27.toByte(), byteArrayOf(0xFD.toByte()), 500)
        try {
            if (!message.isValid || message.answer.size < 10) return false
            val seed = ((message.answer[6].toInt() and 0xFF) shl 24) or
                    ((message.answer[7].toInt() and 0xFF) shl 16) or
                    ((message.answer[8].toInt() and 0xFF) shl 8) or
                    (message.answer[9].toInt() and 0xFF)
            Logger.i { "$tag [securityAccessFdFe] : seed %08X".format(seed) }
            if (seed == 0) return true
            val key = Ems2kSeedKey.key(seed)
            return kwp.write(
                moduleAddress,
                0x27.toByte(),
                byteArrayOf(
                    0xFE.toByte(),
                    ((key ushr 24) and 0xFF).toByte(),
                    ((key ushr 16) and 0xFF).toByte(),
                    ((key ushr 8) and 0xFF).toByte(),
                    (key and 0xFF).toByte()
                ),
                500
            ).isValid
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [securityAccessFdFe], $ex, $message" }
            return false
        }
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
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (_: Exception) {
                            return ZcsCoding()
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
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

    override suspend fun syncEws(ews: ModuleEWS?, onStep: (EwsSyncStep) -> Unit): Boolean {
        if (ews == null) return false
        val ecuIdent = getIdentification()
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_ecu_ident, ecuIdent != null, ecuIdent?.id1))
        if (ecuIdent?.id1.isNullOrEmpty()) return false
        val ewsIdent = ews.getIdentification()
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_ews_ident, ewsIdent != null, ewsIdent?.id1))
        if (ewsIdent?.id1.isNullOrEmpty()) return false
        val session = startDiagnosticSession(0x85)
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_session, session))
        if (!session) return false
        val security = securityAccess(7)
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_security, security))
        if (!security) return false
        val resync = kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0xFF.toByte()), 1000).isValid
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_resync, resync))
        return resync
    }

    override suspend fun readAif(): ByteArray? {
        // AIF_LESEN: readEcuIdentification (0x1A), rekord 0x87 - 46 bajtow od bajtu 6 odpowiedzi
        val message = kwp.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x87.toByte()), 300)
        return try {
            if (message.isValid && message.length >= 6 + EmsAif.RECORD_SIZE + 1)
                message.answer.copyOfRange(6, 6 + EmsAif.RECORD_SIZE).also { record ->
                    Logger.i { "$tag [readAif] : ${record.joinToString(" ") { String.format("%02X", it) }}" }
                    Logger.i { "$tag [readAif] : ${EmsAif.parse(record)}" }
                }
            else null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readAif], $ex, $message" }
            null
        }
    }

    suspend fun readAifData(): EmsAif? = readAif()?.let { EmsAif.parse(it) }

    /** AIF_SCHREIBEN: 3B 87 + rekord, weryfikacja ponownym odczytem 1A 87 (jak w SGBD) */
    /**
     * AIF_SCHREIBEN: 3B 87 + rekord w sesji programowania 85 (sesje 86 i 83 odpowiadaja na 3B 7F 80 - usluga niedostepna).
     * Po poziomie 7 sterownik odrzuca zapis 7F 22, status programowania (dostepny tylko po poziomie 7) ma bit 6 ECU locked.
     * Zapis probowany po kolejnych poziomach security access, na koniec powrot do sesji 81 / poziom 3 (jak identyfikacja)
     * i weryfikacja odczytem 1A 87 (jak w SGBD).
     * TODO: zostawic poziom, z ktorym zapis przechodzi.
     */
    /**
     * AIF_SCHREIBEN: 3B 87 + rekord w sesji programowania 85.
     * Zapis blokuje bit 6 statusu (31 03) "seed_key procedure missing"; zdejmuje go 4-bajtowy
     * SecurityAccess 0xFD/0xFE (seed-key EMS2000, jak Galletto). Po odblokowaniu wysylamy 3B 87,
     * na koniec powrot do sesji 81 / poziom 3 (jak identyfikacja) i weryfikacja odczytem 1A 87.
     */
    override suspend fun writeAif(data: ByteArray): Boolean {
        if (data.size != EmsAif.RECORD_SIZE) return false
        Logger.i { "$tag [writeAif] : ${data.joinToString(" ") { String.format("%02X", it) }}" }
        Logger.i { "$tag [writeAif] : ${EmsAif.parse(data)}" }
        var accepted = false
        if (startDiagnosticSession(0x85) && securityAccessFdFe()) {
            Logger.i { "$tag [writeAif] : reprog status after FD/FE unlock" }
            readReprogStatus()
            accepted = writeAifRecord(data)
        } else {
            Logger.e { "$tag [writeAif] : session 85 / security access FD-FE rejected" }
        }
        // powrot do stanu jak po identyfikacji - w sesji 85 sterownik odrzuca 1A 80 (7F 33)
        startDiagnosticSession(0x81)
        securityAccess(3)
        if (!accepted) {
            Logger.e { "$tag [writeAif] : ********** AIF WRITE FAILED - rejected **********" }
            return false
        }
        delay(AIF_WRITE_SETTLE_MS.milliseconds)
        if (readAif()?.contentEquals(data) == true) {
            Logger.i { "$tag [writeAif] : ********** AIF WRITE OK - session 85, security level 7 **********" }
            return true
        }
        Logger.e { "$tag [writeAif] : ********** AIF WRITE FAILED - verify failed **********" }
        return false
    }

    /** Wysyla 3B 87 + rekord (z obsluga 7F 3B 78 response pending); true, gdy sterownik przyjal zapis */
    private suspend fun writeAifRecord(data: ByteArray): Boolean {
        val message = kwp.writeWithPending(moduleAddress, 0x3B.toByte(), byteArrayOf(0x87.toByte()) + data, AIF_WRITE_TIMEOUT_MS, FLASH_PENDING_TIMEOUT_MS)
        val answer = message.answer
        if (answer.size > 6 && answer[4] == 0x7F.toByte() && answer[6] != 0x78.toByte()) {
            Logger.e { "$tag [writeAif] : write rejected, NRC %02X".format(answer[6].toInt() and 0xFF) }
            readReprogStatus()
            return false
        }
        Logger.i { "$tag [writeAif] : ********** AIF WRITE ACCEPTED - session 85, security level 7 **********" }
        return true
    }

    /** REPORT_REPROG_STATUS (31 03): status = bajty 6-7 odpowiedzi, kazda ustawiona flaga w osobnej linii */
    private suspend fun readReprogStatus(): Int? {
        val message = kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0x03), 500)
        val answer = message.answer
        if (!message.isValid || answer.size < 9) {
            Logger.e { "$tag [reprogStatus] : no answer, ${answer.joinToString(" ") { String.format("%02X", it) }}" }
            return null
        }
        val status = ((answer[6].toInt() and 0xFF) shl 8) or (answer[7].toInt() and 0xFF)
        Logger.i { "$tag [reprogStatus] : %04X".format(status) }
        for ((bit, description) in REPROG_STATUS_FLAGS.withIndex()) {
            if ((status shr bit) and 1 == 1) Logger.i { "$tag [reprogStatus] : bit $bit - $description" }
        }
        return status
    }

    override fun getVinLength(): Int = 17

    /**
     * Zapis VIN przez PELNY cykl programowania. Samodzielny zapis AIF (3B 87) jest blokowany
     * (7F 3B 22), bo bit 6 statusu "seed_key procedure missing" gasnie dopiero po 31 02 w cyklu
     * programowania. Dlatego: odczyt obecnego oprogramowania -> przeprogramowanie tym samym obrazem
     * -> 31 02 (gasi bit 6) -> zapis AIF z nowym VIN i dzisiejsza data -> reset.
     * UWAGA: to realne przeprogramowanie flash - przerwanie moze uszkodzic sterownik.
     */
    override suspend fun writeVIN(newVin: String): Boolean {
        val record = readAif() ?: return false
        val newRecord = EmsAif.withVin(record, newVin.uppercase(), Calendar.getInstance()) ?: return false
        Logger.i { "$tag [writeVIN] : ${EmsAif.parse(newRecord)}" }
        // odczyt obecnego oprogramowania - MUSI sie udac przed kasowaniem (inaczej nie wgramy nic z powrotem)
        val image = readFlashImage()
        if (image == null) {
            Logger.e { "$tag [writeVIN] : odczyt flash nieudany - przerwano przed kasowaniem" }
            return false
        }
        return programFlashAndWriteAif(image, newRecord)
    }

    /** Odczyt pelnego obrazu flash EMS (0x108000..0x10FFFF) - do wgrania z powrotem. null przy bledzie. */
    private suspend fun readFlashImage(): ByteArray? {
        val start = 0x108000L
        val size = 0x8000
        val blockSize = 0x80
        if (!startDiagnosticSession(0x86) || !securityAccess(0x09)) return null
        var result = ByteArray(0)
        var i = start
        while (i < start + size) {
            val block = readFlashData(i)
            if (block.size != blockSize) return null
            result += block
            i += blockSize
        }
        return result
    }

    private suspend fun programFlashAndWriteAif(image: ByteArray, aif: ByteArray): Boolean {
        if (image.size != 0x8000 || aif.size != EmsAif.RECORD_SIZE) return false
        val blockSize = 0x5C
        endDiagnosticSession()
        try { delay(1000.milliseconds) } catch (ex: CancellationException) { throw ex } catch (_: Exception) {}
        if (!startDiagnosticSession(0x85) || !securityAccessFdFe()) {
            Logger.e { "$tag [writeVIN] : sesja 85 / FD-FE nieudane" }
            return false
        }
        val erase = kwp.writeWithPending(moduleAddress, 0x31.toByte(), byteArrayOf(0x01), 500L, FLASH_PENDING_TIMEOUT_MS)
        if (!erase.isValid) { Logger.e { "$tag [writeVIN] : kasowanie nieudane" }; return false }
        if (!kwp.write(moduleAddress, 0x34.toByte(),
                byteArrayOf(0x10, 0x80.toByte(), 0x00, 0x00, 0x00, 0x7F, 0xF0.toByte())).isValid) {
            Logger.e { "$tag [writeVIN] : request download nieudany" }; return false
        }
        var x = 0
        while (x < image.size) {
            val block = image.copyOfRange(x, if (x + blockSize < image.size) x + blockSize else image.size)
            if (block.size == blockSize && !writeFlashData(block)) {
                Logger.e { "$tag [writeVIN] : blad zapisu bloku flash @ $x" }; return false
            }
            x += blockSize
        }
        kwp.write(moduleAddress, 0x37.toByte(), byteArrayOf(), 2000)
        // 31 02 - sprawdzenie sumy, gasi bit 6 "seed_key procedure missing"
        kwp.writeWithPending(moduleAddress, 0x31.toByte(), byteArrayOf(0x02), 500L, FLASH_PENDING_TIMEOUT_MS)
        Logger.i { "$tag [writeVIN] : reprog status po 31 02" }
        val status = readReprogStatus()
        if (status != null && (status and REPROG_STATUS_ECU_LOCKED) != 0)
            Logger.e { "$tag [writeVIN] : bit 6 nadal ustawiony - zapis AIF moze byc odrzucony" }
        // zapis AIF (teraz bit 6 powinien byc zgaszony)
        val aifOk = writeAifRecord(aif)
        kwp.write(moduleAddress, 0x11.toByte(), byteArrayOf(0x01), 2000)   // reset
        if (!aifOk) { Logger.e { "$tag [writeVIN] : ********** zapis AIF nieudany **********" }; return false }
        // weryfikacja po resecie
        try { delay(AIF_WRITE_SETTLE_MS.milliseconds) } catch (ex: CancellationException) { throw ex } catch (_: Exception) {}
        val ok = readAif()?.contentEquals(aif) ?: false
        Logger.i { "$tag [writeVIN] : ${if (ok) "********** VIN WRITE OK **********" else "VERIFY FAILED"}" }
        return ok
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
                delay(1000.milliseconds)
            } catch (ex: CancellationException) {
                throw ex
            } catch (_ : Exception) {
            }
            // sesja programowania 85 + 4-bajtowy SecurityAccess FD/FE (jak Galletto; poziom 7 tez dziala)
            if (startDiagnosticSession(0x85) && securityAccessFdFe()) {
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_security_access,
                        Res.string.activity_flash_programming_ok))
                // kasowanie: 7F 31 78 (response pending), 71 01 po ok. 0.5 s
                val eraseFlash = kwp.writeWithPending(moduleAddress, 0x31.toByte(), byteArrayOf(0x01), 500L, FLASH_PENDING_TIMEOUT_MS)
                if (eraseFlash.isValid)
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
                            // sprawdzenie sumy: 7F 31 78, 71 02 po ok. 0.8 s
                            val m2 = kwp.writeWithPending(moduleAddress, 0x31.toByte(), byteArrayOf(0x02), 500L, FLASH_PENDING_TIMEOUT_MS)
                            val m3 = kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0x03), 2000)
                            Logger.i { "$tag [writeFlash] : reprog status ${m3.answer.joinToString(" ") { String.format("%02X", it) }}" }
                            val m4 = kwp.write(
                                moduleAddress,
                                0x11.toByte(),
                                byteArrayOf(0x01.toByte()),
                                2000
                            )
                            if (m1.isValid && m2.isValid && m3.isValid && m4.isValid) _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
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
                                    String.format("%02d:%02d:%02d", hours, minutes, seconds)))
                            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_ecu_reset))
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Logger.e{"$tag [WriteFlash]: $ex"}
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
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e{"$tag [readFlash] : $ex"}
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
            275
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

    /** 36 + blok; sterownik odpowiada 7F 36 78 (response pending), potem 76 po ok. 0.2 s - nastepny blok dopiero po 76 */
    private suspend fun writeFlashData(data: ByteArray): Boolean {
        val message = kwp.writeWithPending(moduleAddress, 0x36.toByte(), data, 300L, FLASH_PENDING_TIMEOUT_MS)
        if (!message.isValid) Logger.e { "$tag [writeFlashData] : ${message.answer.joinToString(" ") { String.format("%02X", it) }}" }
        return message.isValid
    }

    override val flashProgrammingAvailable: Boolean = true

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.R40, CarType.RJO)
    }

    override fun getAdvanceControlElements() = listOf(
        AdvanceControlElement(Res.string.advance_control_ews_sync_title, isNew = true) { userInfo, onShowResult ->
            AdvanceControlEmsEwsSync(engineModule = this, userInfo = userInfo, onShowResult = onShowResult)
        }
    )
}
