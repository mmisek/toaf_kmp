package pl.misek.toafmobile.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.livedata.LiveDataFormat
import pl.misek.toafmobile.livedata.LiveDataResultElement
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.emptyList

class LiveDateViewModel(
    val module: IModule,
    private val logFolder : File
) : ViewModel()
{
    private val tag = "LiveDateViewModel"
    private var liveDataElements : List<LiveDataElement> = emptyList()

    private val _liveDataResults = MutableStateFlow<List<LiveDataResultElement>>(emptyList())
    val liveDataResults = _liveDataResults.asStateFlow()

    private var _isActive = MutableStateFlow(false)
    val isActive = _isActive.asStateFlow()

    private var _saveFileEvent = Channel<File> (Channel.BUFFERED)
    val saveFileEvent = _saveFileEvent.receiveAsFlow()

    private var _liveDataChartResults = MutableStateFlow<List<MutableList<Float>>>(emptyList())
    val liveDataCharResult = _liveDataChartResults.asStateFlow()

    private var liveDataJob : Job? = null
    private val liveDataModule = module as IModuleLiveData

    fun startLiveData()
    {
        if (liveDataJob?.isActive == true) return

        if (liveDataElements.isNotEmpty())
        {
            _isActive.value = true

            liveDataJob = viewModelScope.launch(Dispatchers.IO)
            {
                _liveDataResults.value = emptyList()
                _liveDataChartResults.value = emptyList()
                val sdf = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault())
                val fileName = "LiveDataLog ${sdf.format(Date())}.csv"
                val header = liveDataElements.joinToString(separator = ";", postfix = "\n"){ "${it.description} [${it.unit}]"}
                if (!logFolder.exists())
                    logFolder.mkdirs()
                val file = File(logFolder,fileName)
                var resultsAvailable = false
                file.bufferedWriter().use {writer ->
                    writer.write(header)
                    val startResult = liveDataModule.startLiveDataRead(liveDataElements = liveDataElements)
                    Logger.i {"$tag: LiveData start result - $startResult"}
                    while (isActive.value) {
                        val oldChartResult = _liveDataChartResults.value
                        val newChartResult : MutableList<MutableList<Float>> = if (oldChartResult.isNotEmpty())
                            oldChartResult.map { it.toMutableList() }.toMutableList()
                        else
                            MutableList(liveDataElements.size) {mutableListOf()}
                        val result = liveDataModule.readLiveData()
                        if (result != null) {
                            val calculatedResults = calculateLiveData(result)
                            if (!resultsAvailable && calculatedResults.isNotEmpty())
                                resultsAvailable = true
                            for (i in calculatedResults.indices)
                                newChartResult[i].add(calculatedResults[i].rawValue)
                            _liveDataResults.value = calculatedResults
                            _liveDataChartResults.value = newChartResult
                            val cvsLine = liveDataResults.value.joinToString(
                                separator = ";",
                                postfix = "\n"
                            ) { "${it.valueWithoutUnit}" }
                            writer.write(cvsLine)
                        }
                        delay(5)
                    }
                    if (resultsAvailable)
                        _saveFileEvent.send(file)
                }
            }
        }
    }

    private fun calculateLiveData(liveData : Map<Int, Any>?) : List<LiveDataResultElement>
    {
        val result = mutableListOf<LiveDataResultElement>()

        for (liveDataElement in liveDataElements)
        {
            var staticValue : StringResource? = null
            var valueWithoutUnit = ""
            var formatedValue = ""
            var rawValue  = 0f
            if (liveData == null) break
            val block = liveData[liveDataElement.block]
            val value = liveDataModule.getLiveDataRawValue(liveData, liveDataElement)
            val tooShort = liveDataElement.liveDataFormat == LiveDataFormat.Standard && value == null
            // Brak bloku (blad odczytu) lub za krotka odpowiedz - pusta pozycja,
            // kolejne elementy nadal liczone, indeksy wynikow/wykresu bez przesuniec
            if (block == null || tooShort) {
                result.add(LiveDataResultElement(
                    name = liveDataElement.name,
                    rawValue = 0f,
                    valueWithoutUnit = "",
                    valueWithUnit = "",
                    description = liveDataElement.descriptionResKey,
                    staticValue = null))
                continue
            }
            if (liveDataElement.liveDataFormat == LiveDataFormat.Standard)
            {
                if (liveDataElement.values == null) {
                    rawValue = liveDataModule.getLiveDataValue(liveData, liveDataElement).toFloat()
                    valueWithoutUnit  = String.format(if (liveDataElement.unit != " ") "%.2f" else "%.0f", rawValue)
                    formatedValue = if (liveDataElement.unit != " ") valueWithoutUnit + " ${liveDataElement.unit}" else valueWithoutUnit
                } else {
                    liveDataElement.values
                        ?.find { it.first.toLong() == value }
                        .let { staticValue = it?.second }
                }
            }
            else if (liveDataElement.liveDataFormat == LiveDataFormat.String)
            {
                formatedValue = liveData[liveDataElement.block].toString()
            }
            val liveDataResult = LiveDataResultElement(
                name = liveDataElement.name,
                rawValue = rawValue,
                valueWithoutUnit = valueWithoutUnit,
                valueWithUnit = formatedValue,
                description =   liveDataElement.descriptionResKey,
                staticValue = staticValue)
            result.add(liveDataResult)
        }
        return result
    }

    fun stopLiveData()
    {
        _isActive.value = false
        viewModelScope.launch {
            liveDataJob?.join()
            liveDataJob = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveData()
    }

    fun setLiveDataElements(elements : List<LiveDataElement>)
    {
        liveDataElements = elements
    }

}