package pl.misek.toafmobile.modules.l322

import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_ac
import toafmobile.composeapp.generated.resources.module_description_hevac

class ModuleHEVAC(protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_HEVAC, listOf(CarType.L322)),
    IModuleLiveData /* ,IModuleCoding*/ {

    override fun getKoinName() = KoinNames.ModuleL322Hevac

    override fun getImageResource() = Res.drawable.module_ac

    override fun getModuleDescription() = Res.string.module_description_hevac

    override suspend fun getAllErrors(): List<ModuleError> {
        var result: MutableList<ModuleError>? = null
        var x: Byte = 1
        while (x <= 5) {
            val message = interfaceWrite(byteArrayOf(0x04, x))
            if (message.isValid && message.length > 5) {
                for (i in 0..1) {
                    val errorCode = String.format("%02X", message.answer[4 + 9 * i])
                    val errorDescription: MutableList<Pair<StringResource,Any?>> = ArrayList()
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[6 + 9 * i].toInt() and 0xFF) and 0x1F))
                    for (j in 0..7) {
                        if ((message.answer[5 + 9 * i].toInt() and (0x01 shr j)) == (0x01 shr j)) {
                            ResourceMapper
                                .stringResource(String.format("hevac_error_detail_%02X",(0x01 shr j)))
                                ?.let { errorDescription.add(Pair(it,null)) }
                        }
                    }
                    if (result == null) result = ArrayList()
                    result.add(ModuleError(ModuleAddress.MODULE_HEVAC, errorCode, errorDescription))
                }
            }
            x = (x + 2).toByte()
        }
        return result!!
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x07)
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (i in bytes.indices) {
            val message = interfaceWrite(byteArrayOf(0x0B, bytes[i]))
            if (message.isValid)
                result[i] = message.answer.copyOfRange(3, message.answer.size)
            else
                return null
        }
        return result
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean  = true
    /*
    @Override
    public List<byte[]> ReadCoding() {
        InterfaceWrite(new byte []{0x00});
        List<byte[]> result = new ArrayList<>();
        OBDMessage message = InterfaceWrite(new byte[] {0x08,0x00,0x00,0x00,0x00,0x00});
        if (message.valid)
            result.add(message.answer.copyOfRange(3,message.answer.length-1);
        else
            return null;
        return result;
    }

    @Override
    public boolean WriteCoding(List<byte[]> dataToSave) {
        InterfaceWrite(new byte []{0x00});
        byte[] data = new byte[]{0x09,0x00,0x00,0x00,0x00,0x00};
        byte[] dataToWrite = Tools.AddArrays(data,dataToSave.get(0));
        OBDMessage message = InterfaceWrite(dataToWrite);
        return message.valid;
    }
     */
}
