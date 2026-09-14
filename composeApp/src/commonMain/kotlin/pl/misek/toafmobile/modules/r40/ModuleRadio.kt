package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_radio
import toafmobile.composeapp.generated.resources.module_radio
import toafmobile.composeapp.generated.resources.radio_module_info_name
import kotlinx.coroutines.CancellationException

private const val tag = "ModuleRadio"
open class ModuleRadio : ModuleDS2, IModuleControl {
    private val radioType: Map<Int, String> = mapOf(
        0x00 to "C23 MID ECE",
        0x01 to "C23 BM ECE",
        0x02 to "C23 MID US",
        0x03 to "C23 BM JPN",
        0x04 to "C33 JPN",
        0x05 to "C23 BM OCN",
        0x07 to "CD23",
        0x08 to "C24 DIN",
        0x09 to "C24 MID",
        0x0A to "C34",
        0x0B to "C32",
        0x0C to "CD33",
        0x0D to "C33 ECE",
        0x0E to "C33 US",
        0x0F to "C33 OCN",
        0x10 to "C43 ECE",
        0x11 to "CD43",
        0x12 to "C43 USA",
        0x13 to "C43 JPN",
        0x14 to "C43 OCN",
        0x15 to "C44",
        0x16 to "C24 BM",
        0x17 to "C42",
        0x18 to "C42R-RD1 Euro",
        0x19 to "RC42-Tempest US",
        0x1A to "RD42-Tempest Ja",
        0x1B to "RC43-RD1 Jap",
        0x1C to "RC43-RD1 Euro",
        0x1D to "RC43-Tempest US",
        0x1E to "RC43-Tempest Ja",
        0x1F to "RC43-Tempest Eu",
        0x20 to "RC43-38a Euro",
        0x21 to "RC43-38a US",
        0x22 to "RC43-38a Jap",
        0x23 to "C43 US E38",
        0x24 to "C43 US E39",
        0x25 to "C43 US BM",
        0x26 to "CD43 E39",
        0x27 to "C42 R50",
        0x28 to "C53 MID L30",
        0x29 to "CD53 L30",
        0x2A to "MD53 L30",
        0x2B to "CD54 L30",
        0x2C to "CD54 E39",
        0x2D to "CD54 E46",
        0x2E to "C53 R50",
        0x2F to "CD53 R50",
        0x30 to "MD53 R50",
        0x31 to "C53 MID E39",
        0x32 to "BM53",
        0x33 to "C53 E46",
        0x34 to "MD53 E46",
        0x35 to "CD53 E46",
        0x36 to "CD53 E39",
        0x37 to "MD53 E39",
        0x39 to "BM54",
        0x3A to "C53 MIR E46",
        0x3B to "MIR E52",
        0x3C to "C33B E39 CKD",
        0x3D to "C33B E46 CKD",
        0x3E to "BM24 MMC",
        0x3F to "C52 E39",
        0x40 to "C52 E53",
        0x41 to "C53 E39",
        0x42 to "C53 E53",
        0x43 to "CD62 E85",
        0x44 to "MD53 CID",
        0x45 to "CD53 E46 VDO",
        0x46 to "CD53 E85",
        0x47 to "MIR E85",
        0x48 to "CD53 CID E85",
        0x49 to "MD53 CID E85",
        0x4A to "CD53 R50 VDO")
    constructor(protocolDS2: ProtocolDS2?) : super(
        protocolDS2,
        ModuleAddress.MODULE_RADIO,
        listOf(CarType.R40)
    )

    protected constructor(protocolDS2: ProtocolDS2?, carTypeList: List<CarType>) : super(
        protocolDS2,
        ModuleAddress.MODULE_RADIO,
        carTypeList
    )
    override fun getKoinName() = KoinNames.ModuleR40Radio

    override fun getImageResource() = Res.drawable.module_radio

    override fun getModuleDescription() = Res.string.module_description_radio

    override suspend fun getIdentification(): ModuleInfo? {
        val moduleInfo = super.getIdentification()
        val radioCode = moduleInfo!!.di.toInt(16)
        if (radioType.containsKey(radioCode)) {
            moduleInfo.additionalInfo.add(
                Pair(
                    Res.string.radio_module_info_name,
                    radioType[radioCode] ?: ""
                )
            )
        }
        val message = interfaceWrite(byteArrayOf(0x53))
        try {
            if (message.isValid) {
                val answer = message.answer
                moduleInfo.serial = String.format(
                    "%02X%02X%02X%02X%02X",
                    (answer[3].toInt() and 0xFF),
                    (answer[4].toInt() and 0xFF),
                    (answer[5].toInt() and 0xFF),
                    (answer[6].toInt() and 0xFF),
                    (answer[7].toInt() and 0xFF)
                )
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getIdentification], $ex, $message"}
        }
        return moduleInfo
    }

    /*
    @Override
    public Map<Integer,byte[]> ReadLiveData() {
        Map<Integer,byte[]> result = new HashMap<>();
        OBDMessage message = InterfaceWrite(new byte[]{0x1B, (byte)0x90});
        if (message.valid) {
            result.put(0x00, message.answer.copyOfRange(3, message.answer.length));
        }
        return  result.size() > 0 ? result : null;
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
    override suspend fun getAllErrors(): List<ModuleError>? {
        val result: MutableList<ModuleError> = ArrayList()
        val message = interfaceWrite(byteArrayOf(0x04, 0x01))
        try {
            if (message.isValid) {
                for (i in 0 until message.answer[3]) {
                    val errorCode = String.format("%02X", message.answer[4 + 2 * i])
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[5 + 2 * i].toInt() and 0x1F)))
                    errorDescription.add(ResourceHelper.getErrorDescriptionIsPresent((message.answer[5 + 2 * i].toInt() and 0x20) == 0x20))
                    result.add(ModuleError(ModuleAddress.MODULE_RADIO, errorCode, errorDescription))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$ex [getAllErrors], $ex, $message"}
        }
        return result
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        super.getIdentification()
        when (controlElement.block) {
            0x0C -> if (controlElement.byteInBlock == 0x0A) {
                val frequency = controlElement.controlSetValue!!.value
                return interfaceWrite(
                    byteArrayOf(
                        0x0C,
                        0x0A,
                        ((frequency shr 16) and 0xFF).toByte(),
                        ((frequency shr 8) and 0xFF).toByte(),
                        (frequency and 0xFF).toByte()
                    )
                ).isValid
            } else {
                return  interfaceWrite(
                    byteArrayOf(
                        0x0C,
                        (controlElement.byteInBlock and 0xFF).toByte()
                    )
                ).isValid
            }

            else -> return false
        }
    }
}
