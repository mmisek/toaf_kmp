package pl.misek.toafmobile.modules.interfaces

import kotlinx.coroutines.flow.SharedFlow
import pl.misek.toafmobile.modules.enums.FlashProgrammingState

interface IModuleFlashProgramming {
    val programmingState : SharedFlow<FlashProgrammingState>
    suspend fun readFlash()
    suspend fun writeFlash(data: ByteArray)
    val flashProgrammingAvailable : Boolean
}
