package pl.misek.toafmobile.modules.enums

import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.modules.ModuleInfo

sealed interface FlashProgrammingState {
    data object Idle : FlashProgrammingState
    data class Progress(val progress : Int) : FlashProgrammingState
    data class Message(val message: Any) : FlashProgrammingState
    data class MessageWithParameter(val message: StringResource,val parameter : Any) : FlashProgrammingState
    data class FlashData(val flashData : List<Byte>, var moduleInfo : ModuleInfo?, var vin : String?) : FlashProgrammingState
}