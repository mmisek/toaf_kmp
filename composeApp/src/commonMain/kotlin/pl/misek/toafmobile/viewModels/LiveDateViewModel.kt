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
import java.nio.ByteBuffer
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
            var mask = liveDataElement.mask
            val block = liveData?.get(liveDataElement.block) ?: break
            val byteInBlock = liveDataElement.byteInBlock
            if (liveDataElement.liveDataFormat == LiveDataFormat.Standard)
            {
                var tempByteValue = (block as ByteArray).copyOfRange(
                    byteInBlock,
                    byteInBlock + calculateLengthFromMask(mask)
                )
                if (liveDataElement.swap)
                    tempByteValue = tempByteValue.reversed().toByteArray()

                var tempValue = if (liveDataElement.u2) {
                    val paddledBytes = ByteArray(8)
                    tempByteValue.copyInto(paddledBytes, destinationOffset =  8 - tempByteValue.size)
                    ByteBuffer.wrap(paddledBytes).getLong()
                }
                else
                    convertToLong(tempByteValue)
                tempValue = tempValue and mask

                while ((mask and 0x01L) != 0x01L) {
                    mask = mask shr 0x01
                    tempValue = tempValue shr 0x01
                }
                // Wartosci ze znakiem - szerokosc pola wg maski
                if (liveDataElement.signFrom >= 0) {
                    val bits = 64 - mask.countLeadingZeroBits()
                    if (bits in 1..63 && tempValue >= liveDataElement.signFrom)
                        tempValue -= (1L shl bits)
                }
                else if (liveDataElement.u2 || liveDataElement.signOffset) {
                    val bits = 64 - mask.countLeadingZeroBits()
                    if (bits in 1..63 && ((tempValue shr (bits - 1)) and 0x01L) == 0x01L) {
                        tempValue = if (liveDataElement.signOffset)
                            ((1L shl (bits - 1)) - 1) - tempValue
                        else
                            tempValue - (1L shl bits)
                    }
                }
                if (liveDataElement.values == null) {
                    rawValue = liveDataElement.factorA * (if (liveDataElement.invertA) 1 / tempValue.toFloat() else tempValue.toFloat()) + liveDataElement.factorB
                    valueWithoutUnit  = String.format(if (liveDataElement.unit != " ") "%.2f" else "%.0f", rawValue)
                    formatedValue = if (liveDataElement.unit != " ") valueWithoutUnit + " ${liveDataElement.unit}" else valueWithoutUnit
                } else {
                    liveDataElement.values
                        ?.find { it.first.toLong() == tempValue }
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

    private fun calculateLengthFromMask(value :Long) : Int{
        var count = 0
        var temp = value

        while (temp > 0) {
            count++
            temp /= 0x100
        }
        return count
        //return if (count % 2 == 0) count - 1 else count
    }

    private fun convertToLong(byteArray: ByteArray): Long {
        var value = 0L
        val size = byteArray.size
        for ((i, byte) in byteArray.withIndex()) {
            val shift = 8 * (size - 1 - i)
            value += (byte.toLong() and 0xFF) shl shift
        }
        return value
    }
}