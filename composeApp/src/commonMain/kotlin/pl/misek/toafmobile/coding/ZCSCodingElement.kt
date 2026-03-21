package pl.misek.toafmobile.coding

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable
data class ZcsCodingElementContainerJson(
    var elements : List<ZCSCodingElementJson>
)

@Serializable
data class ZCSCodingElementJson (
    var name: String = "",
    @SerialName("byte") var byteInBlock: Int = 0,
    var mask: Int = 0,
    @SerialName("values") var codingValues: MutableList<Pair<Int, String>> =ArrayList(),
    var selectedIndex: Int = 0,
    var visible: Boolean = true
)

data class ZCSCodingElement (
    var name: String = "",
    var description : StringResource? = null,
    var byteInBlock: Int = 0,
    var mask: Int = 0,
    var codingValues: MutableList<Pair<Int, StringResource?>> =ArrayList(),
    var selectedIndex: Int = 0,
    var visible: Boolean = true
)
