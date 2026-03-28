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
            "shd_error_01" -> Res.string.shd_error_01
            "shd_error_02" -> Res.string.shd_error_02
            "shd_error_03" -> Res.string.shd_error_03
            "shd_error_04" -> Res.string.shd_error_04
            "shd_error_05" -> Res.string.shd_error_05
            "shd_error_sporadic" -> Res.string.shd_error_sporadic
            "shd_error_static" -> Res.string.shd_error_static
            "shd_live_data_anti_trap_enabled" -> Res.string.shd_live_data_anti_trap_enabled
            "shd_live_data_comfort_function" -> Res.string.shd_live_data_comfort_function
            "shd_live_data_diagnostic_mode" -> Res.string.shd_live_data_diagnostic_mode
            "shd_live_data_increment_encoder_master" -> Res.string.shd_live_data_increment_encoder_master
            "shd_live_data_increment_encoder_slave" -> Res.string.shd_live_data_increment_encoder_slave
            "shd_live_data_manual_levering" -> Res.string.shd_live_data_manual_levering
            "shd_live_data_manual_lifting" -> Res.string.shd_live_data_manual_lifting
            "shd_live_data_manual_push_close" -> Res.string.shd_live_data_manual_push_close
            "shd_live_data_manual_push_open" -> Res.string.shd_live_data_manual_push_open
            "shd_live_data_normalized" -> Res.string.shd_live_data_normalized
            "shd_live_data_over_voltage_detection" -> Res.string.shd_live_data_over_voltage_detection
            "shd_live_data_relay_control_close" -> Res.string.shd_live_data_relay_control_close
            "shd_live_data_relay_control_open" -> Res.string.shd_live_data_relay_control_open
            "shd_live_data_relay_motor_close" -> Res.string.shd_live_data_relay_motor_close
            "shd_live_data_relay_motor_open" -> Res.string.shd_live_data_relay_motor_open
            "shd_live_data_reversing_from_close" -> Res.string.shd_live_data_reversing_from_close
            "shd_live_data_sunroof_close_switch" -> Res.string.shd_live_data_sunroof_close_switch
            "shd_live_data_sunroof_disabled" -> Res.string.shd_live_data_sunroof_disabled
            "shd_live_data_sunroof_in_security_area" -> Res.string.shd_live_data_sunroof_in_security_area
            "shd_live_data_sunroof_open_switch" -> Res.string.shd_live_data_sunroof_open_switch
            "shd_live_data_sunroof_position" -> Res.string.shd_live_data_sunroof_position
            "shd_live_data_sunroof_rise_switch" -> Res.string.shd_live_data_sunroof_rise_switch
            "shd_live_data_supply_voltage_incremental_signal" -> Res.string.shd_live_data_supply_voltage_incremental_signal
            "shd_live_data_thermal_protection" -> Res.string.shd_live_data_thermal_protection
            "shd_live_data_tip_close" -> Res.string.shd_live_data_tip_close
            "shd_live_data_tip_lifting" -> Res.string.shd_live_data_tip_lifting
            "shd_live_data_tip_push_on" -> Res.string.shd_live_data_tip_push_on
            "shd_live_data_under_voltage_detection" -> Res.string.shd_live_data_under_voltage_detection
            "shd_live_data_watchdog_trigger_signal" -> Res.string.shd_live_data_watchdog_trigger_signal
        else -> {
          Logger.e {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
