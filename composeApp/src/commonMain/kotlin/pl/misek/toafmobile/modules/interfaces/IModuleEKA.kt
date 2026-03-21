package pl.misek.toafmobile.modules.interfaces

interface IModuleEKA : IModuleAdvanceCoding {
    suspend fun writeEKACode(eka: Int): Boolean
    suspend fun readEKACode(): String?
}
