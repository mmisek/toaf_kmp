package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Locale
import kotlinx.coroutines.CancellationException

class ModuleTXU(protocolKWP: ProtocolKWP?) : IModule
{
    private val tag = "ModuleTXU"
    private var kwp: IProtocol? = null
    private val moduleAddress = ModuleAddress.MODULE_TXU.address

    init {
        if (protocolKWP != null) kwp = protocolKWP
    }

    override fun getKoinName() = KoinNames.ModuleL322Txu

    override fun getModuleProtocol(): IProtocol {
        return kwp!!
    }

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol
    }

    override fun getModuleName() = "TXU"

    override fun getModuleAddress(): ModuleAddress {
        return ModuleAddress.MODULE_TXU
    }

    override fun getImageResource() = Res.drawable.module_txu
    
    override fun getModuleDescription() = Res.string.module_description_txu

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x80.toByte()))
        try {
            if (message.isValid) {
                info = ModuleInfo()
                val answer = message.answer
                info.id1 = "${String(answer.copyOfRange(6, 8))}${
                    (answer[9].toInt() and 0xFF).toByte().toHexString()
                }${
                    (answer[10].toInt() and 0xFF).toByte().toHexString()
                }${(answer[11].toInt() and 0xFF).toByte().toHexString()}"
                info.hw = (answer[12].toInt() and 0xFF).toByte().toHexString()
                info.ci = (answer[13].toInt() and 0xFF).toByte().toHexString()
                info.di = (answer[15].toInt() and 0xFF).toByte().toHexString()
                info.producer = ModulesFunction.getProducer(answer[22].toInt())
                info.date = String.format(
                    Locale.getDefault(),
                    "%02X/%02X/%02X%02X",
                    message.answer[21], message.answer[20], message.answer[18], message.answer[19]
                )
                info.serial = getSerialNumber().toString()
                info.additionalInfo = ArrayList()
                info.additionalInfo.add(
                    Pair(
                        Res.string.module_info_catalogue_version,
                        "${message.answer[23].toInt()}.${message.answer[24].toInt()}.${message.answer[25].toInt()}"
                    )
                )
                info.additionalInfo.add(
                    Pair(
                        Res.string.module_info_functional_version,
                        "${message.answer[26].toInt()}.${message.answer[27].toInt()}.${message.answer[28].toInt()}"
                    )
                )
                info.additionalInfo.add(
                    Pair(
                        Res.string.module_info_os_version,
                        "${message.answer[29].toInt()}.${message.answer[30].toInt()}.${message.answer[31].toInt()}"
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

    private suspend fun getSerialNumber(): String? {
        val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x89.toByte()))
        try {
            if (message.isValid) {
                return String(message.answer.copyOfRange(6, message.answer.size - 1))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getSerialNumber], $ex, $message"}
        }

        return null
    }

    override suspend fun getVIN() = null

    override suspend fun getOdometer() = null

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        val message = kwp!!.write(moduleAddress, 0x18.toByte(), byteArrayOf(0x02, 0xFF.toByte(), 0xFF.toByte()), 250)
        try {
            if (message.isValid) {
                result = ArrayList()
                for (i in 0 until (message.answer[5].toInt() and 0xFF)) {
                    val errorCode =
                        ((message.answer[6 + 3 * i].toInt() and 0xFF) shl 8) + (message.answer[7 + 3 * i].toInt() and 0xFF)
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    //val errorDetailMessage = kwp?.write(moduleAddress,0x17,byteArrayOf(message.answer[6+3*i], message.answer[7+3*i]))
                    result.add(
                        ModuleError(
                            ModuleAddress.MODULE_TXU,
                            String.format("%04X", errorCode),
                            errorDescription
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
        return result
    }

    override suspend fun clearAllErrors(): Boolean {
        return kwp!!.write(moduleAddress, 0x14.toByte(), byteArrayOf(0x00, 0x00), 500).isValid
    }

    override suspend fun reset(): Boolean {
        return kwp!!.write(moduleAddress, 0x11.toByte(), byteArrayOf(0x01)).isValid
    }


    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.L322)
    }
}
