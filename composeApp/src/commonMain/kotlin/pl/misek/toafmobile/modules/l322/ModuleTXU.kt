package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Locale
import kotlinx.coroutines.CancellationException

class ModuleTXU(protocolKWP: ProtocolKWP?) : IModule, IModuleLiveData
{
    private val tag = "ModuleTXU"
    private var kwp: IProtocol? = null
    private val moduleAddress = ModuleAddress.MODULE_TXU.address
    private val liveDataBlocksToRead: MutableList<Byte> = ArrayList()

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

    private fun Byte.hex() = (toInt() and 0xFF).toByte().toHexString()

    /**
     * Numer czesci wg Testbook (DAVU0010): [6] != 0 - 3 znaki ASCII [6..8] + BCD [9..11],
     * [6] == 0 - 7 cyfr BCD z [8..11] (bez pierwszej cyfry)
     */
    private fun getPartNumber(answer: ByteArray): String {
        return if (answer[6].toInt() != 0)
            String(answer.copyOfRange(6, 9)) + answer.copyOfRange(9, 12).joinToString("") { it.hex() }
        else
            answer.copyOfRange(8, 12).joinToString("") { it.hex() }.substring(1)
    }

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x80.toByte()))
        try {
            if (message.isValid) {
                info = ModuleInfo()
                val answer = message.answer
                info.id1 = getPartNumber(answer)
                info.hw = answer[12].hex()
                info.ci = answer[13].hex()
                info.di = answer[14].hex()
                info.bi = answer[15].hex()
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

    /** 18 02 FF FF: [5] liczba bledow, rekord 3 bajty - kod (2 bajty) + status (bit 6 = blad obecny, wg DAVU0009) */
    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        val message = kwp!!.write(moduleAddress, 0x18.toByte(), byteArrayOf(0x02, 0xFF.toByte(), 0xFF.toByte()), 250)
        try {
            if (message.isValid) {
                result = ArrayList()
                for (i in 0 until (message.answer[5].toInt() and 0xFF)) {
                    val errorCode =
                        ((message.answer[6 + 3 * i].toInt() and 0xFF) shl 8) + (message.answer[7 + 3 * i].toInt() and 0xFF)
                    val status = message.answer[8 + 3 * i].toInt() and 0xFF
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    errorDescription.add(ResourceHelper.getErrorDescriptionIsPresent((status and 0x40) != 0))
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

    /** ReadDataByLocalID 21 <blok> (bloki 01..0A wg DAVU0009), dane od [6] */
    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (b in liveDataBlocksToRead) {
            val message = kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(b), 250)
            try {
                if (message.isValid)
                    result[b.toInt()] = message.answer.copyOfRange(6, message.answer.size - 1)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [readLiveData], $ex, $message"}
            }
        }
        return result.ifEmpty { null }
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        liveDataBlocksToRead.clear()
        liveDataElements.map { it.block.toByte() }.distinct().forEach { liveDataBlocksToRead.add(it) }
        return true
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.L322)
    }
}
