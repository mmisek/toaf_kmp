package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperRs {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "rs_control_init" -> Res.string.rs_control_init
            "rs_error_01" -> Res.string.rs_error_01
            "rs_error_02" -> Res.string.rs_error_02
            "rs_error_03" -> Res.string.rs_error_03
            "rs_error_04" -> Res.string.rs_error_04
            "rs_error_05" -> Res.string.rs_error_05
            "rs_error_06" -> Res.string.rs_error_06
            "rs_live_data_park_position" -> Res.string.rs_live_data_park_position
            "rs_live_data_reinforcement" -> Res.string.rs_live_data_reinforcement
            "rs_live_data_time_point_1" -> Res.string.rs_live_data_time_point_1
            "rs_live_data_time_point_2" -> Res.string.rs_live_data_time_point_2
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
