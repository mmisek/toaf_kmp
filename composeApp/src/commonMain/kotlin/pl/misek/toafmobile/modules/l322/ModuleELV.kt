package pl.misek.toafmobile.modules.l322

import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_elv
import toafmobile.composeapp.generated.resources.module_elv

class ModuleELV( protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_ELV, listOf(CarType.L322)),
    IModuleLiveData {
    private val standardCoding = arrayOf(
        byteArrayOf(0x06,0x01,0x3F,0xC7.toByte(),0x6C,0x8B.toByte(),0x3A,0xEB.toByte(),0xED.toByte(),0xFA.toByte(),0x45,0x32,0x20,0x00,0x40,0x93.toByte()),
        byteArrayOf(0x33,0x33,0x3A,0xC0.toByte(),0xAD.toByte(),0x04,0x00,0x30,0x0F,0xA0.toByte(),0xFC.toByte(),0x18,0x4E,0x20,0x4E,0x20),
        byteArrayOf(0x0F,0x6B,0x25,0xAB.toByte(),0x00,0xC8.toByte(),0x00,0x64,0x00,0x20,0x00,0x60,0x14,0x00,0x73,0x0E),
        byteArrayOf(0x01,0x13,0x88.toByte(),0x45,0x53,0x0B,0xB8.toByte(),0x1E,0x15,0x15,0xAB.toByte(),0x8D.toByte(),0x02,0x02,0x10,0x00),
        byteArrayOf(0x3E,0xA8.toByte(),0x0A,0x0A,0x03,0x32,0x5A,0x64,0x64,0x50,0x8C.toByte(),0x50,0x64,0x64,0x64,0x3C),
        byteArrayOf(0x4B,0x0F,0x03,0x50,0xFE.toByte(),0xF0.toByte(),0xFE.toByte(),0xC8.toByte(),0xC8.toByte(),0x05,0x1E,0x55,0x7F,0xFF.toByte(),0xC8.toByte(),0xFF.toByte()),
        byteArrayOf(0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x2E,0x2D))

    override fun getKoinName() = KoinNames.ModuleL322Elv

    override fun getImageResource() = Res.drawable.module_elv

    override fun getModuleDescription() = Res.string.module_description_elv

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        for (index in 1..2) {
            val message = interfaceWrite(byteArrayOf(0x04, index.toByte()))
            if (message.isValid && message.answer.size > 4) {
                result = ArrayList()
                for (i in 0 until (message.answer[3].toInt() and 0xFF)) {
                    val errorCode = String.format("%02X", message.answer[4 + 2 * i])
                    val errorDescription: MutableList<Pair<StringResource,Any?>> = ArrayList()
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[5 + 2 * i].toInt() and 0xFF) and 0x1F))
                    errorDescription.add(ResourceHelper.getErrorDescriptionIsStatic((message.answer[5 + 2 * i].toInt() and 0x20) == 0x20))
                    result.add(ModuleError(ModuleAddress.MODULE_ELV, errorCode, errorDescription))
                }
            }
        }
        return result
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val message = interfaceWrite(byteArrayOf(0x0B))
        if (message.isValid)
            result[0] = message.answer.copyOfRange(3, message.answer.size)
        else
            return null
        return result
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean  = true

    suspend fun readEeprom(): ByteArray? {
        var result = ByteArray(0)
        var address: Byte = 0x00
        for (i in 0..15) {
            val message = interfaceWrite(byteArrayOf(0x06, 0x03, 0x01, address, 0x10))
            if (message.isValid) {
                result += message.answer.copyOfRange(3, 0x13)
                address = (address + 0x10).toByte()
            }
        }
        return if (result.size == 256) result else null
    }

    suspend fun writeEeprom(dataToWrite: ByteArray): Boolean {
        if (dataToWrite.size != 256) return false
        for (i in 0 until dataToWrite.size / 8) {
            val data = byteArrayOf(0x07, 0x03, 0x00, 0x00, ((i * 8) / 2).toByte(), 0x04) + dataToWrite.copyOfRange( i * 8, 8 * (i + 1))
            interfaceWrite(data)
        }

        return reset()
    }
}
