package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogInput
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.composeModels.menus.ToafVerticalScrollbar
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.containers.SERVOTRONIC_RAW_MAX
import pl.misek.toafmobile.modules.containers.ServotronicMap
import pl.misek.toafmobile.modules.containers.ServotronicPoint
import pl.misek.toafmobile.modules.containers.servotronicMaToRaw
import pl.misek.toafmobile.modules.containers.servotronicRawToMa
import pl.misek.toafmobile.modules.l322.ModuleBCU
import pl.misek.toafmobile.modules.interfaces.IModuleServotronic
import pl.misek.toafmobile.network.UserInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

const val SERVOTRONIC_CURRENT_MIN = 0

private data class ServotronicReadResult(val codingIndex : String?, val points : List<ServotronicPoint>?)

data class ServotronicValidationTexts(
    val notNumber : String,
    val outOfRange : String,
    val greaterThanPrevious : String,
    val lessThanNext : String
)

val SERVOTRONIC_CURRENT_MAX = servotronicRawToMa(SERVOTRONIC_RAW_MAX)

fun validateServotronicValue(points : List<ServotronicPoint>, index : Int, input : String, texts : ServotronicValidationTexts) : String?
{
    val currentMa = input.trim().toIntOrNull() ?: return texts.notNumber
    if (currentMa !in SERVOTRONIC_CURRENT_MIN..SERVOTRONIC_CURRENT_MAX)
        return String.format(texts.outOfRange, SERVOTRONIC_CURRENT_MIN, SERVOTRONIC_CURRENT_MAX)
    val raw = servotronicMaToRaw(currentMa)
    val previous = points.getOrNull(index - 1)
    if (previous != null && raw > previous.rawDec)
        return String.format(texts.greaterThanPrevious, previous.currentMa, previous.speed)
    val next = points.getOrNull(index + 1)
    if (next != null && raw < next.rawDec)
        return String.format(texts.lessThanNext, next.currentMa, next.speed)
    return null
}

