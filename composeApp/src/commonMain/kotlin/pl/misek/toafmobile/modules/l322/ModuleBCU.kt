package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.*

private const val tag = "ModuleBCU - L322"
class ModuleBCU(protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_BCU_L322, listOf(CarType.L322)),
    IModuleCoding, IModuleEepromProgramming /*, IModuleLiveData*/ {
    override fun getKoinName() = KoinNames.ModuleL322Bcu

    override fun getImageResource()  = Res.drawable.module_bcu

    override fun getModuleDescription() = Res.string.module_description_bcu
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
    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        for (i in 0..0x2) {
            val message = interfaceWrite(byteArrayOf(0x08, i.toByte()))
            try {
                if (message.isValid)
                    result.add(message.answer.copyOfRange(3,message.answer.size - 1))
                else
                    return null
            }
            catch (ex : Exception)
            {
                Logger.e {"$tag [readCoding] : $ex, $message"}
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
        } catch (ex :Exception)
        {
            Logger.e {"$tag [getAllErrors], $ex, $message"}
        }
        return result
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
