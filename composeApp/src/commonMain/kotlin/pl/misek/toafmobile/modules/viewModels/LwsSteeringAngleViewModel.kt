package pl.misek.toafmobile.modules.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import pl.misek.toafmobile.modules.l322.ModuleLWS
import pl.misek.toafmobile.tools.JsonSupport
import kotlin.time.Duration.Companion.milliseconds

class LwsSteeringAngleViewModel(private val module : ModuleLWS?): ViewModel()
{
    val angle : StateFlow<Float> = flow {
        if (module == null)
        {
            emit(Float.NaN)
            return@flow
        }

        val liveDataElements = JsonSupport.getLiveDataElements(module.getModuleName())
        val liveDataElement = liveDataElements?.firstOrNull {x->x.name == "steering_angle"}
        if (liveDataElement == null)
        {
            emit(Float.NaN)
            return@flow
        }
        // Bez tego readLiveData() nie zada zadnego bloku (iteruje po liveDataElements modulu) i zwraca null.
        module.startLiveDataRead(listOf(liveDataElement))
        while(true)
        {
            val liveData = module.readLiveData()
            val value = try {
                val block = liveData?.get(liveDataElement.block)
                val i = liveDataElement.byteInBlock
                if (block == null || block.size < i + 2) {
                    Float.NaN
                } else {
                    val raw = ((block[i].toInt() and 0xFF) shl 8) or (block[i + 1].toInt() and 0xFF)
                    // u2 = wartosc w kodzie uzupelnien do dwoch (kat moze byc ujemny)
                    val signed = if (liveDataElement.u2) raw.toShort().toInt() else raw
                    signed * liveDataElement.factorA + liveDataElement.factorB
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (_ : Exception)
            {
                Float.NaN
            }
            emit(value)
            delay(25.milliseconds)
        }
    }.conflate()
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(1_000),
            initialValue = Float.NaN
        )

}