@Composable
fun AdvanceControlServotronic(userInfo: UserInfo, onShowResult : (result : Boolean, onSuccess : String?, onFail : String?) -> Unit) {
    val modulesManager : ModulesManager = koinInject()
    val moduleBcu = modulesManager.getCurrentModules().firstOrNull { it.getKoinName() == KoinNames.ModuleL322Bcu } as? IModuleServotronic
    var servotronicPoints by remember { mutableStateOf<List<ServotronicPoint>>(emptyList()) }
    var codingIndex by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var actionType by remember { mutableStateOf(ActionTypeEnum.None) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var dialogShowNotFullVersion by remember { mutableStateOf(false) }
    val readSuccessful = stringResource(Res.string.advance_control_servotronic_read_successful)
    val readFailed = stringResource(Res.string.advance_control_servotronic_read_failed)
    val writeSuccessful = stringResource(Res.string.advance_control_servotronic_write_successful)
    val writeFailed = stringResource(Res.string.advance_control_servotronic_write_failed)

    AdvanceControlServotronicContent(
        servotronicPoints = servotronicPoints,
        codingIndex = codingIndex,
        defaultMaps = moduleBcu?.getDefaultMaps(codingIndex) ?: emptyList(),
        onDefaultMapSelected = { map -> servotronicPoints = map.points },
        onPointChanged = { index, value ->
            servotronicPoints = servotronicPoints.toMutableList().also {
                it[index] = it[index].copy(rawDec = value)
            }
        },
        onReadClick = {
            actionType = ActionTypeEnum.ReadServotronic
            action = {
                ServotronicReadResult(
                    codingIndex = moduleBcu?.readCodingIndex(),
                    points = moduleBcu?.readServotronicPoints()
                )
            }
            showProgressDialog = true
        },
        onWriteClick = {
            actionType = ActionTypeEnum.WriteServotronic
            val pointsToWrite = servotronicPoints
            action = { moduleBcu?.writeServotronicPoints(pointsToWrite) == true }
            if (userInfo.isFullVersion())
                showProgressDialog = true
            else
                dialogShowNotFullVersion = true
        }
    )

    if (showProgressDialog) {
        ToafProgressDialog(
            action = { action() },
            onSuccess = { actionResult ->
                when (actionType) {
                    ActionTypeEnum.ReadServotronic -> {
                        val readResult = actionResult as? ServotronicReadResult
                        if (readResult?.codingIndex != null)
                            codingIndex = readResult.codingIndex
                        val points = readResult?.points
                        if (points != null)
                            servotronicPoints = points
                        onShowResult(points != null, readSuccessful, readFailed)
                    }
                    ActionTypeEnum.WriteServotronic -> {
                        onShowResult(actionResult == true, writeSuccessful, writeFailed)
                    }
                    else -> {}
                }
            },
            onError = {
                onShowResult(
                    false,
                    null,
                    if (actionType == ActionTypeEnum.WriteServotronic) writeFailed else readFailed
                )
            },
            onDismissRequest = {
                actionType = ActionTypeEnum.None
                showProgressDialog = false })
    }
    if (dialogShowNotFullVersion) {
        DialogNotFullVersion(
            onWatchAdResult = { showProgressDialog = true },
            onCloseClick = { dialogShowNotFullVersion = false }
        )
    }
}

@Composable
private fun AdvanceControlServotronicContent(
    servotronicPoints : List<ServotronicPoint>,
    codingIndex : String?,
    defaultMaps : List<ServotronicMap>,
    onDefaultMapSelected : (ServotronicMap) -> Unit,
    onPointChanged : (index : Int, rawDec : Int) -> Unit,
    onReadClick : () -> Unit,
    onWriteClick : () -> Unit)
{
    var editedIndex by remember { mutableStateOf<Int?>(null) }
    val validationTexts = ServotronicValidationTexts(
        notNumber = stringResource(Res.string.advance_control_servotronic_validation_not_number),
        outOfRange = stringResource(Res.string.advance_control_servotronic_validation_out_of_range),
        greaterThanPrevious = stringResource(Res.string.advance_control_servotronic_validation_greater_than_previous),
        lessThanNext = stringResource(Res.string.advance_control_servotronic_validation_less_than_next)
    )
    Surface(
        color = ToafColors.toafBackground,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.White),
        modifier = Modifier
            .padding(all = 3.dp)
            .fillMaxSize()
    )
    {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            Text(
                text = stringResource(Res.string.advance_control_servotronic_title),
                textAlign = TextAlign.Center,
                style = TextStyle(color = Color.White, fontSize = 16.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 2.dp)
            )

            if (defaultMaps.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.advance_control_servotronic_default_map, codingIndex ?: "--"),
                    textAlign = TextAlign.Center,
                    style = TextStyle(color = Color.White, fontSize = 14.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                )
                {
                    defaultMaps.forEach { map ->
                        ToafButtons.GreenButton(
                            buttonText = map.name,
                            modifier = Modifier.weight(1f),
                            onClick = { onDefaultMapSelected(map) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            ServotronicTable(
                servotronicPoints = servotronicPoints,
                onCurrentClick = { index -> editedIndex = index },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            )
            {
                ToafButtons.GreenButton(
                    buttonText = Res.string.advance_control_servotronic_read,
                    modifier = Modifier.weight(1f),
                    onClick = { onReadClick() }
                )
                ToafButtons.RedButton(
                    buttonText = Res.string.advance_control_servotronic_write,
                    modifier = Modifier.weight(1f),
                    onClick = { onWriteClick() },
                    enabled = servotronicPoints.isNotEmpty()
                )
            }
        }
    }
    editedIndex?.let { index ->
        DialogInput(
            title = Res.string.advance_control_servotronic_edit_title,
            name = Res.string.advance_control_servotronic_current,
            value = servotronicPoints[index].currentMa.toString(),
            okButtonText = Res.string.advance_control_servotronic_edit_save,
            validate = { input -> validateServotronicValue(servotronicPoints, index, input, validationTexts) },
            onCancelClick = { editedIndex = null },
            onOkClick = { input ->
                onPointChanged(index, servotronicMaToRaw(input.trim().toInt()))
                editedIndex = null
            }
        )
    }
}

@Composable
private fun ServotronicTable(servotronicPoints : List<ServotronicPoint>, onCurrentClick : (index : Int) -> Unit, modifier: Modifier)
{
    val listState = rememberLazyListState()
    Column(
        modifier = modifier
            .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
            .background(ToafColors.toafBackground, RoundedCornerShape(6.dp))
    )
    {
        ServotronicTableRow(
            speed = stringResource(Res.string.advance_control_servotronic_speed),
            current = stringResource(Res.string.advance_control_servotronic_current),
            isHeader = true,
            onCurrentClick = null
        )
        HorizontalDivider(color = Color.LightGray)
        if (servotronicPoints.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.advance_control_servotronic_no_data),
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            }
        }
        else {
            Box(modifier = Modifier.fillMaxSize())
            {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(servotronicPoints) { index, point ->
                        ServotronicTableRow(
                            speed = point.speed.toString(),
                            current = point.currentMa.toString(),
                            isHeader = false,
                            onCurrentClick = { onCurrentClick(index) }
                        )
                        if (index < servotronicPoints.lastIndex)
                            HorizontalDivider(color = Color.DarkGray)
                    }
                }
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    ToafVerticalScrollbar(listState = listState)
                }
            }
        }
    }
}

@Composable
private fun ServotronicTableRow(speed : String, current : String, isHeader : Boolean, onCurrentClick : (() -> Unit)?)
{
    val style = TextStyle(
        color = Color.White,
        fontSize = if (isHeader) 14.sp else 16.sp,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    )
    {
        Text(text = speed, style = style, modifier = Modifier.weight(1f))
        Text(
            text = current,
            style = if (onCurrentClick != null) style.copy(textDecoration = TextDecoration.Underline) else style,
            modifier = Modifier
                .weight(1f)
                .then(if (onCurrentClick != null) Modifier.clickable { onCurrentClick() } else Modifier)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xff000000)
@Composable
fun AdvanceControlServotronicPreview()
{
    val defaultMaps = ModuleBCU(null).getDefaultMaps("10")
    var points by remember { mutableStateOf(defaultMaps.first().points) }
    AdvanceControlServotronicContent(
        servotronicPoints = points,
        codingIndex = "10",
        defaultMaps = defaultMaps,
        onDefaultMapSelected = { map -> points = map.points },
        onPointChanged = { index, value ->
            points = points.toMutableList().also { it[index] = it[index].copy(rawDec = value) }
        },
        onReadClick = {},
        onWriteClick = {}
    )
}
