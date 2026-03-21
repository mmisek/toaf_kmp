package pl.misek.toafmobile.activities.composeModels

import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.control.ControlSetValue
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.containers.BCUKey
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleBcuKeys
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEKA
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLSMAutoLight
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleOdometer
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_bcu
import toafmobile.composeapp.generated.resources.module_description_bcu

class MockModule() :
        IModule,
        IModuleVIN,
        IModuleOdometer,
        IModuleBcuKeys,
        IModuleLSMAutoLight,
        IModuleCoding,
        IModuleEKA,
        IModuleControl,
        IModuleEepromProgramming,
        IModuleLiveData
{
    override fun getKoinName() = KoinNames.ModuleR40Bcu
    override fun getModuleName(): String = "BCU"
    override fun getModuleAddress(): ModuleAddress = ModuleAddress.MODULE_DDE
    override fun getImageResource() = Res.drawable.module_bcu
    override fun getModuleDescription() = Res.string.module_description_bcu
    override suspend fun getIdentification() = ModuleInfo()
    override suspend fun getVIN()  = "SARR12345667890"
    override suspend fun getOdometer() = "301234"
    override suspend fun getAllErrors() = listOf(ModuleError(
        ModuleAddress.MODULE_BCU,"04",listOf(Pair(Res.string.module_description_bcu,12))))
    override suspend fun clearAllErrors(): Boolean = true
    override suspend fun reset(): Boolean = true
    override fun getModuleProtocol(): IProtocol = ProtocolDS2(null)
    override fun setModuleProtocol(protocol: IProtocol) {}
    override fun getSupportedCarTypes(): List<CarType> = listOf(CarType.R40, CarType.RJO, CarType.L322)

    override suspend fun writeVIN(newVin: String): Boolean = true
    override fun getVinLength(): Int = 17

    override suspend fun writeOdometer(odometer: Long): Boolean  = true

    override suspend fun removeKeyAt(position: Int): Boolean = true
    override suspend fun addKeyAt(position: Int, keyCode: String): Boolean = true
    override suspend fun readKeys() = listOf(BCUKey(0,"ABCDEF"), BCUKey(1,"123456"))

    override fun detectAutoLightMode(coding: List<ByteArray>?): Int = 0
    override fun setAutoLightMode(
        coding: List<ByteArray>,
        mode: Int
    ): List<ByteArray>  = listOf(ByteArray(10))

    override suspend fun readCoding()= listOf(ByteArray(10))

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean  = true

    override suspend fun writeEKACode(eka: Int): Boolean = true
    override suspend fun readEKACode() = "123456"

    override suspend fun control(controlElement: ControlElement): Boolean = true

    fun getMockControlElemets() : List<ControlElement>
    {
        return listOf(
            ControlElement("Element 1", Res.string.module_description_bcu, 0, 0,0, null,true),
            ControlElement("Element 2", Res.string.module_description_bcu, 1, 1, 1, ControlSetValue(10,110,"V",55),true),
            ControlElement("Element 3", Res.string.module_description_bcu, 2, 2,2, null,false),
        )
    }

    override suspend fun readEeprom(address: Int, size: Int)  = ByteArray(255)

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean  = true

    override fun getEepromSize(): Int = 256

    override fun getEepromReadSize(): Int = 8

    override fun getEepromWriteSize(): Int = 8

    override suspend fun readLiveData()=  mapOf(0 to byteArrayOf(0x00,0x01,0x03))

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) = true
}
