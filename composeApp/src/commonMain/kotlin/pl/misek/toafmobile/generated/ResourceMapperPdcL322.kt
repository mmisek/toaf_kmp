package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperPdcL322 {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "pdc_l322_coding_activation_delay" -> Res.string.pdc_l322_coding_activation_delay
            "pdc_l322_coding_active_on_ign" -> Res.string.pdc_l322_coding_active_on_ign
            "pdc_l322_coding_audible_switch_off" -> Res.string.pdc_l322_coding_audible_switch_off
            "pdc_l322_coding_audible_switch_on_always" -> Res.string.pdc_l322_coding_audible_switch_on_always
            "pdc_l322_coding_audible_switch_on_first" -> Res.string.pdc_l322_coding_audible_switch_on_first
            "pdc_l322_coding_deactivation" -> Res.string.pdc_l322_coding_deactivation
            "pdc_l322_coding_emergency_signal" -> Res.string.pdc_l322_coding_emergency_signal
            "pdc_l322_coding_front_sounder" -> Res.string.pdc_l322_coding_front_sounder
            "pdc_l322_coding_front_volume" -> Res.string.pdc_l322_coding_front_volume
            "pdc_l322_coding_ice_suppression" -> Res.string.pdc_l322_coding_ice_suppression
            "pdc_l322_coding_max_detect_area" -> Res.string.pdc_l322_coding_max_detect_area
            "pdc_l322_coding_not_kl15_powered" -> Res.string.pdc_l322_coding_not_kl15_powered
            "pdc_l322_coding_num_sensors" -> Res.string.pdc_l322_coding_num_sensors
            "pdc_l322_coding_offsets" -> Res.string.pdc_l322_coding_offsets
            "pdc_l322_coding_one_sounder" -> Res.string.pdc_l322_coding_one_sounder
            "pdc_l322_coding_pdc_switch" -> Res.string.pdc_l322_coding_pdc_switch
            "pdc_l322_coding_permanent_tones" -> Res.string.pdc_l322_coding_permanent_tones
            "pdc_l322_coding_range_trunk_centre" -> Res.string.pdc_l322_coding_range_trunk_centre
            "pdc_l322_coding_range_trunk_corner" -> Res.string.pdc_l322_coding_range_trunk_corner
            "pdc_l322_coding_rear_sounder" -> Res.string.pdc_l322_coding_rear_sounder
            "pdc_l322_coding_rear_volume" -> Res.string.pdc_l322_coding_rear_volume
            "pdc_l322_coding_reversing_signal" -> Res.string.pdc_l322_coding_reversing_signal
            "pdc_l322_coding_sensor_spacing" -> Res.string.pdc_l322_coding_sensor_spacing
            "pdc_l322_coding_signal_by_switch" -> Res.string.pdc_l322_coding_signal_by_switch
            "pdc_l322_coding_speed_signal" -> Res.string.pdc_l322_coding_speed_signal
            "pdc_l322_coding_speed_switch_off" -> Res.string.pdc_l322_coding_speed_switch_off
            "pdc_l322_coding_speed_switch_on" -> Res.string.pdc_l322_coding_speed_switch_on
            "pdc_l322_coding_switch_function" -> Res.string.pdc_l322_coding_switch_function
            "pdc_l322_coding_switch_off_distance" -> Res.string.pdc_l322_coding_switch_off_distance
            "pdc_l322_coding_tball_present_dist" -> Res.string.pdc_l322_coding_tball_present_dist
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
