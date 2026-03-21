package pl.misek.toafmobile.generated

import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperRadio {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "radio_coding_country" -> Res.string.radio_coding_country
            "radio_coding_gal" -> Res.string.radio_coding_gal
            "radio_coding_key_memory_radio" -> Res.string.radio_coding_key_memory_radio
            "radio_coding_pty_table" -> Res.string.radio_coding_pty_table
            "radio_coding_rds_af_mode" -> Res.string.radio_coding_rds_af_mode
            "radio_control_audio_key" -> Res.string.radio_control_audio_key
            "radio_control_default_volume" -> Res.string.radio_control_default_volume
            "radio_control_frequency" -> Res.string.radio_control_frequency
            "radio_control_gal_dec" -> Res.string.radio_control_gal_dec
            "radio_control_gal_inc" -> Res.string.radio_control_gal_inc
            "radio_control_seek_down" -> Res.string.radio_control_seek_down
            "radio_control_seek_up" -> Res.string.radio_control_seek_up
            "radio_control_speaker_left_front" -> Res.string.radio_control_speaker_left_front
            "radio_control_speaker_left_rear" -> Res.string.radio_control_speaker_left_rear
            "radio_control_speaker_right_front" -> Res.string.radio_control_speaker_right_front
            "radio_control_speaker_right_rear" -> Res.string.radio_control_speaker_right_rear
            "radio_control_switch_seek" -> Res.string.radio_control_switch_seek
            "radio_control_vf_dec" -> Res.string.radio_control_vf_dec
            "radio_control_vf_inc" -> Res.string.radio_control_vf_inc
            "radio_control_volume_down" -> Res.string.radio_control_volume_down
            "radio_control_volume_up" -> Res.string.radio_control_volume_up
            "radio_error_00" -> Res.string.radio_error_00
            "radio_error_01" -> Res.string.radio_error_01
            "radio_error_02" -> Res.string.radio_error_02
            "radio_error_03" -> Res.string.radio_error_03
            "radio_error_04" -> Res.string.radio_error_04
            "radio_error_05" -> Res.string.radio_error_05
            "radio_error_not_present" -> Res.string.radio_error_not_present
            "radio_error_present" -> Res.string.radio_error_present
            "radio_live_data_value_line_is_open" -> Res.string.radio_live_data_value_line_is_open
            "radio_live_data_value_short" -> Res.string.radio_live_data_value_short
            "radio_live_data_value_signal_too_high" -> Res.string.radio_live_data_value_signal_too_high
            "radio_live_data_value_signal_too_low" -> Res.string.radio_live_data_value_signal_too_low
            "radio_module_info_name" -> Res.string.radio_module_info_name
        else -> {
          println("NO RESOURCE for : $resourceName")
            null
        }
     }
   }
}
