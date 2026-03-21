package pl.misek.toafmobile.modules.containers

import pl.misek.toafmobile.modules.enums.TemperatureGaugeEnum

class TemperatureGaugeContainer(
    var temperatureGauge: TemperatureGaugeEnum,
    var temperatureValue: Int,
    var positionX: Int,
    var positionY: Int
)
