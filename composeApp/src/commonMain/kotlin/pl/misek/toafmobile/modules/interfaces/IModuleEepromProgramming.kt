package pl.misek.toafmobile.modules.interfaces

interface IModuleEepromProgramming {
    suspend fun beforeWrite() {}
    suspend fun beforeRead() {}
    suspend fun readEeprom(address: Int, size: Int): ByteArray?
    suspend fun writeEeprom(address: Int, data: ByteArray): Boolean
    fun getEepromSize(): Int
    fun getEepromReadSize(): Int
    fun getEepromWriteSize(): Int
}
