package pl.misek.toafmobile.interfaces

interface IInterfaceDevice {
    suspend fun write(data: ByteArray?, forceAnswer: Boolean, useDelay: Boolean): ByteArray?
    suspend fun write(data: ByteArray?, timeout: Long?, forceAnswer: Boolean, useDelay: Boolean): ByteArray?
    fun disconnect()
    fun connect(deviceName : String)
    val isConnected: Boolean
}
