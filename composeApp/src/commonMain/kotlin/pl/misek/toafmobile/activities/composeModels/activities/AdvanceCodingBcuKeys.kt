package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogInput
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogQuestion
import pl.misek.toafmobile.activities.definitions.ToafStyles
import pl.misek.toafmobile.billing.IToafBilling
import pl.misek.toafmobile.tools.getPlatformActivity
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.getPlatformType
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.containers.BCUKey
import pl.misek.toafmobile.modules.interfaces.IModuleBcuKeys
import pl.misek.toafmobile.network.ServerConnectionState
import pl.misek.toafmobile.network.ToafApi
import pl.misek.toafmobile.network.UserInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import kotlin.collections.forEach

@Composable
fun AdvanceCodingBcuKeys(
    koinModuleName : KoinNames,
    userInfo : UserInfo,
    onShowResult : (result : Boolean, onSuccess: String?, onFail : String?)  -> Unit)
{
    val modulesManager : ModulesManager = koinInject()
    val toafBilling : IToafBilling = koinInject()
    val database : Database = koinInject()
    val toafApi : ToafApi = koinInject()
    val currentModule = modulesManager.getCurrentModules().firstOrNull { it.getKoinName() == koinModuleName}
    var moduleVin by remember {mutableStateOf<String?>(null)}
    val scope = rememberCoroutineScope()
    var showProgressDialog by remember { mutableStateOf(false) }
    var showFullVersionDialog by remember { mutableStateOf(false) }
    var showDeleteKeyDialog by remember { mutableStateOf(false) }
    var showInputDialog by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    val onReadKeysFailText = stringResource(Res.string.activity_coding_bcu_key_read_error)
    val onKeyDeleteErrorText = stringResource(Res.string.activity_coding_bcu_key_remove_error)
    val onKeyRemoveSuccessText = stringResource(Res.string.activity_coding_bcu_key_remove_key_successful)
    val onKeyIsNotValidText = stringResource(Res.string.activity_coding_bcu_key_code_not_valid)
    val onKeyAddSuccessText = stringResource(Res.string.activity_coding_bcu_key_add_successful)
    val onKeyAddFailText = stringResource(Res.string.activity_coding_bcu_key_add_error)
    val serverConnectionFailedText = stringResource(Res.string.server_connection_failed)
    val useMobileVersionToBuyTokens = stringResource(Res.string.useMobileVersionToBuyTokens)
    var keyIdToProcess by remember { mutableIntStateOf(-1) }
    var keyCodeToProcess by remember { mutableStateOf<String?>(null) }
    var actionType by remember {mutableStateOf (ActionTypeEnum.ReadKeys)}
    val activity = getPlatformActivity()
    val currentBcuKey = remember { mutableStateListOf(
        BCUKey(0," "),
        BCUKey(1," "),
        BCUKey(2," "),
        BCUKey(3," "),
        BCUKey(4," "))
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
        modifier = Modifier
            .fillMaxWidth()
    )
    {
        if (showInputDialog)
        {
            DialogInput(
                title = Res.string.activity_coding_bcu_key_add_message,
                name = Res.string.activity_coding_bcu_key_add_hint,
                value = "",
                onCancelClick = { showInputDialog = false },
                onOkClick = {keyCode ->
                    val regex = Regex("^[0-9a-fA-F]{6}$")
                    if (!regex.matches(keyCode))
                    {
                        onShowResult(false,null, onKeyIsNotValidText)
                    }
                    else
                    {
                        showInputDialog = false
                        keyCodeToProcess = keyCode
                        action = {
                            val localUserInfo = toafApi.getUserInfo()
                            if (localUserInfo == null)
                                onShowResult(false,null, serverConnectionFailedText)
                            else if (localUserInfo.bcuKeys == 0) {
                                if (getPlatformType() == PlatformType.Android) {
                                    toafBilling.buyElement(
                                        activity,
                                        ToafConstants.SKU_TOAF_BCU_KEY
                                    )
                                } else {
                                    onShowResult(false, null, useMobileVersionToBuyTokens)
                                }
                            }
                            else {
                                (currentModule as IModuleBcuKeys).addKeyAt(keyIdToProcess, keyCode)
                            }
                        }
                        actionType = ActionTypeEnum.AddKey
                        showProgressDialog = true
                    }
                }
            )
        }
        if (showDeleteKeyDialog)
        {
            DialogQuestion.Show(
                question = Res.string.activity_coding_bcu_key_remove_message,
                onNoClick = {showDeleteKeyDialog = false},
                onYesClick = {
                    action = {
                        (currentModule as IModuleBcuKeys).removeKeyAt(keyIdToProcess)
                    }
                    showDeleteKeyDialog = false
                    showProgressDialog = true
                }
            )
        }
        if (showProgressDialog)
        {
            ToafProgressDialog(
                action = { action() },
                onSuccess = { actionResult ->
                    if (actionResult is Boolean)
                    {
                        when(actionType) {
                            ActionTypeEnum.ReadKeys ->
                                if (!actionResult)
                                    onShowResult(false, null, onReadKeysFailText)

                            ActionTypeEnum.DeleteKey -> {
                                if (actionResult)
                                    currentBcuKey[keyIdToProcess] = BCUKey(keyIdToProcess,"")
                                keyIdToProcess = -1
                                onShowResult(
                                    actionResult,
                                    onKeyRemoveSuccessText,
                                    onKeyDeleteErrorText
                                )
                            }

                            ActionTypeEnum.AddKey -> {
                                if (actionResult)
                                {
                                    scope.launch {
                                        withContext(Dispatchers.IO)
                                        {
                                            toafApi.userConsumeBcuKey(moduleVin, keyCodeToProcess)
                                            userInfo.bcuKeys -= 1
                                            toafApi.updateUserInfo(userInfo = userInfo)
                                            database.updateIntSettingByName(DatabaseSettingFields.TOAF_BCU_KEYS, userInfo.bcuKeys)
                                            currentBcuKey[keyIdToProcess] = BCUKey(keyIdToProcess,keyCodeToProcess ?: "")
                                            keyIdToProcess = -1
                                            keyCodeToProcess = ""
                                        }
                                    }

                                }
                                onShowResult(
                                    actionResult,
                                    onKeyAddSuccessText,
                                    onKeyAddFailText
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
            text = stringResource(Res.string.activity_coding_bcu_keys),
            textAlign = TextAlign.Center,
            style = TextStyle(color = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 2.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            currentBcuKey.forEach { bcuKey ->
                BcuKeyRow(
                    bcuKey,
                    onDeleteKey = { key ->
                        actionType = ActionTypeEnum.DeleteKey
                        keyIdToProcess = key
                        showDeleteKeyDialog = true
                    },
                    onAddKey = {keyId ->
                        actionType = ActionTypeEnum.AddKey
                        keyIdToProcess = keyId
                        showInputDialog = true
                    }
                )
            }
        }
        ToafButtons.GreenButton(
            buttonText = Res.string.activity_advance_coding_read_button,
            modifier = Modifier
                .padding(ToafStyles.buttonPadding)
                .fillMaxWidth(),
            onClick = {
                actionType = ActionTypeEnum.ReadKeys
                action = {
                    moduleVin = currentModule?.getVIN()
                    val keys = (currentModule as IModuleBcuKeys).readKeys()
                    keys?.let {
                        it.forEach { key -> currentBcuKey[key.id] = key }
                    }
                    keys != null
                }
                showProgressDialog = true
            })
        }
    }
}

@Composable
fun BcuKeyRow(bcuKey: BCUKey, onDeleteKey :(Int) -> Unit, onAddKey :(Int) -> Unit)
{
    val keyNumberText = Res.string.activity_coding_bcu_key_number
    Row (
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ){
        ToafEditText(
            modifier = Modifier
                .padding(ToafStyles.buttonPadding)
                .weight(0.2f),
            value = bcuKey.key,
            readOnly = true,
            onValueChanged = { bcuKey.updateKey(it) },
            nameString = String.format(stringResource(keyNumberText),bcuKey.id)
        )
        ToafButtons.RedButton(
            buttonText = Res.string.activity_coding_bcu_key_delete,
            modifier = Modifier.padding(ToafStyles.buttonPadding),
            onClick = {
                onDeleteKey(bcuKey.id)
            })
        ToafButtons.GreenButton(
            buttonText = Res.string.activity_coding_bcu_key_add,
            modifier = Modifier.padding(ToafStyles.buttonPadding),
            onClick = {onAddKey(bcuKey.id)}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AdvanceCodingBcuKeysPreview()
{
    AdvanceCodingBcuKeys(
        koinModuleName = KoinNames.ModuleR40Bcu,
        userInfo = UserInfo(),
        onShowResult =  {_,_,_ ->}
    )
}