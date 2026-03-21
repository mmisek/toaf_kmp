package pl.misek.toafmobile.modules.interfaces

interface IModuleOdometer : IModuleAdvanceCoding {
    suspend fun writeOdometer(odometer: Long): Boolean
}
