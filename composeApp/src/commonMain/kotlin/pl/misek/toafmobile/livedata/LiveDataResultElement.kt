package pl.misek.toafmobile.livedata

import org.jetbrains.compose.resources.StringResource

data class LiveDataResultElement(
    var name : String,
    var rawValue : Float,
    var valueWithoutUnit : String?,
    var valueWithUnit : String?,
    var description : StringResource?,
    var staticValue : StringResource?
)
