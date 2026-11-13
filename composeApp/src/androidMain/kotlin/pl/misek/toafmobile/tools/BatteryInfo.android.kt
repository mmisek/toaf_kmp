package pl.misek.toafmobile.tools

import android.content.Context
import co.touchlab.kermit.Logger
import io.techie.volta.VoltaFactory
import io.techie.volta.VoltaSensorState
import io.techie.volta.enums.ChargingSource
import io.techie.volta.enums.ChargingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext

private const val tag = "BatteryInfo"
private const val READ_TIMEOUT = 2000L

actual suspend fun readBatteryInfo(): BatteryReadResult {
    val context = try {
        GlobalContext.get().get<Context>()
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Logger.e { "$tag [readBatteryInfo], brak kontekstu, $ex" }
        return BatteryReadResult.Failed
    }

    VoltaFactory.initialize(context)
    val volta = VoltaFactory.create()
    return try {
        volta.startMonitoring()
        // AndroidBatteryStateProvider odczytuje sticky ACTION_BATTERY_CHANGED od razu przy
        // starcie, wiec pierwszy stan z poziomem naladowania przychodzi praktycznie natychmiast.
        val state = withTimeoutOrNull(READ_TIMEOUT) {
            volta.batteryState.first { it is VoltaSensorState.Available && it.data.level != null }
        }
        val battery = when (state) {
            is VoltaSensorState.Available -> state.data
            else -> null
        }
        val level = battery?.level
        if (battery == null || level == null) {
            Logger.w { "$tag [readBatteryInfo], brak stanu baterii w zadanym czasie" }
            BatteryReadResult.Failed
        } else {
            BatteryReadResult.Ok(
                BatteryInfo(
                    level = level,
                    isCharging = battery.chargingStatus == ChargingStatus.CHARGING,
                    isPlugged = battery.chargingSource != ChargingSource.UNKNOWN ||
                        battery.chargingStatus == ChargingStatus.FULL,
                )
            )
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Logger.e { "$tag [readBatteryInfo], $ex" }
        BatteryReadResult.Failed
    } finally {
        volta.stopMonitoring()
    }
}
