package pl.misek.toafmobile.modules.containers

import org.jetbrains.compose.resources.StringResource

data class GS20FreezeFrameData(

    val unit : String,
    val mask : Byte,
    val multiplier : Float,
    val divider : Float,
    val adder : Float,
    val values : Map<Int, StringResource>?
)
