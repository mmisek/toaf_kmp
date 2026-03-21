package pl.misek.toafmobile.modules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable
data class DDEFreezeFrameContainerJson(
    @SerialName("errors") var list :List<DDEFreezeFrameJson>
)
@Serializable
data class DDEFreezeFrameDetailsJson(
    var data : String = "",
    var text : String? = ""
)
@Serializable
data class DDEFreezeFrameJson (
    var code: String = "",
    @SerialName("ff_items") var ffItems: MutableList<String> = ArrayList(),
    @SerialName("ff_details") var ffDetails: MutableList<DDEFreezeFrameDetailsJson> = ArrayList()
)

data class DDEFreezeFrame (
    var code: String = "",
    var ffItems: MutableList<FreezeFrameElement> = ArrayList(),
    var ffDetails: MutableList<Pair<Int, StringResource?>> = ArrayList()
)