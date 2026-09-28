package pl.misek.toafmobile.modules.interfaces

import pl.misek.toafmobile.modules.enums.TemperatureGaugeEnum

interface ITemperatureCoding {
    suspend fun readTemperatureGauge(): Map<TemperatureGaugeEnum, Int>?
    suspend fun writeTemperatureGauge(values: Map<TemperatureGaugeEnum, Int>): Boolean
}
