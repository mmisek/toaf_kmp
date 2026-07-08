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
        while(true)
        {
            val liveData = module.readLiveData()
            val value = try {
                val block = liveData?.get(liveDataElement.block) as ByteArray
                val rawValue = (block[liveDataElement.byteInBlock].toInt() shl 8) + (block[liveDataElement.byteInBlock + 1].toInt())
                rawValue.toFloat() * liveDataElement.factorA + liveDataElement.factorB
            } catch (_ : Exception)
            {
                Float.NaN
            }
            emit(value)
        }
        delay(25.milliseconds)
    }.conflate()
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(1_000),
            initialValue = Float.NaN
        )

}