package pl.misek.toafmobile.control

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable
data class ControlElementContainerJson(
    val elements : List<ControlElementJson>
)

@Serializable
data class ControlSetValueJson (
    val min: String? = "",
    val max: String? = "",
    val unit: String? = "",
)

@Serializable
data class ControlElementJson(
    var name: String? = "",
    var block: String? = "",
    var byte: String? = "",
    var value: String? = "",
    @SerialName("set_value") var controlSetValue: ControlSetValueJson? = null,
    var visible: String? = ""
)

data class ControlElement(
    var name: String = "",
    var description: StringResource? = null,
    var block: Int = 0,
    var byteInBlock: Int = 0,
    var value: Int = 0,
    var controlSetValue: ControlSetValue? = null,
    var visibility: Boolean = true
)

data class ControlSetValue (
    var min: Int = 0,
    var max: Int = 100,
    var unit: String = "",
    var value: Int = 0
)
