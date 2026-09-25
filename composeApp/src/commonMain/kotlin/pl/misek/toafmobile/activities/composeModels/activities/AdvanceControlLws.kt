package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import jdk.internal.net.http.common.Utils.close
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.PlatformType
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafEditText
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.definitions.ToafStyles
import pl.misek.toafmobile.getPlatformType
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.modules.l322.ModuleDSC
import pl.misek.toafmobile.modules.viewModels.LwsSteeringAngleViewModel
import pl.misek.toafmobile.network.UserInfo
import pl.misek.toafmobile.tools.JsonSupport
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.moveTo
import kotlin.time.Duration.Companion.milliseconds

private const val tag = "AdvanceControlLws"
@Composable
fun AdvanceControlLws(userInfo: UserInfo, onShowResult : (result : Boolean, onSuccess : String?, onFail : String?) -> Unit) {
    val modulesManager : ModulesManager = koinInject()
    val moduleLws = modulesManager.getCurrentModules().firstOrNull { it.getKoinName() == KoinNames.ModuleL322Lws }
    val moduleDsc = modulesManager.getCurrentModules().firstOrNull { it.getKoinName() == KoinNames.ModuleL322Dsc }
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var actionType by remember {mutableStateOf (ActionTypeEnum.None)}
    var showProgressDialog by remember { mutableStateOf(false) }
    var dialogShowNotFullVersion by remember { mutableStateOf(false) }
    val calibrationSuccessful = stringResource(Res.string.lws_advance_control_calibration_successful)
    val calibrationFailed = stringResource(Res.string.lws_advance_control_calibration_failed)
    val dialogFullVersionTittle = stringResource(Res.string.dialog_full_version_tittle)
    val moduleLiveData = moduleLws as? IModuleLiveData
    var liveDataText by remember { mutableStateOf("---") }
    val viewModel : LwsSteeringAngleViewModel = koinInject()
    val rotation by viewModel.angle.collectAsState()
    LaunchedEffect(Unit)
    {
        moduleLiveData?.startLiveDataRead(emptyList())
    }
    LaunchedEffect(rotation)
    {
        liveDataText = if (rotation.isNaN()) "---"  else
            String.format(
            Locale.getDefault(),
            "%.2f °",
            rotation)

    }

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.White),
        modifier = Modifier
            .padding(all = 3.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
        )
    {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    )
    {
        Text(
            text = stringResource(Res.string.lws_advance_control_title),
            textAlign = TextAlign.Center,
            style = TextStyle(color = Color.White, fontSize = 16.sp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 2.dp)
        )

        Spacer(Modifier.height(12.dp))

        SteeringCalibrationScreen(rotation, Modifier.fillMaxWidth().weight(1f))

        Spacer(Modifier.height(12.dp))

        Text(
            text = liveDataText,
            textAlign = TextAlign.Center,
            style = TextStyle(color = Color.White, fontSize = 24.sp))

        Spacer(Modifier.height(12.dp))

        ToafButtons.GreenButton(
            buttonText = Res.string.lws_advance_control_write_adjustment,
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp),
            onClick = {
                actionType = ActionTypeEnum.LwsCalibration
                action = {
                    val m = moduleDsc as? ModuleDSC
                    m?.getIdentification() != null && m.initLwsCalibration() && m.writeLwsCalibration()
                }
                if (userInfo.isFullVersion())
                    showProgressDialog = true
                else
                    dialogShowNotFullVersion = true
            }
        )
        Row(
            modifier = Modifier
                .padding(all = 2.dp)
                .fillMaxWidth()
        )
        {
            if (showProgressDialog) {
                ToafProgressDialog(
                    action = { action() },
                    onSuccess = { actionResult ->
                        if (actionResult is Boolean) {
                            onShowResult(
                                actionResult,
                                calibrationSuccessful,
                                calibrationFailed
                            )
                        }
                    },
                    onError = {
                        onShowResult(
                            false,
                            null,
                            calibrationFailed
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
        }
    }
}
@Composable
fun SteeringWheelLive(targetAngle: Float, modifier: Modifier = Modifier) {
    var lastValidAngle by remember { mutableFloatStateOf(0f) }
    if (!targetAngle.isNaN())
        lastValidAngle = targetAngle
    val angle by animateFloatAsState(
        targetValue = lastValidAngle,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "steer"
    )
    Image(
        painter = painterResource(Res.drawable.steering_wheel_l322),
        contentDescription = null,
        modifier = modifier.size(480.dp).graphicsLayer { rotationZ = angle }
    )
}

@Composable
fun SteeringCalibrationScreen(angleDeg: Float, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(Modifier.size(width = 24.dp, height = 16.dp)) {
            val p = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height) // wierzchołek w dół
                close()
            }
            drawPath(p, Color(0xFFD9534F))
        }

        Spacer(Modifier.height(6.dp))

        SteeringWheelLive(
            targetAngle = angleDeg,
            modifier = Modifier.size(260.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AdvanceControlLwsPreview()
{
    AdvanceControlLws(UserInfo()) { _, _, _ -> }
}