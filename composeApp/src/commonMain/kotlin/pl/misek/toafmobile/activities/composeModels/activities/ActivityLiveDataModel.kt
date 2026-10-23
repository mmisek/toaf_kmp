package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.TopBarModel
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogQuestion
import pl.misek.toafmobile.activities.composeModels.menus.ToafVerticalScrollbar
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.livedata.LiveDataResultElement
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.tools.IFileSupport
import pl.misek.toafmobile.tools.JsonSupport.getLiveDataElements
import pl.misek.toafmobile.viewModels.LiveDateViewModel
import toafmobile.composeapp.generated.resources.AD_save_to_file_message
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.activity_live_data
import toafmobile.composeapp.generated.resources.activity_live_data_stop
import toafmobile.composeapp.generated.resources.bmbt_live_data_battery_voltage
import toafmobile.composeapp.generated.resources.button_live_data_start
import toafmobile.composeapp.generated.resources.cc_live_data_set_speed
import toafmobile.composeapp.generated.resources.live_data_type_chart
import toafmobile.composeapp.generated.resources.live_data_type_table
import java.io.File

private enum class LiveDataType
{
    Table,
    Chart
}
@Composable
fun ActivityLiveDataModel(moduleKoinName : KoinNames, onBackClick :() -> Unit)
 {
    val tag = "ActivityLiveDataModel"
    val modulesManage : ModulesManager = koinInject()
    val fileSupport : IFileSupport = koinInject()
    val currentModule = modulesManage.getCurrentModules().firstOrNull {it.getKoinName() == moduleKoinName} as IModule
    val isPreview = LocalInspectionMode.current
    val liveDataElements = remember { mutableStateListOf<LiveDataElement>()}
    val selectedLiveDataElements = remember { mutableStateListOf<LiveDataElement>() }
    val liveDataViewModel = koinViewModel<LiveDateViewModel>{parametersOf(currentModule)}
    var buttonsActive by remember { mutableStateOf(false) }
    val liveDataResults by liveDataViewModel.liveDataResults.collectAsState()
    val liveDataViewModelIsActive by liveDataViewModel.isActive.collectAsState()
    val liveDataChartResult by liveDataViewModel.liveDataCharResult.collectAsState()
    var pendingFileToSave by remember { mutableStateOf<File?>(null) }
    var fileToSave by remember { mutableStateOf<File?>(null) }
    var saveToFileDialogQuestion by remember { mutableStateOf(false) }
    var liveDataType by remember {mutableStateOf(LiveDataType.Table) }
    val livaDataTypeChartText = Res.string.live_data_type_chart
    val liveDataTypeTableText = Res.string.live_data_type_table
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        liveDataElements.clear()
        if (isPreview)
        {
            liveDataElements.addAll(getFakeLiveDataElements())
        }
        else {
            withContext(Dispatchers.IO)
            {
                liveDataElements.clear()
                val elements = getLiveDataElements(
                    currentModule.getLiveDataName())
                elements?.forEach { liveDataElements.add(it) }
            }
        }
        liveDataViewModel.saveFileEvent.collect { file ->
            saveToFileDialogQuestion = true
            pendingFileToSave = file
        }
    }

    LaunchedEffect(selectedLiveDataElements.count()) {
        buttonsActive = selectedLiveDataElements.count() > 0
    }

    Scaffold(
        modifier = Modifier
            .padding(all = 0.dp)
            .fillMaxWidth()
            .fillMaxHeight(),
        containerColor = ToafColors.toafBackground,
        topBar = {
            TopBarModel(
                subTitleText = currentModule.getModuleName(),
                titleResource = Res.string.activity_live_data,
                onBackClicked = { onBackClick() })
        },
        content = { paddingValues ->
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .background(ToafColors.toafBackground))
            {
                fileSupport.SaveFileHandler(
                    file = fileToSave,
                    onSaveComplete = {
                        fileToSave = null
                        pendingFileToSave = null
                    }
                )
                ToafButtons.GreenButton(
                    buttonText = if (!liveDataViewModelIsActive) Res.string.button_live_data_start else Res.string.activity_live_data_stop,
                    onClick = {
                        switchLiveDataState(liveDataViewModelIsActive, liveDataViewModel, selectedLiveDataElements)
                        },
                    modifier = Modifier.padding(2.dp).fillMaxWidth(),
                    enabled = buttonsActive
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.padding(8.dp).fillMaxWidth())
                {
                    LiveDataType.entries.forEachIndexed { index, type ->
                        val label = when(type){
                           LiveDataType.Table -> liveDataTypeTableText
                           LiveDataType.Chart -> livaDataTypeChartText
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = LiveDataType.entries.size),
                            onClick = {liveDataType = type },
                            selected = liveDataType == type,
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = ToafColors.greenButton,
                                activeContentColor = Color.White,
                                inactiveContainerColor = ToafColors.disabledButton,
                                inactiveContentColor = Color.Black

                            )
                        ) {
                            Text(stringResource(label))
                        }
                    }
                }
                if (!liveDataViewModelIsActive) {

                    Box(modifier = Modifier.fillMaxSize())
                    {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxHeight().fillMaxWidth()
                        ) {
                            items(
                                count = liveDataElements.size
                            ) { item ->
                                val elementDescription =
                                    liveDataElements[item].descriptionResKey?.let {
                                        stringResource(it)
                                    }
                                CheckboxRow(
                                    element = liveDataElements[item],
                                    isChecked = selectedLiveDataElements.contains(liveDataElements[item]),
                                    onCheckedChange = {
                                        val element = liveDataElements[item]
                                        if (selectedLiveDataElements.contains(element)) {
                                            selectedLiveDataElements.remove(element)
                                        } else {
                                            element.description = elementDescription
                                            selectedLiveDataElements.add(element)
                                        }
                                    })
                            }
                        }
                        Box(
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            ToafVerticalScrollbar(listState = listState)
                        }
                    }
                }
                else
                {
                    when(liveDataType){
                        LiveDataType.Chart -> LivaDataChart(liveDataChartResult)
                        LiveDataType.Table -> LivaDataTable(liveDataResults)
                    }
                }
                if (saveToFileDialogQuestion)
                {
                    DialogQuestion(
                        question =  Res.string.AD_save_to_file_message,
                        onNoClick = {
                            pendingFileToSave = null
                            saveToFileDialogQuestion = false
                        },
                        onYesClick = {
                            fileToSave = pendingFileToSave
                            saveToFileDialogQuestion = false
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun CheckboxRow(element : LiveDataElement?, isChecked : Boolean, onCheckedChange :(Boolean) -> Unit )
{
    if(element != null) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { onCheckedChange(!isChecked) }
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                        checkedColor =  Color.White,
                    uncheckedColor = Color.White,
                    checkmarkColor = Color.Red)
                )
            Spacer(modifier = Modifier.width(1.dp))
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(1.dp),
                text = if (element.descriptionResKey == null ) element.name else stringResource(element.descriptionResKey!!),
                color = ToafColors.toafTextColorWhite,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun LivaDataTable(liveDataResults : List<LiveDataResultElement>)
{
    LazyColumn(
        modifier = Modifier.fillMaxHeight().fillMaxWidth()
    ) {
        items(
            count = liveDataResults.count()
        ) { item ->
            ResultRow(liveDataResults[item])
        }
    }
}

@Composable
private fun LivaDataChart(liveDataElements: List<List<Float>>)
{
    if (liveDataElements.isNotEmpty()) {
        val lines = getLineSeries(liveDataElements.size)
        val modelProducer = remember { CartesianChartModelProducer() }
        LaunchedEffect(liveDataElements, modelProducer) {
            val validateSeries = liveDataElements.filter { it.isNotEmpty() }
            if (validateSeries.isNotEmpty()) {
                withContext(Dispatchers.Default)
                {
                    modelProducer.runTransaction {
                        lineModel { validateSeries.forEach { data -> series(data) } }
                    }
                }
            }
        }
        // Zoom.max/Zoom.fixed create a new instance on every call, so it must be
        // remembered - otherwise the zoom state below would see a "changed" maxZoom on
        // every recomposition (i.e. every incoming sample) and reset itself.
        val maxZoom = remember { Zoom.max(Zoom.fixed(50f), Zoom.Content) }
        // null = auto-fit mode ("1x": every sample is always visible). Once the user
        // zooms in with the wheel this holds their chosen absolute zoom factor, until
        // they zoom back out to the fit-all level, at which point it becomes null again.
        val manualZoomFactor = remember { mutableStateOf<Float?>(null) }
        // Identity must stay stable (see the maxZoom comment above), but its *behavior*
        // reacts to manualZoomFactor, so this alone drives auto vs. manual mode - Vico's
        // own "auto until the user zooms" flag can't be reset once tripped, so we don't
        // rely on it at all.
        val dynamicInitialZoom = remember {
            Zoom { context, layerDimensions, bounds ->
                manualZoomFactor.value ?: Zoom.Content.getValue(context, layerDimensions, bounds)
            }
        }
        val zoomState = rememberVicoZoomState(
            // We handle the wheel ourselves below (ordinary drag/touch panning still
            // goes through Vico normally), so that zooming back out to 1x can cleanly
            // resume auto-fit.
            zoomEnabled = false,
            initialZoom = dynamicInitialZoom,
            minZoom = Zoom.Content,
            maxZoom = maxZoom
        )
        CartesianChartHost(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .pointerInput(zoomState) {
                    awaitPointerEventScope {
                        while (true) {
                            // Initial pass: intercept the wheel before Vico's own
                            // (unrelated to zoom, since zoomEnabled = false) drag/scroll
                            // handling can treat it as panning.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.firstOrNull() ?: continue
                            val minValue = zoomState.valueRange.start
                            val requested = zoomState.value * (1f - change.scrollDelta.y * 0.1f)
                            manualZoomFactor.value =
                                if (requested <= minValue + 0.0001f) null else requested
                            change.consume()
                        }
                    }
                },
            chart = rememberCartesianChart(
                layers = arrayOf(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            lines = lines
                        )
                    )
                ),
                startAxis = VerticalAxis.rememberStart(
                    title = {"Oś Y"},
                    label = rememberTextComponent(style = TextStyle(color = Color.White)),
                    guideline = rememberAxisGuidelineComponent(
                        fill = Fill(Color.White),
                        thickness = 1.dp
                    ),
                    tick = rememberLineComponent(
                        fill = Fill(Color.White),
                        thickness = 1.dp
                    ),
                    line = rememberLineComponent(
                        fill = Fill(Color.White),
                        thickness = 1.dp
                    ),
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    guideline = null,
                    label = null,
                    tick = rememberLineComponent(
                        fill = Fill(Color.White),
                        thickness = 1.dp
                    ),
                    line = rememberLineComponent(
                        fill = Fill(Color.White),
                        thickness = 1.dp
                    ),

                ),
            ),
            modelProducer = modelProducer,
            zoomState = zoomState,
            scrollState = rememberVicoScrollState(
                scrollEnabled = true
            ),
            animationSpec = null
        )
    }
}

