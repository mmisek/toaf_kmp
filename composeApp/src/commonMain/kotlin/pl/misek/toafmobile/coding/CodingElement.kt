package pl.misek.toafmobile.coding

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable
data class CodingElementContainerJson(
    var elements : List<CodingElementJson>
)
@Serializable
data class CodingElementCodingValuesJson(
    var data : String?,
    var text : String?
)
@Serializable
data class CodingElementCustomValueJson(
    var min : String?,
    var max : String?
)
@Serializable
data class CodingElementJson(
    var name: String = "",
    var block: String = "",
    var byte: String = "",
    var mask: String = "",
    @SerialName("coding_index") var codingIndex: MutableList<String>? = ArrayList(),
    @SerialName("coding_values") var codingValues: MutableList<CodingElementCodingValuesJson>? = ArrayList(),
    @SerialName("custom_values") var codingCustomValues: CodingElementCustomValueJson? = null,
    var visible: String = ""
)

data class CodingElement(
    var name: String = "",
    var description : StringResource? = null,
    var block: Int = 0,
    var byteInBlock: Int = 0,
    var mask: Int = 0,
    var codingIndex: MutableList<Int> = ArrayList(),
    var codingValues: MutableList<Pair<Int, StringResource?>> = ArrayList(),
    var codingCustomValues: Pair<Int, Int>? =
        Pair(
            Int.MIN_VALUE,
            Int.MAX_VALUE
        ),
    var selectedIndex: Int = 0,
    var visible: Boolean = true
)
