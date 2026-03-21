package pl.misek.toafmobile.generated

import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperLws {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
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
        else -> {
          println("NO RESOURCE for : $resourceName")
            null
        }
     }
   }
}
