package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperMfl {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "mfl_error_01" -> Res.string.mfl_error_01
            "mfl_error_02" -> Res.string.mfl_error_02
            "mfl_error_03" -> Res.string.mfl_error_03
            "mfl_error_07" -> Res.string.mfl_error_07
            "mfl_error_0a" -> Res.string.mfl_error_0A
            "mfl_error_active" -> Res.string.mfl_error_active
            "mfl_error_not_active" -> Res.string.mfl_error_not_active
            "mfl_live_data_air_circulating_control_present" -> Res.string.mfl_live_data_air_circulating_control_present
            "mfl_live_data_button_air_recirculation_pressed" -> Res.string.mfl_live_data_button_air_recirculation_pressed
            "mfl_live_data_button_cruise_control_off_pressed" -> Res.string.mfl_live_data_button_cruise_control_off_pressed
            "mfl_live_data_button_radio_telephone_pressed" -> Res.string.mfl_live_data_button_radio_telephone_pressed
            "mfl_live_data_button_resume_pressed" -> Res.string.mfl_live_data_button_resume_pressed
            "mfl_live_data_button_seek_down_pressed" -> Res.string.mfl_live_data_button_seek_down_pressed
            "mfl_live_data_button_seek_up_pressed" -> Res.string.mfl_live_data_button_seek_up_pressed
            "mfl_live_data_button_set_minus_pressed" -> Res.string.mfl_live_data_button_set_minus_pressed
            "mfl_live_data_button_set_plus_pressed" -> Res.string.mfl_live_data_button_set_plus_pressed
            "mfl_live_data_button_telephone_pressed" -> Res.string.mfl_live_data_button_telephone_pressed
            "mfl_live_data_button_vol_down_pressed" -> Res.string.mfl_live_data_button_vol_down_pressed
            "mfl_live_data_button_vol_up_pressed" -> Res.string.mfl_live_data_button_vol_up_pressed
            "mfl_live_data_cruise_control_present" -> Res.string.mfl_live_data_cruise_control_present
            "mfl_live_data_operating_voltage" -> Res.string.mfl_live_data_operating_voltage
            "mfl_live_data_operating_voltage_at_the_switch" -> Res.string.mfl_live_data_operating_voltage_at_the_switch
            "mfl_live_data_radio_control_present" -> Res.string.mfl_live_data_radio_control_present
            "mfl_live_data_telephone_control_present" -> Res.string.mfl_live_data_telephone_control_present
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
