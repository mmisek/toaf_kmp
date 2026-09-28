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

private const val tag = "ModuleBCU - L322"
class ModuleBCU(protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_BCU_L322, listOf(CarType.L322)),
    IModuleCoding, IModuleEepromProgramming, IModuleAdvanceControl, IModuleServotronic /*, IModuleLiveData*/ {
    override fun getKoinName() = KoinNames.ModuleL322Bcu

    override fun getImageResource()  = Res.drawable.module_bcu

    override fun getModuleDescription() = Res.string.module_description_bcu

    override fun getAdvanceControlElements() = listOf(
        AdvanceControlElement(Res.string.advance_control_servotronic_title) { userInfo, onShowResult ->
            AdvanceControlServotronic(userInfo = userInfo, onShowResult = onShowResult)
        }
    )

    /*
     * Mapy Servotronic (GM3). Modul adresuje kodowanie blokami, kazdy blok konczy sie bajtem
     * CODING_OK (AAh) i suma kontrolna XOR. Uklad blokow 1 i 2 jest taki sam dla obu CI:
     * bajty 0-15 mapa, bajt 16 CODING_OK, bajt 17 suma kontrolna.
     *
     * CI=04 (NNV500222): blok 0 = 00-11, SERVO_SPEED_MAP 12-21, SERVO_CURRENT_MAP 24-33
     * CI=10 (NNV500227): blok 0 = 00-19, SERVO_SPEED_MAP 1A-29, SERVO_CURRENT_MAP 2C-3B
     *
     * Prad [mA] = raw * SERVOTRONIC_CURRENT_SCALING (3.533).
     */
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

    /** Polozenie map Servotronic w blokach kodowania dla danego CI */
    private data class ServotronicLayout(val speedBlock: Int, val currentBlock: Int, val offset: Int, val size: Int)

    private val servotronicLayouts = mapOf(
        // CI=04: blok 1 = 12-23 (SERVO_SPEED_MAP 12-21), blok 2 = 24-35 (SERVO_CURRENT_MAP 24-33)
        "04" to ServotronicLayout(speedBlock = 1, currentBlock = 2, offset = 0, size = 16),
        // CI=10: blok 1 = 1A-2B (SERVO_SPEED_MAP 1A-29), blok 2 = 2C-3D (SERVO_CURRENT_MAP 2C-3B)
        "10" to ServotronicLayout(speedBlock = 1, currentBlock = 2, offset = 0, size = 16)
    )

    private suspend fun readServotronicLayout(): ServotronicLayout? {
        val codingIndex = readCodingIndex()
        val layout = servotronicLayouts[codingIndex?.uppercase()]
        if (layout == null)
            Logger.w { "$tag: servotronic map not supported for CI=$codingIndex" }
        return layout
    }

    /** Blok musi zawierac cala mape oraz bajt sumy kontrolnej na koncu */
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
        // writeCoding przelicza sume kontrolna kazdego bloku przed zapisem, poprawiajac takze bledne CS
        return writeCoding(coding)
    }
    /*
    @Override
    public boolean Control(ControlElement controlElement)
    {
        byte[] data = new byte[]{0x0C,0x00,0x00,0x00,0x00,0x00,0x00};
        data[controlElement.byteInBlock + 2] = (byte)controlElement.value;
        OBDMessage message = InterfaceWrite(data);
        return  message.valid;
    }
     */
    /*
    @Override
    public Map<Integer,byte[]> ReadLiveData() {
        Map<Integer,byte[]> result = new HashMap<>();
        OBDMessage message = InterfaceWrite(new byte[] {0x0B,0x00});
        if (message.valid)
        {
            result.put(0x00,message.answer.copyOfRange(3,message.answer.length));
            return result;
        }
        return null;
    }

    @Override
    public Intent GetLiveDataActivity(Context context) {
        return new Intent(context, ActivityLiveData.class);
    }

    @Override
    public boolean StartLiveDataRead(List<LiveDataElement> liveDataElements) {
        return true;
    }
*/
    /**
     * Odczyt wszystkich blokow kodowania. Zwraca null, jezeli ktorykolwiek blok nie zostal odczytany
     * poprawnie - odczyt jest zawsze kompletny (bloki 0-2). Suma kontrolna nie jest tu sprawdzana,
     * zeby mozna bylo odczytac i poprawic kodowanie z blednym CS.
     */
    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        for (i in 0..0x2) {
            val message = interfaceWrite(byteArrayOf(0x08, i.toByte()))
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
                    )
                )
                if (!message.isValid) return false
            }
            reset()
        }
        return true
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        var message = interfaceWrite(byteArrayOf(0x04, 0x00))
        try {
            if (message.isValid && (message.answer[3].toInt() and 0xFF) == 0) {
                return ArrayList()
            } else if (message.isValid && (message.answer[3].toInt() and 0xFF) > 0) {
                for (j in 1..2) {
                    message = interfaceWrite(byteArrayOf(0x04, j.toByte()))
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

    /** Suma kontrolna bloku: XOR wszystkich bajtow poza ostatnim == ostatni bajt */
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

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? {
        val message = interfaceWrite(
            byteArrayOf(
                0x06,
                0x03,
                0x00,
                0x00,
                0x00,
                (0x0D + (address shr 8)).toByte(),
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

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val dataToWrite =
            byteArrayOf(
                0x07,
                0x03,
                0x00,
                0x00,
                0x00,
                (0x0D + (address shr 8)).toByte(),
                (address and 0xFF).toByte(),
                (data.size).toByte())  + data

        return interfaceWrite(dataToWrite).isValid
    }

    override fun getEepromSize(): Int = 0x300

    override fun getEepromReadSize(): Int = 0x10

    override fun getEepromWriteSize(): Int  = 0x10 / 2
}
