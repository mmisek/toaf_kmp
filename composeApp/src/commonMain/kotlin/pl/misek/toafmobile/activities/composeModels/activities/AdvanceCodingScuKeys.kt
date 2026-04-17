package pl.misek.toafmobile.activities.composeModels.activities

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
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.PlatformType
import pl.misek.toafmobile.activities.ToafConstants
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafEditText
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.ToafWaitingDialog
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogInformation
import pl.misek.toafmobile.activities.definitions.ToafStyles
import pl.misek.toafmobile.billing.IToafBilling
import pl.misek.toafmobile.billing.StripeSession
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.getPlatformType
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.containers.BCUKey
import pl.misek.toafmobile.modules.interfaces.IModuleScuKeys
import pl.misek.toafmobile.network.NetworkTools
import pl.misek.toafmobile.network.ToafApi
import pl.misek.toafmobile.tools.getPlatformActivity
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

private const val tag = "AdvanceCoddingScuKeys"
@Composable
fun AdvanceCoddingScuKeys(koinModuleName : KoinNames, onShowResult : (result : Boolean, onSuccess : String?, onFail : String?) -> Unit) {
    val modulesManager : ModulesManager = koinInject()
    val currentModule = modulesManager.getCurrentModules().firstOrNull { it.getKoinName() == koinModuleName}
    var moduleVin by remember {mutableStateOf<String?>(null)}
    val toafBilling : IToafBilling = koinInject()
    val toafApi : ToafApi = koinInject()
    var stripeSession by remember { mutableStateOf<StripeSession?>(null) }
    val activity = getPlatformActivity()
    val scope = rememberCoroutineScope()
    var newBarcode by remember{ mutableStateOf("") }
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var showInformationDialog by remember { mutableStateOf(false) }
    var showWaitingDialog by remember { mutableStateOf(false) }
    var actionType by remember {mutableStateOf (ActionTypeEnum.None)}
    var barCodeValidationResult by remember { mutableStateOf(false) }
    val writeBarCodeSuccessText = stringResource(Res.string.activity_advance_coding_scu_write_success)
    val writeBarCodeFailText = stringResource(Res.string.activity_advance_coding_scu_write_failed)
    val serverConnectionFailedText = stringResource(Res.string.server_connection_failed)
    val purchaseCheckSuccess = stringResource(Res.string.information_purchase_ok)
    val purchaseCheckFailed = stringResource(Res.string.information_purchase_failed)
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
        Text(
            text = stringResource(Res.string.activity_advance_coding_scu_keys_programming),
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
            name = Res.string.activity_advance_coding_scu_new_key_barcode,
            value = newBarcode,
            isError = !barCodeValidationResult,
            onValueChanged = { newValue ->
                newBarcode = newValue
                barCodeValidationResult = validateBarcode(newBarcode)
            }
        )
        Row(
            modifier = Modifier
                .padding(all = 2.dp)
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
                            when(actionType) {
                                ActionTypeEnum.AddKey ->
                                {
                                    if (!actionResult)
                                    {
                                        scope.launch {
                                            withContext(Dispatchers.IO)
                                            {
                                                toafApi.userConsumeBcuKey(moduleVin, newBarcode)
                                                val localUserInfo = toafApi.getUserInfo()
                                                localUserInfo?.let {
                                                    it.bcuKeys -= 1
                                                    toafApi.updateUserInfo(userInfo = it)
                                                }
                                                newBarcode = ""
                                            }
                                        }
                                    }
                                    onShowResult(
                                        actionResult,
                                        writeBarCodeSuccessText,
                                        writeBarCodeFailText
                                    )
                                }
                                ActionTypeEnum.BuyElement ->
                                {
                                    if (actionResult)
                                    {
                                        actionType = ActionTypeEnum.CheckingPurchase
                                        action = {
                                            val timeout = 5*60
                                            var repeatNumber = 0
                                            var purchaseResult = false
                                            do
                                            {
                                                Logger.i {"Checking purchase, $repeatNumber"}
                                                purchaseResult = toafApi.checkPurchase(stripeSession?.sessionId!! )
                                                if (purchaseResult)
                                                    break
                                                repeatNumber++
                                                delay(1000)
                                            } while (repeatNumber < timeout)
                                            purchaseResult
                                        }
                                        showWaitingDialog = true
                                    }
                                }
                                else -> {}
                            }
                        }
                    },
                    onError = {
                        onShowResult(
                            false,
                            null,
                            writeBarCodeFailText
                        )
                    },
                    onDismissRequest = {showProgressDialog = false})
            }
            if (showWaitingDialog)
            {
                ToafWaitingDialog(
                    action = {action()},
                    onSuccess = { actionResult ->
                        if (actionResult is Boolean) {
                            when (actionType) {
                                ActionTypeEnum.CheckingPurchase ->
                                    onShowResult(
                                        actionResult,
                                        purchaseCheckSuccess,
                                        purchaseCheckFailed
                                    )

                                else -> {}
                            }
                        }
                    },
                    onError = {},
                    onDismissRequest = {showWaitingDialog = false}
                )
            }
            if (showInformationDialog)
            {
                DialogInformation(
                    information =  Res.string.information_no_bcu_keys,
                    onCancelClick = { showInformationDialog = false},
                    onOkClick = {
                        actionType = ActionTypeEnum.BuyElement
                        action = {
                            if (getPlatformType() == PlatformType.Android) {
                                toafBilling.buyElement(
                                    activity,
                                    ToafConstants.SKU_TOAF_BCU_KEY
                                )
                                true
                            } else {
                                toafBilling.getElementPrice(ToafConstants.SKU_TOAF_BCU_KEY)
                                stripeSession = toafBilling.getStripeSession(ToafConstants.SKU_TOAF_BCU_KEY)
                                stripeSession?.let { NetworkTools.openUrlInBrowser(it.url) }
                                true
                            }
                        }
                        showInformationDialog = false
                        showProgressDialog = true
                    }
                )
            }
            ToafButtons.GreenButton(
                buttonText = Res.string.activity_advance_coding_save_button,
                modifier = Modifier.weight(1f).padding(ToafStyles.buttonPadding),
                enabled = barCodeValidationResult,
                onClick = {
                    action = {
                        if (validateBarcode(newBarcode)) {
                            val localUserInfo = toafApi.getUserInfo()
                            if (localUserInfo == null)
                                onShowResult(false,null, serverConnectionFailedText)
                            else if (localUserInfo.bcuKeys == 0) {
                                showInformationDialog = true
                            }
                            else {
                                moduleVin = currentModule?.getVIN()
                                (currentModule as IModuleScuKeys).addKey(newBarcode)
                            }
                        }
                        else
                        {
                            false
                        }
                    }
                    actionType = ActionTypeEnum.AddKey
                    showProgressDialog = true
                })
            }
        }
    }
}

private fun validateBarcode(barcode : String) : Boolean
{
    val regex = "[a-zA-Z][a-fA-F0-9]{20}[a-zA-Z]".toRegex()
    return if (barcode.isEmpty()) false else barcode.matches(regex)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AdvanceCoddingScuKeysPreview()
{
    AdvanceCoddingScuKeys(KoinNames.ModuleRJOScu, ) { _, _, _ -> }
}