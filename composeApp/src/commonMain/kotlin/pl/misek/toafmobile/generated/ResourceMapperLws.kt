package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperLws {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "lws_advance_control_calibration_failed" -> Res.string.lws_advance_control_calibration_failed
            "lws_advance_control_calibration_successful" -> Res.string.lws_advance_control_calibration_successful
            "lws_advance_control_title" -> Res.string.lws_advance_control_title
            "lws_advance_control_write_adjustment" -> Res.string.lws_advance_control_write_adjustment
            "lws_error_01" -> Res.string.lws_error_01
            "lws_error_02" -> Res.string.lws_error_02
            "lws_error_03" -> Res.string.lws_error_03
            "lws_error_04" -> Res.string.lws_error_04
            "lws_error_05" -> Res.string.lws_error_05
            "lws_error_06" -> Res.string.lws_error_06
            "lws_error_07" -> Res.string.lws_error_07
            "lws_error_08" -> Res.string.lws_error_08
            "lws_error_09" -> Res.string.lws_error_09
            "lws_error_0a" -> Res.string.lws_error_0A
            "lws_error_0b" -> Res.string.lws_error_0B
            "lws_error_0c" -> Res.string.lws_error_0C
            "lws_error_0d" -> Res.string.lws_error_0D
            "lws_error_0e" -> Res.string.lws_error_0E
            "lws_error_0f" -> Res.string.lws_error_0F
            "lws_error_10" -> Res.string.lws_error_10
            "lws_live_data_lateral_acceleration" -> Res.string.lws_live_data_lateral_acceleration
            "lws_live_data_program_cycle_time" -> Res.string.lws_live_data_program_cycle_time
            "lws_live_data_steering_angle" -> Res.string.lws_live_data_steering_angle
            "lws_live_data_steering_angle_speed" -> Res.string.lws_live_data_steering_angle_speed
            "lws_live_data_synthetic_steering_wheel_angles" -> Res.string.lws_live_data_synthetic_steering_wheel_angles
            "lws_live_data_vehicle_speed" -> Res.string.lws_live_data_vehicle_speed
            "lws_live_data_wheel_speed_left_front" -> Res.string.lws_live_data_wheel_speed_left_front
            "lws_live_data_wheel_speed_left_rear" -> Res.string.lws_live_data_wheel_speed_left_rear
            "lws_live_data_wheel_speed_right_front" -> Res.string.lws_live_data_wheel_speed_right_front
            "lws_live_data_wheel_speed_right_rear" -> Res.string.lws_live_data_wheel_speed_right_rear
            "lws_live_data_yaw_rate" -> Res.string.lws_live_data_yaw_rate
            "lws_live_data_lws_new" -> Res.string.lws_live_data_lws_new
            "lws_live_data_power_on_reset" -> Res.string.lws_live_data_power_on_reset
            "lws_live_data_watchdog_reset" -> Res.string.lws_live_data_watchdog_reset
            "lws_live_data_relative_angle_only" -> Res.string.lws_live_data_relative_angle_only
            "lws_live_data_filter_not_initialised" -> Res.string.lws_live_data_filter_not_initialised
            "lws_live_data_calibration_mode" -> Res.string.lws_live_data_calibration_mode
            "lws_live_data_can_init" -> Res.string.lws_live_data_can_init
            "lws_live_data_asc2_received" -> Res.string.lws_live_data_asc2_received
            "lws_live_data_asc3_calibration_received" -> Res.string.lws_live_data_asc3_calibration_received
            "lws_live_data_asc3_angle_speed_received" -> Res.string.lws_live_data_asc3_angle_speed_received
            "lws_live_data_can_version_received" -> Res.string.lws_live_data_can_version_received
            "lws_live_data_can_version_ok" -> Res.string.lws_live_data_can_version_ok
            "lws_live_data_can_tx_off" -> Res.string.lws_live_data_can_tx_off
            "lws_live_data_can_off" -> Res.string.lws_live_data_can_off
            "lws_live_data_err_asc2" -> Res.string.lws_live_data_err_asc2
            "lws_live_data_err_can_version" -> Res.string.lws_live_data_err_can_version
            "lws_live_data_err_id" -> Res.string.lws_live_data_err_id
            "lws_live_data_err_program" -> Res.string.lws_live_data_err_program
            "lws_live_data_err_angle_gradient" -> Res.string.lws_live_data_err_angle_gradient
            "lws_live_data_err_angle_max" -> Res.string.lws_live_data_err_angle_max
            "lws_live_data_err_stack" -> Res.string.lws_live_data_err_stack
            "lws_live_data_err_watchdog" -> Res.string.lws_live_data_err_watchdog
            "lws_live_data_err_kl30" -> Res.string.lws_live_data_err_kl30
            "lws_live_data_err_sensor_current" -> Res.string.lws_live_data_err_sensor_current
            "lws_live_data_err_90deg_calibration" -> Res.string.lws_live_data_err_90deg_calibration
            "lws_live_data_err_90deg" -> Res.string.lws_live_data_err_90deg
            "lws_live_data_err_eeprom" -> Res.string.lws_live_data_err_eeprom
            "lws_live_data_err_ad" -> Res.string.lws_live_data_err_ad
            "lws_live_data_err_rom" -> Res.string.lws_live_data_err_rom
            "lws_live_data_err_ram" -> Res.string.lws_live_data_err_ram
            "lws_live_data_scheduler_state" -> Res.string.lws_live_data_scheduler_state
            "lws_live_data_scheduler_mode" -> Res.string.lws_live_data_scheduler_mode
            "lws_live_data_value_sdl_zykl_syc" -> Res.string.lws_live_data_value_sdl_zykl_syc
            "lws_live_data_value_sdl_zykl_start" -> Res.string.lws_live_data_value_sdl_zykl_start
            "lws_live_data_value_sdl_lrw" -> Res.string.lws_live_data_value_sdl_lrw
            "lws_live_data_value_sdl_asc2" -> Res.string.lws_live_data_value_sdl_asc2
            "lws_live_data_value_sdl_psi_pkt" -> Res.string.lws_live_data_value_sdl_psi_pkt
            "lws_live_data_value_sdl_vir_sens" -> Res.string.lws_live_data_value_sdl_vir_sens
            "lws_live_data_value_sdl_klass" -> Res.string.lws_live_data_value_sdl_klass
            "lws_live_data_value_sdl_stat_err" -> Res.string.lws_live_data_value_sdl_stat_err
            "lws_live_data_value_sdl_lws1_tr" -> Res.string.lws_live_data_value_sdl_lws1_tr
            "lws_live_data_value_sdl_init_mod" -> Res.string.lws_live_data_value_sdl_init_mod
            "lws_live_data_value_asc2_syc1_mod" -> Res.string.lws_live_data_value_asc2_syc1_mod
            "lws_live_data_value_asc2_syc2_mod" -> Res.string.lws_live_data_value_asc2_syc2_mod
            "lws_live_data_value_asc2_asyc_mod" -> Res.string.lws_live_data_value_asc2_asyc_mod
            "lws_live_data_value_sdl_slow_mod" -> Res.string.lws_live_data_value_sdl_slow_mod
            "lws_live_data_value_sdl_nachlf_mod" -> Res.string.lws_live_data_value_sdl_nachlf_mod
            "lws_live_data_value_sdl_can_err_mod" -> Res.string.lws_live_data_value_sdl_can_err_mod
            "lws_live_data_can_driver_supply" -> Res.string.lws_live_data_can_driver_supply
            "lws_live_data_base_sensor_supply" -> Res.string.lws_live_data_base_sensor_supply
            "lws_live_data_sensor_slider_1" -> Res.string.lws_live_data_sensor_slider_1
            "lws_live_data_sensor_slider_2" -> Res.string.lws_live_data_sensor_slider_2
            "lws_live_data_terminal_87_voltage" -> Res.string.lws_live_data_terminal_87_voltage
            "lws_live_data_terminal_30_counter" -> Res.string.lws_live_data_terminal_30_counter
            "lws_live_data_value_yes" -> Res.string.lws_live_data_value_yes
            "lws_live_data_value_no" -> Res.string.lws_live_data_value_no
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
