package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperVideo {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "video_control_cassette_dolby_b" -> Res.string.video_control_cassette_dolby_b
            "video_control_cassette_dolby_c" -> Res.string.video_control_cassette_dolby_c
            "video_control_cassette_dolby_off" -> Res.string.video_control_cassette_dolby_off
            "video_control_cassette_eject" -> Res.string.video_control_cassette_eject
            "video_control_cassette_ffw" -> Res.string.video_control_cassette_ffw
            "video_control_cassette_frw" -> Res.string.video_control_cassette_frw
            "video_control_cassette_mss_fw" -> Res.string.video_control_cassette_mss_fw
            "video_control_cassette_mss_rw" -> Res.string.video_control_cassette_mss_rw
            "video_control_cassette_normal" -> Res.string.video_control_cassette_normal
            "video_control_cassette_pause_off" -> Res.string.video_control_cassette_pause_off
            "video_control_cassette_pause_on" -> Res.string.video_control_cassette_pause_on
            "video_control_cassette_play" -> Res.string.video_control_cassette_play
            "video_control_cassette_reverse" -> Res.string.video_control_cassette_reverse
            "video_control_cassette_standby" -> Res.string.video_control_cassette_standby
            "video_control_monitor_off" -> Res.string.video_control_monitor_off
            "video_control_monitor_on" -> Res.string.video_control_monitor_on
            "video_control_mute_off" -> Res.string.video_control_mute_off
            "video_control_mute_on" -> Res.string.video_control_mute_on
            "video_control_normal_mode_on" -> Res.string.video_control_normal_mode_on
            "video_control_production_mode_on" -> Res.string.video_control_production_mode_on
            "video_control_sleep_mode" -> Res.string.video_control_sleep_mode
            "video_control_transport_mode_on" -> Res.string.video_control_transport_mode_on
            "video_control_workshop_mode_on" -> Res.string.video_control_workshop_mode_on
            "video_error_01" -> Res.string.video_error_01
            "video_error_02" -> Res.string.video_error_02
            "video_error_03" -> Res.string.video_error_03
            "video_error_04" -> Res.string.video_error_04
            "video_error_05" -> Res.string.video_error_05
            "video_error_06" -> Res.string.video_error_06
            "video_error_07" -> Res.string.video_error_07
            "video_error_08" -> Res.string.video_error_08
            "video_error_09" -> Res.string.video_error_09
            "video_error_0a" -> Res.string.video_error_0A
            "video_error_0b" -> Res.string.video_error_0B
            "video_error_0c" -> Res.string.video_error_0C
            "video_error_0d" -> Res.string.video_error_0D
            "video_error_81" -> Res.string.video_error_81
            "video_error_82" -> Res.string.video_error_82
            "video_error_83" -> Res.string.video_error_83
            "video_error_not_present" -> Res.string.video_error_not_present
            "video_error_present" -> Res.string.video_error_present
            "video_live_data_af_fond" -> Res.string.video_live_data_af_fond
            "video_live_data_af_nav" -> Res.string.video_live_data_af_nav
            "video_live_data_af_radio" -> Res.string.video_live_data_af_radio
            "video_live_data_af_vcr" -> Res.string.video_live_data_af_vcr
            "video_live_data_blue_in" -> Res.string.video_live_data_blue_in
            "video_live_data_blue_out" -> Res.string.video_live_data_blue_out
            "video_live_data_cvbs_cam" -> Res.string.video_live_data_cvbs_cam
            "video_live_data_cvbs_fond" -> Res.string.video_live_data_cvbs_fond
            "video_live_data_cvbs_tuner" -> Res.string.video_live_data_cvbs_tuner
            "video_live_data_cvbs_vcr" -> Res.string.video_live_data_cvbs_vcr
            "video_live_data_green_in" -> Res.string.video_live_data_green_in
            "video_live_data_green_out" -> Res.string.video_live_data_green_out
            "video_live_data_red_in" -> Res.string.video_live_data_red_in
            "video_live_data_red_out" -> Res.string.video_live_data_red_out
            "video_live_data_value_line_is_open" -> Res.string.video_live_data_value_line_is_open
            "video_live_data_value_short" -> Res.string.video_live_data_value_short
            "video_live_data_value_signal_too_high" -> Res.string.video_live_data_value_signal_too_high
            "video_live_data_value_signal_too_low" -> Res.string.video_live_data_value_signal_too_low
            "video_live_data_x_sync" -> Res.string.video_live_data_x_sync
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
