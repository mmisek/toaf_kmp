package pl.misek.toafmobile.generated

import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

object ResourceHelper {
    fun byteArrayToDate(byteArray: ByteArray) : String
    {
        return String.format(
            "%02X/%02X/%02X%02X",
            byteArray[0].toInt() and 0xFF,
            byteArray[1].toInt() and 0xFF,
            byteArray[2].toInt() and 0xFF,
            byteArray[3].toInt() and 0xFF)
    }

    fun getErrorDescriptionCount(count :Int) : Pair<StringResource,Int>
    {
        return Pair(Res.string.module_error_count, count)
    }

    fun getErrorDescriptionLogisticCounter(count :Int) : Pair<StringResource,Int>
    {
        return Pair(Res.string.module_error_logistic_counter, count)
    }

    fun getErrorDescriptionIsStatic(isStatic : Boolean) : Pair<StringResource,Int?>
    {
        return  Pair(if (isStatic) Res.string.shd_error_static else Res.string.shd_error_sporadic, null )
    }
    fun getErrorDescriptionIsPresent(isPresent : Boolean) : Pair<StringResource,Int?>
    {
        return  Pair(if (isPresent) Res.string.bmbt_error_present else Res.string.bmbt_error_not_present, null )
    }
    fun getErrorDescriptionSpeed(speed : Double): Pair<StringResource, Double>
    {
        return Pair(Res.string.string_with_float_value_speed, speed)
    }
}