package pl.misek.toafmobile.modules.interfaces

interface IModuleCoding {
    suspend fun readCoding(): List<ByteArray>?
    suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean
}
