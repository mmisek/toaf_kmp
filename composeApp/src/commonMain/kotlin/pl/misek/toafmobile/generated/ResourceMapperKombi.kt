package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperKombi {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "kombi_coding_aircon" -> Res.string.kombi_coding_aircon
            "kombi_coding_brake_system_input" -> Res.string.kombi_coding_brake_system_input
            "kombi_coding_odo_offset" -> Res.string.kombi_coding_odo_offset
            "kombi_coding_odometer_leading_zeros" -> Res.string.kombi_coding_odometer_leading_zeros
            "kombi_coding_overspeed_warning" -> Res.string.kombi_coding_overspeed_warning
            "kombi_coding_sia_distance_service" -> Res.string.kombi_coding_sia_distance_service
            "kombi_coding_sia_interval_distance" -> Res.string.kombi_coding_sia_interval_distance
            "kombi_coding_sia_liter_limit" -> Res.string.kombi_coding_sia_liter_limit
            "kombi_coding_sia_time_limit" -> Res.string.kombi_coding_sia_time_limit
            "kombi_coding_sia_time_service" -> Res.string.kombi_coding_sia_time_service
            "kombi_error_status_present_not_stored" -> Res.string.kombi_error_status_present_not_stored
            "kombi_error_status_present_stored" -> Res.string.kombi_error_status_present_stored
            "kombi_error_status_stored_not_present" -> Res.string.kombi_error_status_stored_not_present
            "kombi_error_status_test_not_complete" -> Res.string.kombi_error_status_test_not_complete
            "kombi_error_status_warning_lamp" -> Res.string.kombi_error_status_warning_lamp
            "module_description_kombi" -> Res.string.module_description_kombi
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
