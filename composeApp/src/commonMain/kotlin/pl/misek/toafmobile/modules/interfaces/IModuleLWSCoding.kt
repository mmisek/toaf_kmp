package pl.misek.toafmobile.modules.interfaces

interface IModuleLWSCoding : IModuleAdvanceCoding {
    suspend fun readId(): Int
    suspend fun writeId(id: Int): Boolean

    suspend fun readAdjustment(): Int
    suspend fun writeAdjustment(adjustment: Int): Boolean

    suspend fun writeDefaultCoding(): Boolean
}
