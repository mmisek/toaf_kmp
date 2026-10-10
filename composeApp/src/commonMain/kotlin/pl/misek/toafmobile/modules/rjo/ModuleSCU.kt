package pl.misek.toafmobile.modules.rjo

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEKA
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleScuKeys
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWPStar
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.dialog_wifi_interface_hardware
import toafmobile.composeapp.generated.resources.dialog_wifi_interface_software
import toafmobile.composeapp.generated.resources.module_bcu
import toafmobile.composeapp.generated.resources.module_description_scu
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

private const val tag = "ModuleSCU"
private const val CODING_BLOCKS = 2 // 0x51, 0x52
class ModuleSCU(private var kwp: ProtocolKWPStar) : IModule, IModuleControl, IModuleVIN,
    IModuleCoding, IModuleLiveData, IModuleEKA, IModuleScuKeys {

    private val MODULE_ADDRESS = ModuleAddress.MODULE_SCU.address

    override fun getModuleProtocol() = kwp

    override fun getKoinName() = KoinNames.ModuleRJOScu

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol as ProtocolKWPStar
    }

    private suspend fun moduleInit(): Boolean {
        return kwp.write(MODULE_ADDRESS, 0x81.toByte(), byteArrayOf(), 50).isValid
    }

    private suspend fun closeConnection(): Boolean {
        return kwp.write(MODULE_ADDRESS, 0x82.toByte(), byteArrayOf()).isValid
    }

    override fun getModuleName(): String {
        return "SCU"
    }

    override fun getModuleAddress(): ModuleAddress {
        return ModuleAddress.MODULE_SCU
    }

    override fun getImageResource() = Res.drawable.module_bcu

    override fun getModuleDescription() = Res.string.module_description_scu

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        if (moduleInit()) {
                val message =
                    kwp.write(MODULE_ADDRESS, 0x1A.toByte(), byteArrayOf(0x80.toByte()), 50)
                if (message.isValid) {
                    try {
                        info = ModuleInfo()
                        val answer = message.answer
                        info.id1 = String(answer.copyOfRange(5, 8)) + String.format(
                            "%02X%02X%02X",
                            answer[8].toInt() and 0xFF,
                            answer[9].toInt() and 0xFF,
                            answer[10].toInt() and 0xFF
                        )
                        info.bi = String.format("%02X", answer[11].toInt() and 0xFF)
                        info.ci = String.format("%02X", answer[12].toInt() and 0xFF)
                        info.di = String.format("%02X", answer[13].toInt() and 0xFF)
                        info.date = String.format(
                            "%02X/%02X",
                            answer[15].toInt() and 0xFF,
                            answer[16].toInt() and 0xFF
                        )
                        info.producer = ModulesFunction.getProducer(answer[17].toInt() and 0xFF)
                        info.sw = String.format("%02X", answer[18].toInt() and 0xFF)
                        info.serial = getSerialNumber()
                        info.additionalInfo.add(
                            Pair(
                                Res.string.dialog_wifi_interface_hardware,
                                getSupplierHardwareReferenceNumber() ?: ""
                            )
                        )
                        info.additionalInfo.add(
                            Pair(
                                Res.string.dialog_wifi_interface_software,
                                getSupplierSoftwareReferenceNumber() ?: ""
                            )
                        )
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Logger.e { "$tag [getIdentification]: $ex, $message" }
                    }
                }
        }
        return info
    }

    private suspend fun getSupplierHardwareReferenceNumber(): String? {
        if (moduleInit()) {
            val message = kwp.write(MODULE_ADDRESS, 0x1A.toByte(), byteArrayOf(0x92.toByte()), 50)
            if (message.isValid)
                try {
                    return String(message.answer.copyOfRange(5,message.answer.size-1))
                }
                catch (ex : CancellationException)
                {
                    throw ex
                }
                catch (ex : Exception)
                {
                    Logger.e { "$tag : $ex, $message" }
                }

        }
        return null
    }

    private suspend fun getSupplierSoftwareReferenceNumber(): String? {
        if (moduleInit()) {
            val message = kwp.write(MODULE_ADDRESS, 0x1A.toByte(), byteArrayOf(0x95.toByte()), 50)
            if (message.isValid)
                try {
                    return String(message.answer.copyOfRange(5,message.answer.size - 1))
                }
                catch (ex: CancellationException)
                {
                    throw ex
                }
                catch (ex: Exception)
                {
                    Logger.e { "$tag [getSupplierSoftwareReferenceNumber]: $ex, $message" }
                }
        }
        return null
    }

    private suspend fun getSerialNumber(): String {
        if (moduleInit()) {
            val message = kwp.write(MODULE_ADDRESS, 0x1A.toByte(), byteArrayOf(0x8A.toByte()), 50)
            if (message.isValid) {
                try {
                    return String(message.answer.copyOfRange(5, 7)) + String.format(
                        "%02X%02X%02X",
                        message.answer[7].toInt() and 0xFF,
                        message.answer[8].toInt() and 0xFF,
                        message.answer[9].toInt() and 0xFF
                    )
                }
                catch (ex: CancellationException)
                {
                    throw ex
                }
                catch (ex: Exception)
                {
                    Logger.e { "$tag [getSerialNumber]: $ex, $message" }
                }
            }
        }
        return ""
    }

    override suspend fun getVIN(): String? {
        if (moduleInit()) {
            // Wg SGBD C_FG_LESEN: 21 90, VIN (17 znakow) od [5]
            val message = kwp.write(MODULE_ADDRESS, 0x21.toByte(), byteArrayOf(0x90.toByte()), 50)
            if (message.isValid) {
                try {
                    return String(message.answer.copyOfRange(5, 22))
                } catch (ex: CancellationException) {
                    throw ex
                } catch ( ex: Exception)
                {
                    Logger.e { "$tag [getVIN]: $ex, $message" }
                }
            }
        }
        return null
    }

    override fun getVinLength(): Int = 17

    override suspend fun writeVIN(newVin: String): Boolean {
        // Wg SGBD C_FG_AUFTRAG: 17 znakow 0-9 / A-Z, po zapisie weryfikacja odczytem 21 90
        if (newVin.length != getVinLength() || !newVin.all { it in '0'..'9' || it in 'A'..'Z' }) return false
        var result = false
        if (moduleInit() && startDiagnosticSession(0x86.toByte()) && securityAccess(0x05.toByte())) {
            val data = byteArrayOf(0x90.toByte()) + newVin.toByteArray()
            if (kwp.write(MODULE_ADDRESS, 0x3B.toByte(), data, 50).isValid) {
                val verify = kwp.write(MODULE_ADDRESS, 0x21.toByte(), byteArrayOf(0x90.toByte()), 50)
                result = verify.isValid && verify.answer.size >= 22 &&
                        String(verify.answer.copyOfRange(5, 22)) == newVin
            }
            stopDiagnosticSession()
        }
        return result
    }

    override suspend fun getOdometer(): String? {
        return null
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        val result: MutableList<ModuleError>
        if (moduleInit() && startDiagnosticSession(0x86.toByte()) && securityAccess(0x05.toByte())) {
            val message = kwp.write(MODULE_ADDRESS, 0x21.toByte(), byteArrayOf(0x62.toByte()), 50)
            if (message.isValid) {
                try {
                    // 21 62: kazdy bajt danych to kod przyczyny wyzwolenia alarmu (01..0A, scu_error_XX)
                    result = ArrayList()
                    for (i in 5 until message.length - 1) {
                        val code = message.answer[i].toInt() and 0xFF
                        if (code in 0x01..0x0A)
                            result.add(
                                ModuleError(
                                    ModuleAddress.MODULE_SCU,
                                    String.format("%02X", code),
                                    ArrayList()
                                )
                            )
                    }
                    return result
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Logger.e { "$tag [getAllErrors]: $ex, $message" }
                }
            }
        }
        return null
    }

    override suspend fun clearAllErrors(): Boolean {
        //addKeyFob("N64AEAF42139873A2B3FEM")
        /*
        for (int i = 0 ;i < 5 ;i++)
        {
            if (ModuleInit() && StartDiagnosticSession((byte)0x83) && SecurityAccess((byte)0x05)) {
                OBDMessage message = kwp.Write(MODULE_ADDRESS,(byte)0x14,new byte[]{(byte)0x00,0x00},1500);
                if (message.isValid())
                    return true;
            }
        }*/
        return false
    }

    override suspend fun reset(): Boolean {
        return kwp.write(MODULE_ADDRESS, 0x11.toByte(), byteArrayOf(0x01), 50).isValid
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        if (moduleInit() && startDiagnosticSession(0x86.toByte()) && securityAccess(0x05.toByte())) {
            val data = byteArrayOf(
                controlElement.byteInBlock.toByte(),
                0x07,
                controlElement.value.toByte()
            )
            return kwp.write(MODULE_ADDRESS, 0x30.toByte(), data).isValid
        }
        return false
    }

    private suspend fun testerPresent(): Boolean {
        return kwp.write(MODULE_ADDRESS, 0x3E.toByte(), byteArrayOf(0x00, 0x00)).isValid
    }

    private suspend fun securityAccess(mode: Byte): Boolean {
        val message = kwp.write(MODULE_ADDRESS, 0x27.toByte(), byteArrayOf(mode), 50)
        if (message.isValid) {
            try {
                val code =
                    ((message.answer[5].toInt() and 0xFF) shl 8) + (message.answer[6].toInt() and 0xFF)
                // Seed 0000 - modul juz odblokowany (SGBD nie wysyla klucza)
                if (code == 0x0000) return true
                val seed = securityAccessCalculator(code)
                val data = byteArrayOf(
                    (mode + 1).toByte(),
                    ((seed and 0xFF00) shr 8).toByte(),
                    (seed and 0xFF).toByte()
                )
                return kwp.write(MODULE_ADDRESS, 0x27.toByte(), data, 50).isValid
            } catch (ex: CancellationException) {
                throw ex
            } catch ( ex: Exception)
            {
                Logger.e { "$tag [securityAccess]: $ex, $message" }
            }
        }
        return false
    }


    private suspend fun startDiagnosticSession(mode: Byte): Boolean {
        return kwp.write(MODULE_ADDRESS, 0x10.toByte(), byteArrayOf(mode), 50).isValid
    }

    private suspend fun stopDiagnosticSession(): Boolean {
        return kwp.write(MODULE_ADDRESS, 0x20.toByte(), byteArrayOf(), 50).isValid
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.RJO)
    }

    private fun securityAccessCalculator(inKey: Int): Int {
        var key = inKey
        var n = (((key and 0xFF) xor 0x73) and 0x2E) + 0x0A
        while (n > 0) {
            var x = 0
            if ((key and 0x8000) == 0x8000) x = x xor 1
            if ((key and 0x2000) == 0x2000) x = x xor 1
            if ((key and 0x0020) == 0x0020) x = x xor 1
            if ((key and 0x0008) == 0x0008) x = x xor 1
            if ((key and 0x0002) == 0x0002) x = x xor 1
            key = (((key shl 1) and 0xFFFE) or x)
            n--
        }
        return key
    }

    override suspend fun readEKACode(): String? {
        if (moduleInit() && startDiagnosticSession(0x86.toByte()) && securityAccess(0x05.toByte())) {
            val message = kwp.write(MODULE_ADDRESS, 0x21.toByte(), byteArrayOf(0x60), 50)
            stopDiagnosticSession()
            if (message.isValid) {
                try {
                    return String.format(
                        "%02X%02X",
                        message.answer[5].toInt() and 0xFF,
                        message.answer[6].toInt() and 0xFF
                    )
                } catch (ex: CancellationException) {
                    throw ex
                } catch ( ex: Exception)
                {
                    Logger.e { "$tag [readEKACode]: $ex, $message" }
                }
            }
        }
        return null
    }

    override suspend fun writeEKACode(eka: Int): Boolean {
        var ekaCode = eka
        var result = false
        if (moduleInit() && startDiagnosticSession(0x86.toByte()) && securityAccess(0x05.toByte())) {
            val eka2 = ((ekaCode % 10) + (((ekaCode / 10) % 10) shl 4)).toByte()
            ekaCode /= 100
            val eka1 = ((ekaCode % 10) + (((ekaCode / 10) % 10) shl 4)).toByte()
            result = kwp.write(
                MODULE_ADDRESS,
                0x3B.toByte(),
                byteArrayOf(0x60, eka1, eka2),
                50
            ).isValid
            stopDiagnosticSession()
        }
        return result
    }

    override suspend fun readCoding(): List<ByteArray>? {
        var result: MutableList<ByteArray>? = null
        if (moduleInit() && startDiagnosticSession(0x86.toByte()) && securityAccess(0x05.toByte())) {
            for (i in 0 until CODING_BLOCKS) {
                val message =
                    kwp.write(MODULE_ADDRESS, 0x21.toByte(), byteArrayOf((i + 0x51).toByte()), 50)
                if (!message.isValid) {
                    Logger.e { "$tag [readCoding]: block ${(i + 0x51).toString(16)} read failed, $message" }
                    return null
                }
                try {
                    if (result == null)
                        result = mutableListOf()
                    result.add(message.answer.copyOfRange(5, message.answer.size - 1))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex : Exception)
                {
                    Logger.e { "$tag [readCoding]: $ex, $message" }
                    return null
                }
            }
        }
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        if (!(moduleInit() && startDiagnosticSession(0x86.toByte()) && securityAccess(0x05.toByte())))
            return false
        var result = true
        for (i in dataToSave.indices) {
            result = result and kwp.write(
                MODULE_ADDRESS,
                0x3B.toByte(),
                byteArrayOf((i + 0x51).toByte()) + dataToSave[i],
                50
            ).isValid
        }
        if (result)
            writeCodingDate()
        return result
    }

    // Wg SGBD C_CODINGDATE_WRITE: 3B 99 DD MM CC YY (BCD)
    private suspend fun writeCodingDate(): Boolean {
        val currentDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val data = byteArrayOf(
            0x99.toByte(),
            currentDate.day.toBcd(), (currentDate.month.ordinal + 1).toBcd(),
            (currentDate.year / 100).toBcd(), (currentDate.year % 100).toBcd()
        )
        return kwp.write(MODULE_ADDRESS, 0x3B.toByte(), data, 50).isValid
    }

    private fun Int.toBcd(): Byte = (((this / 10) shl 4) or (this % 10)).toByte()

    override suspend fun addKey(barCode: String): Boolean {
        var result = false
        val keyFob = barCode.drop(1).dropLast(1)
        val data = keyFob.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
        if (moduleInit() && startDiagnosticSession(0x86.toByte()) && securityAccess(0x05.toByte()))
        {
            //if (kwp.write(MODULE_ADDRESS, 0x3B.toByte(), byteArrayOf(0x7F), 50).isValid)
            //{
                try {
                    delay(1000)
                    result = kwp.write(
                        MODULE_ADDRESS,
                        0x3B.toByte(),
                        byteArrayOf((0x70).toByte()) + data,
                        50
                    ).isValid
                } catch (ex: CancellationException) {
                    throw ex
                } catch (_: Exception) {
                }
            //}
            kwp.write(MODULE_ADDRESS, 0x20.toByte(), byteArrayOf(), 50)
        }
        return result
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val message = kwp.write(MODULE_ADDRESS, 0x21.toByte(), byteArrayOf(0x50), 50)
        if (message.isValid) {
            try {
                result[0x50] = message.answer.copyOfRange(5, message.answer.size - 1)
                return result
            } catch (ex: CancellationException) {
                throw ex
            } catch ( ex : Exception)
            {
                Logger.e { "$tag [readLiveData]: $ex, $message" }
            }
        }

        return null
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        return (moduleInit() && startDiagnosticSession(0x86.toByte()) && securityAccess(0x05.toByte()))
    }
}
