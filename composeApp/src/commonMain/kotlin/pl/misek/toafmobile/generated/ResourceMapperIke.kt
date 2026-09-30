package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperIke {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "ike_coding_acc_flag" -> Res.string.ike_coding_acc_flag
            "ike_coding_arrival_time_format" -> Res.string.ike_coding_arrival_time_format
            "ike_coding_asc3_evaluation" -> Res.string.ike_coding_asc3_evaluation
            "ike_coding_aux_heating_mode" -> Res.string.ike_coding_aux_heating_mode
            "ike_coding_average_speed_units" -> Res.string.ike_coding_average_speed_units
            "ike_coding_bc_code" -> Res.string.ike_coding_bc_code
            "ike_coding_bc_code_active" -> Res.string.ike_coding_bc_code_active
            "ike_coding_bc_speed_correction" -> Res.string.ike_coding_bc_speed_correction
            "ike_coding_bc_with_ignition" -> Res.string.ike_coding_bc_with_ignition
            "ike_coding_brake_force" -> Res.string.ike_coding_brake_force
            "ike_coding_can_asc" -> Res.string.ike_coding_can_asc
            "ike_coding_can_coolant_temp" -> Res.string.ike_coding_can_coolant_temp
            "ike_coding_can_ebv" -> Res.string.ike_coding_can_ebv
            "ike_coding_can_flag" -> Res.string.ike_coding_can_flag
            "ike_coding_can_instr2_send" -> Res.string.ike_coding_can_instr2_send
            "ike_coding_can_instr3_send" -> Res.string.ike_coding_can_instr3_send
            "ike_coding_can_kl61" -> Res.string.ike_coding_can_kl61
            "ike_coding_can_load" -> Res.string.ike_coding_can_load
            "ike_coding_can_shift_indicator" -> Res.string.ike_coding_can_shift_indicator
            "ike_coding_can_tacho" -> Res.string.ike_coding_can_tacho
            "ike_coding_consumption_1_units" -> Res.string.ike_coding_consumption_1_units
            "ike_coding_consumption_2_units" -> Res.string.ike_coding_consumption_2_units
            "ike_coding_displays" -> Res.string.ike_coding_displays
            "ike_coding_distance_units" -> Res.string.ike_coding_distance_units
            "ike_coding_engine_type" -> Res.string.ike_coding_engine_type
            "ike_coding_ext_temp_warn_gong" -> Res.string.ike_coding_ext_temp_warn_gong
            "ike_coding_fuel_flap_warning" -> Res.string.ike_coding_fuel_flap_warning
            "ike_coding_gearbox_type" -> Res.string.ike_coding_gearbox_type
            "ike_coding_gps_clock" -> Res.string.ike_coding_gps_clock
            "ike_coding_hba" -> Res.string.ike_coding_hba
            "ike_coding_k_number_distance" -> Res.string.ike_coding_k_number_distance
            "ike_coding_k_number_distance_compl" -> Res.string.ike_coding_k_number_distance_compl
            "ike_coding_k_number_tacho" -> Res.string.ike_coding_k_number_tacho
            "ike_coding_k_number_tacho_compl" -> Res.string.ike_coding_k_number_tacho_compl
            "ike_coding_kl50_can" -> Res.string.ike_coding_kl50_can
            "ike_coding_language" -> Res.string.ike_coding_language
            "ike_coding_load_calibration_factor" -> Res.string.ike_coding_load_calibration_factor
            "ike_coding_memo_flag" -> Res.string.ike_coding_memo_flag
            "ike_coding_memory_flag" -> Res.string.ike_coding_memory_flag
            "ike_coding_model_year_bc" -> Res.string.ike_coding_model_year_bc
            "ike_coding_motor_factor" -> Res.string.ike_coding_motor_factor
            "ike_coding_num_cylinders" -> Res.string.ike_coding_num_cylinders
            "ike_coding_odometer_units" -> Res.string.ike_coding_odometer_units
            "ike_coding_park_heating" -> Res.string.ike_coding_park_heating
            "ike_coding_park_ventilation" -> Res.string.ike_coding_park_ventilation
            "ike_coding_range_units" -> Res.string.ike_coding_range_units
            "ike_coding_rcc_clock" -> Res.string.ike_coding_rcc_clock
            "ike_coding_rcc_wake_time" -> Res.string.ike_coding_rcc_wake_time
            "ike_coding_replace_point" -> Res.string.ike_coding_replace_point
            "ike_coding_seatbelt_warning" -> Res.string.ike_coding_seatbelt_warning
            "ike_coding_sia4_active" -> Res.string.ike_coding_sia4_active
            "ike_coding_sia4_distance" -> Res.string.ike_coding_sia4_distance
            "ike_coding_sia_display_parameter" -> Res.string.ike_coding_sia_display_parameter
            "ike_coding_sia_liter_limit" -> Res.string.ike_coding_sia_liter_limit
            "ike_coding_sia_time_limit" -> Res.string.ike_coding_sia_time_limit
            "ike_coding_speed_limit_units" -> Res.string.ike_coding_speed_limit_units
            "ike_coding_tank_reserve_threshold" -> Res.string.ike_coding_tank_reserve_threshold
            "ike_coding_tank_up_down_threshold" -> Res.string.ike_coding_tank_up_down_threshold
            "ike_coding_temperature_units" -> Res.string.ike_coding_temperature_units
            "ike_coding_time_format" -> Res.string.ike_coding_time_format
            "ike_coding_timer1_format" -> Res.string.ike_coding_timer1_format
            "ike_coding_timer2_format" -> Res.string.ike_coding_timer2_format
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
