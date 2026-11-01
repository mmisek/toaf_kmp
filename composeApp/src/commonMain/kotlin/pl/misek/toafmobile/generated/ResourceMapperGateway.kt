package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperGateway {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "gateway_error_1480" -> Res.string.gateway_error_1480
            "gateway_error_1520" -> Res.string.gateway_error_1520
            "gateway_error_1623" -> Res.string.gateway_error_1623
            "gateway_error_1670" -> Res.string.gateway_error_1670
            "gateway_error_1671" -> Res.string.gateway_error_1671
            "gateway_error_1672" -> Res.string.gateway_error_1672
            "gateway_error_1673" -> Res.string.gateway_error_1673
            "gateway_error_1675" -> Res.string.gateway_error_1675
            "gateway_error_1676" -> Res.string.gateway_error_1676
            "gateway_error_1677" -> Res.string.gateway_error_1677
            "gateway_error_1678" -> Res.string.gateway_error_1678
            "gateway_error_1679" -> Res.string.gateway_error_1679
            "gateway_live_data_coolant_temperature" -> Res.string.gateway_live_data_coolant_temperature
            "gateway_live_data_engine_speed" -> Res.string.gateway_live_data_engine_speed
            "gateway_live_data_fuel_level" -> Res.string.gateway_live_data_fuel_level
            "gateway_live_data_ignition" -> Res.string.gateway_live_data_ignition
            "gateway_live_data_immobiliser" -> Res.string.gateway_live_data_immobiliser
            "gateway_live_data_oil_pressure" -> Res.string.gateway_live_data_oil_pressure
            "gateway_live_data_value_low" -> Res.string.gateway_live_data_value_low
            "gateway_live_data_value_normal" -> Res.string.gateway_live_data_value_normal
            "gateway_live_data_value_off" -> Res.string.gateway_live_data_value_off
            "gateway_live_data_value_on" -> Res.string.gateway_live_data_value_on
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
