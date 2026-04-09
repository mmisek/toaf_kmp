package pl.misek.toafmobile.modules.enums

import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

enum class CarType(val value :Int, val labelRes : StringResource) {
    R40(0, Res.string.car_type_rover_75),
    RJO(1, Res.string.car_type_rover_25),
    L322(2, Res.string.car_type_range_rover_l322),
    L20(3, Res.string.car_type_land_rover_freelander_1);
}
