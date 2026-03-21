package pl.misek.toafmobile.modules.interfaces

interface IModuleVIN : IModuleAdvanceCoding {
    suspend fun writeVIN(newVin: String): Boolean
    fun getVinLength() : Int
}
