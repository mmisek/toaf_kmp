package pl.misek.toafmobile.activities.composeModels.activities

import pl.misek.toafmobile.PlatformType
import pl.misek.toafmobile.getPlatformType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.ToafConstants
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafEditText
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.definitions.ToafStyles
import pl.misek.toafmobile.billing.IToafBilling
import pl.misek.toafmobile.billing.StripeSession
import pl.misek.toafmobile.tools.getPlatformActivity
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.IModuleEKA
import pl.misek.toafmobile.network.NetworkTools
import pl.misek.toafmobile.network.ToafApi
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun AdvanceCodingEka(
        koinModuleName : KoinNames,
        onShowResult : (result : Boolean, onSuccess : String?, onFail : String? ) -> Unit)
{
    val scope = rememberCoroutineScope()
    val toafApi : ToafApi = koinInject()
    val toafBilling : IToafBilling = koinInject()
    val modulesManager : ModulesManager = koinInject()
    val currentModule = modulesManager.getCurrentModules().firstOrNull { it.getKoinName() == koinModuleName}
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var actionType by remember {mutableStateOf (ActionTypeEnum.ReadEkaCode)}
    var showProgressDialog by remember { mutableStateOf(false) }
    var showFullVersionDialog by remember { mutableStateOf(false) }
    var ekaCodeValidationResult by remember {mutableStateOf(true)}
    var ekaCode by remember{ mutableStateOf<String?>("") }
    var stripeSession by remember { mutableStateOf<StripeSession?>(null) }
    val ekaCodeValidationFailText = stringResource(Res.string.activity_advance_coding_eka_matches_error)
    val ekaCodeWriteSuccessText = stringResource(Res.string.activity_advance_coding_eka_write_successful)
    val ekaCodeWriteFailText = stringResource(Res.string.activity_advance_coding_eka_write_error)
    val serverConnectionFailedText = stringResource(Res.string.server_connection_failed)
    val useMobileVersionToBuyTokens = stringResource(Res.string.useMobileVersionToBuyTokens)
    val activity = getPlatformActivity()
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
        modifier = Modifier
            .fillMaxWidth()
    )
    {
        if (showProgressDialog)
        {
            ToafProgressDialog(
                action = { action() },
                onSuccess = { actionResult ->
                    if (actionResult is Boolean)
                    {
                        when(actionType)
                        {
                            ActionTypeEnum.ReadEkaCode -> {
                                scope.launch {
                                    val localUserInfo = toafApi.getUserInfo()
                                    if (localUserInfo != null) {
                                        localUserInfo.ekaKeys -= 1
                                        toafApi.updateUserInfo(userInfo = localUserInfo)
                                    }
                                }
                            }
                            ActionTypeEnum.WriteEkaCode -> {
                                onShowResult(
                                    actionResult,
                                    ekaCodeWriteSuccessText,
                                    ekaCodeWriteFailText
                                )
                            }
                            else -> {}
                        }
                    }
                },
                onDismissRequest = {showProgressDialog = false})
        }
        if (showFullVersionDialog)
        {
            DialogNotFullVersion(
                onWatchAdResult = { showProgressDialog = true },
                onCloseClick = {showProgressDialog = false}
            )
        }
        Text(
            text = stringResource(Res.string.activity_advance_coding_eka_programming),
            textAlign = TextAlign.Center,
            style = TextStyle(color = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 2.dp)
        )
        ToafEditText(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(all = 5.dp),
            value = ekaCode ?: "",
            isError = !ekaCodeValidationResult,
            onValueChanged = { v ->
                ekaCode = v
                validateEkaCode(ekaCode)
            },
            name = Res.string.activity_advance_coding_eka_current
        )
        Row(
            modifier = Modifier
                .padding(all = 2.dp)
                .fillMaxWidth()
        )
        {
            ToafButtons.GreenButton(
                buttonText = Res.string.activity_advance_coding_read_button,
                modifier = Modifier.weight(1f).padding(ToafStyles.buttonPadding),
                onClick = {
                    actionType = ActionTypeEnum.ReadEkaCode
                    action = {
                        val localUserInfo = toafApi.getUserInfo()
                        if (localUserInfo == null) {
                            onShowResult(false,null, serverConnectionFailedText)
                        }
                        else if (localUserInfo.ekaKeys == 0) {
                            if (getPlatformType() == PlatformType.Android) {
                                toafBilling.buyElement(activity, ToafConstants.SKU_TOAF_EKA_CODE)
                            } else {
                                toafBilling.getElementPrice(ToafConstants.SKU_TOAF_EKA_CODE)
                                stripeSession = toafBilling.getStripeSession(ToafConstants.SKU_TOAF_EKA_CODE)
                                if (stripeSession != null)
                                {
                                    NetworkTools.openUrlInBrowser(stripeSession?.url ?: "")
                                }
                            }
                        }
                        else {
                            ekaCode = (currentModule as IModuleEKA).readEKACode()
                        }
                    }
                    showProgressDialog = true
                })
            ToafButtons.RedButton(
                enabled = false,
                buttonText = Res.string.activity_advance_coding_save_button,
                modifier = Modifier.weight(1f).padding(ToafStyles.buttonPadding),
                onClick = {
                    actionType = ActionTypeEnum.WriteEkaCode
                    action = {
                        (currentModule as IModuleEKA).writeEKACode(ekaCode!!.toInt())
                    }
                    if (ekaCodeValidationResult) {
                        showProgressDialog = true
                    } else
                        onShowResult(false, null, ekaCodeValidationFailText)
                })
        }
        }
    }
}
private fun validateEkaCode(code : String?) : Boolean
{
    val regex = "[0-9]{4}".toRegex()
    var result = code?.matches(regex) == true
    val eka = code?.toIntOrNull() ?: 0
    result = eka in 1001..9999 && result
    return result
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AdvanceCodingEkaPreview()
{
    AdvanceCodingEka(
        KoinNames.ModuleRJOScu){_,_,_ ->}
}