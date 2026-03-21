package pl.misek.toafmobile.modules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable
data class FreezeFrameElementContainerJson(
    @SerialName("freeze_frames") var list : List<FreezeFrameElementJson>
)

@Serializable
data class FreezeFrameElementJson (
    var name: String = "",
    var unit: String = "",
    var mask: String = "",
    var byteInBlock: String = "",
    @SerialName("factor_a") var factorA: String = "",
    @SerialName("factor_b") var factorB: String= "",
    var u2: String? = ""
)



data class FreezeFrameElement (
    var name: String = "",
    var description: StringResource? = null,
    var unit: String = "",
    var mask: Long = 0xFF,
    var byteInBlock: Int = 0,
    var factorA: Float = 1f,
    var factorB: Float = 0f,
    var u2: Boolean = false
)