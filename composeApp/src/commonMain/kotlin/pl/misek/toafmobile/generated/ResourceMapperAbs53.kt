package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperAbs53 {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "abs53_control_abs_lamp_test" -> Res.string.abs53_control_abs_lamp_test
            "abs53_control_pump_start" -> Res.string.abs53_control_pump_start
            "abs53_control_pump_stop" -> Res.string.abs53_control_pump_stop
            "abs53_control_valve_fl_close" -> Res.string.abs53_control_valve_fl_close
            "abs53_control_valve_fl_open" -> Res.string.abs53_control_valve_fl_open
            "abs53_control_valve_fr_close" -> Res.string.abs53_control_valve_fr_close
            "abs53_control_valve_fr_open" -> Res.string.abs53_control_valve_fr_open
            "abs53_control_valve_rl_close" -> Res.string.abs53_control_valve_rl_close
            "abs53_control_valve_rl_open" -> Res.string.abs53_control_valve_rl_open
            "abs53_control_valve_rr_close" -> Res.string.abs53_control_valve_rr_close
            "abs53_control_valve_rr_open" -> Res.string.abs53_control_valve_rr_open
            "abs53_live_data_brake_pedal" -> Res.string.abs53_live_data_brake_pedal
            "abs53_live_data_main_supply_relay" -> Res.string.abs53_live_data_main_supply_relay
            "abs53_live_data_pump_ground_relay" -> Res.string.abs53_live_data_pump_ground_relay
            "abs53_live_data_value_closed" -> Res.string.abs53_live_data_value_closed
            "abs53_live_data_value_off" -> Res.string.abs53_live_data_value_off
            "abs53_live_data_value_on" -> Res.string.abs53_live_data_value_on
            "abs53_live_data_value_open" -> Res.string.abs53_live_data_value_open
            "abs53_live_data_wheel_speed_fl" -> Res.string.abs53_live_data_wheel_speed_fl
            "abs53_live_data_wheel_speed_fr" -> Res.string.abs53_live_data_wheel_speed_fr
            "abs53_live_data_wheel_speed_rl" -> Res.string.abs53_live_data_wheel_speed_rl
            "abs53_live_data_wheel_speed_rr" -> Res.string.abs53_live_data_wheel_speed_rr
            "abs53_control_brake_lamp_test" -> Res.string.abs53_control_brake_lamp_test
            "abs53_error_11" -> Res.string.abs53_error_11
            "abs53_error_15" -> Res.string.abs53_error_15
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