@Composable
private fun getLineSeries(count : Int) : List<LineCartesianLayer.Line>
{
    val seriesColors = listOf(
        Color.Red,
        Color.Green,
        Color.Blue,
        Color.Yellow,
        Color.Cyan,
        Color.Magenta,
        Color.White
    )
    val newLines =  List(count) { i ->
        val color = seriesColors[i % seriesColors.size]
        LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill = Fill(color)),
            pointConnector = LineCartesianLayer.PointConnector.cubic(1f),
            stroke = LineCartesianLayer.LineStroke.Continuous(
                thickness = 3.dp,
            ),
        )
    }
    val cache = remember { mutableStateOf(newLines) }
    if (cache.value != newLines)
    {
        cache.value = newLines
    }
    return  cache.value
}

@Composable
private fun ResultRow(element : LiveDataResultElement?) {
    if (element != null) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(1.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            )
            {
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    text = if (element.description != null) stringResource(element.description!!) else element.name,
                    color = ToafColors.toafTextColorWhite,
                    fontSize = 16.sp,
                    maxLines = 2
                )
                Text(
                    modifier = Modifier
                        .padding(1.dp),
                    text = element.staticValue?.let { stringResource(it) }
                        ?: element.valueWithUnit?.takeIf { it.isNotEmpty() }
                        ?: "---",
                    color = ToafColors.toafTextColorWhite,
                    fontSize = 16.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
@Preview(showBackground = true, backgroundColor = 0x0FF00000)
@Composable
fun ActivityLiveDataModelPreview()
{
    ActivityLiveDataModel(
        moduleKoinName = KoinNames.ModuleR40Bcu,
        {}
    )
}

private fun getFakeLiveDataElements() : List<LiveDataElement>
{
    val ld1 = LiveDataElement()
    ld1.name = "Voltage"
    ld1.descriptionResKey = Res.string.bmbt_live_data_battery_voltage
    val ld2 = LiveDataElement()
    ld2.name = "speed"
    ld2.descriptionResKey = Res.string.cc_live_data_set_speed
    val ld3 = LiveDataElement()
    ld3.name = "emptyElement"

    return listOf(ld1,ld2,ld3)
}

private fun switchLiveDataState(state : Boolean, viewModel : LiveDateViewModel, elements : List<LiveDataElement>)
{
    if (state) {
        viewModel.stopLiveData()
    }
    else
    {
        viewModel.setLiveDataElements(elements)
        viewModel.startLiveData()
    }

}