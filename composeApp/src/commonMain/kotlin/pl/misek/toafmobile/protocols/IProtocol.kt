package pl.misek.toafmobile.protocols

import pl.misek.toafmobile.interfaces.IInterfaceDevice

interface IProtocol {
    suspend fun write(address: Byte, command: Byte, data: ByteArray): OBDMessage
    suspend fun write(address: Byte, command: Byte, data: ByteArray, timeout: Int): OBDMessage
    suspend fun write(address: Byte, command: Byte, data: ByteArray, timeout: Int, forceAnswer: Boolean): OBDMessage
    fun setSpeed(baud: Int)
    fun setDevice(newDevice : IInterfaceDevice?)

    companion object {
        const val useDelay: Boolean = false
    }
}
