package pl.misek.toafmobile.modules.interfaces

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.protocols.IProtocol

interface IModule {
    fun getKoinName() : KoinNames
    fun getModuleName(): String
    fun getModuleAddress(): ModuleAddress
    fun getImageResource(): DrawableResource?
    fun getModuleDescription() : StringResource?
    suspend fun getIdentification(): ModuleInfo?
    suspend fun getVIN(): String?
    suspend fun getOdometer(): String?

    suspend fun getAllErrors(): List<ModuleError>?
    suspend fun clearAllErrors(): Boolean

    suspend fun reset(): Boolean

    fun getModuleProtocol(): IProtocol
    fun setModuleProtocol(protocol: IProtocol)

    fun getSupportedCarTypes(): List<CarType>
}
