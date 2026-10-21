package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperShd {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "shd_coding_anti_pinch_functionality" -> Res.string.shd_coding_anti_pinch_functionality
            "shd_coding_data_sets" -> Res.string.shd_coding_data_sets
            "shd_coding_one_touch_close" -> Res.string.shd_coding_one_touch_close
            "shd_coding_one_touch_open" -> Res.string.shd_coding_one_touch_open
            "shd_coding_one_touch_run_to_tilt" -> Res.string.shd_coding_one_touch_run_to_tilt
            "shd_coding_one_touch_tilt_to_open" -> Res.string.shd_coding_one_touch_tilt_to_open
            "shd_coding_optimised_close_function" -> Res.string.shd_coding_optimised_close_function
            "shd_coding_pinch_characteristics" -> Res.string.shd_coding_pinch_characteristics
            "shd_control_normalization_reset" -> Res.string.shd_control_normalization_reset
            "shd_control_tip_close" -> Res.string.shd_control_tip_close
            "shd_control_tip_lift" -> Res.string.shd_control_tip_lift
            "shd_control_tip_open" -> Res.string.shd_control_tip_open
            "shd_error_01" -> Res.string.shd_error_01
            "shd_error_02" -> Res.string.shd_error_02
            "shd_error_03" -> Res.string.shd_error_03
            "shd_error_04" -> Res.string.shd_error_04
            "shd_error_05" -> Res.string.shd_error_05
            "shd_error_present" -> Res.string.shd_error_present
            "shd_error_sporadic" -> Res.string.shd_error_sporadic
            "shd_error_sporadic_not_present" -> Res.string.shd_error_sporadic_not_present
            "shd_error_static" -> Res.string.shd_error_static
            "shd_live_data_sunroof_position" -> Res.string.shd_live_data_sunroof_position
            "shd_live_data_switch_status" -> Res.string.shd_live_data_switch_status
            "shd_live_data_value_faulty" -> Res.string.shd_live_data_value_faulty
            "shd_live_data_value_inactive" -> Res.string.shd_live_data_value_inactive
            "shd_live_data_value_inch_close" -> Res.string.shd_live_data_value_inch_close
            "shd_live_data_value_inch_open" -> Res.string.shd_live_data_value_inch_open
            "shd_live_data_value_one_touch_close" -> Res.string.shd_live_data_value_one_touch_close
            "shd_live_data_value_one_touch_open" -> Res.string.shd_live_data_value_one_touch_open
            "shd_live_data_value_tilt_open" -> Res.string.shd_live_data_value_tilt_open
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
