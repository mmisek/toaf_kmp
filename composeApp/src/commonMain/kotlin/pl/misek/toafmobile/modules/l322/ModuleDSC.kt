package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

class ModuleDSC(protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_DSC, listOf(CarType.L322)),
    /*IModuleAdvanceControl,*/ IModuleCoding, IModuleLiveData {
    private val tag ="ModuleDSC"
    override fun getKoinName() = KoinNames.ModuleL322Dsc

    override fun getImageResource() = Res.drawable.module_abs

    override fun getModuleDescription() = Res.string.module_description_dsc

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null

        interfaceWrite(byteArrayOf(0x00))
        val message = interfaceWrite(byteArrayOf(0x04, 0x01))
        try {
            if (message.isValid && message.answer.size > 3) {
                result = ArrayList()
                val errorDetails =
                    arrayOf(
                        intArrayOf(0x00, 0x01),
                        intArrayOf(0x03, 0x02),
                        intArrayOf(0x04, 0x05),
                        intArrayOf(0x07, 0x06),
                        intArrayOf(0x09, 0x08),
                        intArrayOf(0x0B, 0x0A),
                        intArrayOf(0x0D, 0x0C),
                        intArrayOf(0x0F, 0x0F)
                    )
                for (i in 0 until (message.answer[3].toInt() and 0xFF)) {
                    val errorCode = String.format("%02X", message.answer[4 + 4 * i])
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount(message.answer[6 + 4 * i].toInt() and 0xFF))
                    errorDescription.add(
                        ResourceHelper.getErrorDescriptionSpeed(
                            ((message.answer[7 + 4 * i].toInt() and 0xFF) shl 8) + 0.0625 * (message.answer[8 + 4 * i].toInt() and 0xFF)
                        )
                    )
                    for (j in errorDetails.indices) {
                        if ((message.answer[5 + 4 * i].toInt() and (0x01 shl j)) == (0x01 shl j)) {
                            ResourceMapper
                                .stringResource(
                                    String.format(
                                        "dsc_error_detail_%02X",
                                        errorDetails[j][1]
                                    )
                                )
                                ?.let { errorDescription.add(Pair(it, null)) }
                        } else {
                            ResourceMapper
                                .stringResource(
                                    String.format(
                                        "dsc_error_detail_%02X",
                                        errorDetails[j][1]
                                    )
                                )
                                ?.let { errorDescription.add(Pair(it, null)) }
                        }
                    }
                    result.add(ModuleError(ModuleAddress.MODULE_DSC, errorCode, errorDescription))
                }
            }
        } catch (ex :Exception)
        {
            Logger.e {"$tag [getAllErrors], $ex, $message"}
        }
        return result
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (j in 0..3) {
            val message = interfaceWrite(byteArrayOf(0x0B, j.toByte()))
            try {
                if (message.isValid) {
                    val res = message.answer.copyOfRange(3, message.answer.size - 1)
                    if (j == 0 || j == 1 || j == 3) {
                        var i = 0
                        while (i < res.size) {
                            val a = res[i]
                            val b = res[i + 1]
                            res[i] = b
                            res[i + 1] = a
                            i += 2
                        }
                    }

                    result[j] = res
                } else {
                    return null
                }
            } catch (ex : Exception)
            {
                Logger.e{"$tag [readLiveData], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun readCoding(): List<ByteArray>? {
        interfaceWrite(byteArrayOf(0x00))
        val result: MutableList<ByteArray> = ArrayList()
        val message = interfaceWrite(byteArrayOf(0x08, 0x00, 0x06))
        try {
            if (message.isValid)
                result.add(message.answer.copyOfRange(3, message.answer.size - 1))
            else
                return null
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readCoding], $ex, $message"}
            return null
        }
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        interfaceWrite(byteArrayOf(0x00))
        if (interfaceWrite(byteArrayOf(0x90.toByte())).isValid) {
            val dataToSaveWithChecksum = calculateCodingChecksum(dataToSave.toMutableList())
            for (i in dataToSaveWithChecksum.indices)
                interfaceWrite(arrayOf(byteArrayOf(0x09, i.toByte()),dataToSaveWithChecksum[i]))
            val checkData = readCoding()
            reset()
            return checkData!![0].copyOfRange( 0, 4).contentEquals(dataToSaveWithChecksum[0])
        } else return false
    }

    private fun calculateCodingChecksum(data: MutableList<ByteArray>): List<ByteArray> {
        val cs = (0xFF.toByte() - data[0][2] - data[0][3]).toByte()
        data[0][1] = cs
        data[0] = data[0].copyOfRange(0, 4)
        return data
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean = interfaceWrite(byteArrayOf(0x00)).isValid

    //override fun getAdvanceControlActivity(context: Context): Intent = Intent(context, ActivityControlDSC::class.java)

    //override fun getAdvanceControlTitle(): String = resources.getString(R.string.activity_control_dsc_brake_bleeding)
}
