package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperHevac {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "hevac_error_00" -> Res.string.hevac_error_00
            "hevac_error_01" -> Res.string.hevac_error_01
            "hevac_error_02" -> Res.string.hevac_error_02
            "hevac_error_03" -> Res.string.hevac_error_03
            "hevac_error_04" -> Res.string.hevac_error_04
            "hevac_error_05" -> Res.string.hevac_error_05
            "hevac_error_06" -> Res.string.hevac_error_06
            "hevac_error_0b" -> Res.string.hevac_error_0B
            "hevac_error_0d" -> Res.string.hevac_error_0D
            "hevac_error_12" -> Res.string.hevac_error_12
            "hevac_error_17" -> Res.string.hevac_error_17
            "hevac_error_18" -> Res.string.hevac_error_18
            "hevac_error_19" -> Res.string.hevac_error_19
            "hevac_error_1a" -> Res.string.hevac_error_1A
            "hevac_error_1b" -> Res.string.hevac_error_1B
            "hevac_error_1c" -> Res.string.hevac_error_1C
            "hevac_error_1d" -> Res.string.hevac_error_1D
            "hevac_error_1e" -> Res.string.hevac_error_1E
            "hevac_error_1f" -> Res.string.hevac_error_1F
            "hevac_error_20" -> Res.string.hevac_error_20
            "hevac_error_21" -> Res.string.hevac_error_21
            "hevac_error_ff" -> Res.string.hevac_error_FF
            "hevac_error_detail_01" -> Res.string.hevac_error_detail_01
            "hevac_error_detail_02" -> Res.string.hevac_error_detail_02
            "hevac_error_detail_04" -> Res.string.hevac_error_detail_04
            "hevac_error_detail_08" -> Res.string.hevac_error_detail_08
            "hevac_error_detail_40" -> Res.string.hevac_error_detail_40
            "hevac_error_detail_80" -> Res.string.hevac_error_detail_80
            "hevac_live_data_evaporator_temperature_sensor" -> Res.string.hevac_live_data_evaporator_temperature_sensor
            "hevac_live_data_heat_exchanger_sensor" -> Res.string.hevac_live_data_heat_exchanger_sensor
            "hevac_live_data_outdoor_temperature_sensor" -> Res.string.hevac_live_data_outdoor_temperature_sensor
            "hevac_live_data_pollution_sensor_heating_voltage" -> Res.string.hevac_live_data_pollution_sensor_heating_voltage
            "hevac_live_data_pollution_sensor_signal" -> Res.string.hevac_live_data_pollution_sensor_signal
            "hevac_live_data_power_supply" -> Res.string.hevac_live_data_power_supply
            "hevac_live_data_power_supply_pollution_sensor" -> Res.string.hevac_live_data_power_supply_pollution_sensor
            "hevac_live_data_pressure_sensor" -> Res.string.hevac_live_data_pressure_sensor
            "hevac_live_data_pwm_fan_control_voltage" -> Res.string.hevac_live_data_pwm_fan_control_voltage
            "hevac_live_data_solar_sensor" -> Res.string.hevac_live_data_solar_sensor
        else -> {
          Logger.e {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
