package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.AdvanceControlShowResult
import pl.misek.toafmobile.modules.interfaces.EwsSyncStep
import pl.misek.toafmobile.modules.interfaces.IModuleEwsSync
import pl.misek.toafmobile.modules.r40.ModuleEWS
import pl.misek.toafmobile.network.UserInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun AdvanceControlEmsEwsSync(engineModule : IModuleEwsSync, userInfo : UserInfo, onShowResult : AdvanceControlShowResult) {
    val modulesManager : ModulesManager = koinInject()
    val modules = modulesManager.getCurrentModules()
    val moduleEws = modules.firstOrNull { it.getKoinName() == KoinNames.ModuleR40Ews } as? ModuleEWS

    var showProgressDialog by remember { mutableStateOf(false) }
    var dialogShowNotFullVersion by remember { mutableStateOf(false) }
    val stepResults = remember { mutableStateListOf<EwsSyncStep>() }
    val successful = stringResource(Res.string.advance_control_ews_sync_successful)
    val failed = stringResource(Res.string.advance_control_ews_sync_failed)

    AdvanceControlEmsEwsSyncContent(
        steps = stepResults,
        onSyncClick = {
            if (userInfo.isFullVersion())
                showProgressDialog = true
            else
                dialogShowNotFullVersion = true
        }
    )

    if (showProgressDialog) {
        ToafProgressDialog(
            action = {
                stepResults.clear()
                engineModule.syncEws(moduleEws) { step -> stepResults.add(step) }
            },
            onSuccess = { actionResult ->
                onShowResult(actionResult, successful, failed)
            },
            onError = { onShowResult(false, null, failed) },
            onDismissRequest = { showProgressDialog = false }
        )
    }
    if (dialogShowNotFullVersion) {
        DialogNotFullVersion(
            onWatchAdResult = { showProgressDialog = true },
            onCloseClick = { dialogShowNotFullVersion = false }
        )
    }
}

@Composable
private fun AdvanceControlEmsEwsSyncContent(
    steps : List<EwsSyncStep>,
    onSyncClick : () -> Unit)
{
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
                text = stringResource(Res.string.advance_control_ews_sync_title),
                textAlign = TextAlign.Center,
                style = TextStyle(color = Color.White, fontSize = 16.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 2.dp)
            )
            Text(
                text = stringResource(Res.string.advance_control_ews_sync_info),
                textAlign = TextAlign.Center,
                style = TextStyle(color = Color.White, fontSize = 14.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )

            Spacer(Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
            )
            {
                steps.forEachIndexed { index, step ->
                    EwsSyncStepRow(index + 1, stringResource(step.name), step.success, step.detail)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            )
            {
                ToafButtons.RedButton(
                    buttonText = Res.string.advance_control_ews_sync_button,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSyncClick() }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EwsSyncStepRow(number : Int, name : String, success : Boolean, detail : String?)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    )
    {
        Text(
            text = if (success) "✓" else "✗",
            style = TextStyle(
                color = if (success) Color.Green else Color.Red,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = "$number. $name",
                style = TextStyle(color = Color.White, fontSize = 15.sp)
            )
            if (!detail.isNullOrEmpty())
                Text(
                    text = detail,
                    style = TextStyle(color = Color.LightGray, fontSize = 12.sp)
                )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xff000000)
@Composable
fun AdvanceControlEmsEwsSyncPreview()
{
    AdvanceControlEmsEwsSyncContent(
        steps = listOf(
            EwsSyncStep(Res.string.advance_control_ews_sync_step_ecu_ident, true, "DDE 7801234"),
            EwsSyncStep(Res.string.advance_control_ews_sync_step_ews_ident, true, "883874480381810708000606"),
            EwsSyncStep(Res.string.advance_control_ews_sync_step_ews_handshake, true),
            EwsSyncStep(Res.string.advance_control_ews_sync_step_enter, true),
            EwsSyncStep(Res.string.advance_control_ews_sync_step_status, true, "0x00")
        ),
        onSyncClick = {}
    )
}
