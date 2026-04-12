package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperAtc {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "atc_control_ac_compressor_off" -> Res.string.atc_control_ac_compressor_off
            "atc_control_ac_compressor_on" -> Res.string.atc_control_ac_compressor_on
            "atc_control_blower_0" -> Res.string.atc_control_blower_0
            "atc_control_blower_100" -> Res.string.atc_control_blower_100
            "atc_control_blower_15" -> Res.string.atc_control_blower_15
            "atc_control_blower_25" -> Res.string.atc_control_blower_25
            "atc_control_blower_50" -> Res.string.atc_control_blower_50
            "atc_control_blower_75" -> Res.string.atc_control_blower_75
            "atc_control_blower_85" -> Res.string.atc_control_blower_85
            "atc_control_flap_defrost" -> Res.string.atc_control_flap_defrost
            "atc_control_flap_face" -> Res.string.atc_control_flap_face
            "atc_control_fresh_air" -> Res.string.atc_control_fresh_air
            "atc_control_hrw_off" -> Res.string.atc_control_hrw_off
            "atc_control_hrw_on" -> Res.string.atc_control_hrw_on
            "atc_control_left_blend_flap_cold" -> Res.string.atc_control_left_blend_flap_cold
            "atc_control_left_blend_flap_hot" -> Res.string.atc_control_left_blend_flap_hot
            "atc_control_recirculation" -> Res.string.atc_control_recirculation
            "atc_control_right_blend_flap_cold" -> Res.string.atc_control_right_blend_flap_cold
            "atc_control_right_blend_flap_hot" -> Res.string.atc_control_right_blend_flap_hot
            "atc_error_11" -> Res.string.atc_error_11
            "atc_error_12" -> Res.string.atc_error_12
            "atc_error_13" -> Res.string.atc_error_13
            "atc_error_14" -> Res.string.atc_error_14
            "atc_error_21" -> Res.string.atc_error_21
            "atc_error_22" -> Res.string.atc_error_22
            "atc_error_31" -> Res.string.atc_error_31
            "atc_error_32" -> Res.string.atc_error_32
            "atc_error_33" -> Res.string.atc_error_33
            "atc_live_data_ac_compressor" -> Res.string.atc_live_data_ac_compressor
            "atc_live_data_actual_air_distribution" -> Res.string.atc_live_data_actual_air_distribution
            "atc_live_data_ambient_temp" -> Res.string.atc_live_data_ambient_temp
            "atc_live_data_atc_system" -> Res.string.atc_live_data_atc_system
            "atc_live_data_blower_speed" -> Res.string.atc_live_data_blower_speed
            "atc_live_data_car_speed" -> Res.string.atc_live_data_car_speed
            "atc_live_data_driver_required_blend" -> Res.string.atc_live_data_driver_required_blend
            "atc_live_data_driver_required_temp" -> Res.string.atc_live_data_driver_required_temp
            "atc_live_data_driver_target_temp" -> Res.string.atc_live_data_driver_target_temp
            "atc_live_data_evaporator_temp" -> Res.string.atc_live_data_evaporator_temp
            "atc_live_data_hand_drive" -> Res.string.atc_live_data_hand_drive
            "atc_live_data_in_car_temp" -> Res.string.atc_live_data_in_car_temp
            "atc_live_data_left_air_blend_pot" -> Res.string.atc_live_data_left_air_blend_pot
            "atc_live_data_left_side_temp" -> Res.string.atc_live_data_left_side_temp
            "atc_live_data_left_solar" -> Res.string.atc_live_data_left_solar
            "atc_live_data_passenger_required_blend" -> Res.string.atc_live_data_passenger_required_blend
            "atc_live_data_passenger_required_temp" -> Res.string.atc_live_data_passenger_required_temp
            "atc_live_data_passenger_target_blend" -> Res.string.atc_live_data_passenger_target_blend
            "atc_live_data_passenger_target_temp" -> Res.string.atc_live_data_passenger_target_temp
            "atc_live_data_recirculation" -> Res.string.atc_live_data_recirculation
            "atc_live_data_right_air_blend_pot" -> Res.string.atc_live_data_right_air_blend_pot
            "atc_live_data_right_side_temp" -> Res.string.atc_live_data_right_side_temp
            "atc_live_data_right_solar" -> Res.string.atc_live_data_right_solar
            "atc_live_data_target_air_dist" -> Res.string.atc_live_data_target_air_dist
            "atc_live_data_value_fresh" -> Res.string.atc_live_data_value_fresh
            "atc_live_data_value_invalid" -> Res.string.atc_live_data_value_invalid
            "atc_live_data_value_left_hand" -> Res.string.atc_live_data_value_left_hand
            "atc_live_data_value_off" -> Res.string.atc_live_data_value_off
            "atc_live_data_value_on" -> Res.string.atc_live_data_value_on
            "atc_live_data_value_recirculation" -> Res.string.atc_live_data_value_recirculation
            "atc_live_data_value_right_hand" -> Res.string.atc_live_data_value_right_hand
            "atc_live_data_water_temp" -> Res.string.atc_live_data_water_temp
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
