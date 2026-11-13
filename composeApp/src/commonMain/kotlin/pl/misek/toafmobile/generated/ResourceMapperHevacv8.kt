package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperHevacv8 {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "hevacv8_control_enable" -> Res.string.hevacv8_control_enable
            "hevacv8_control_inhibit" -> Res.string.hevacv8_control_inhibit
            "hevacv8_error_01" -> Res.string.hevacv8_error_01
            "hevacv8_error_02" -> Res.string.hevacv8_error_02
            "hevacv8_error_04" -> Res.string.hevacv8_error_04
            "hevacv8_error_08" -> Res.string.hevacv8_error_08
            "hevacv8_error_10" -> Res.string.hevacv8_error_10
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
