package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.menus.ToafVerticalScrollbar
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.containers.EwsKey
import pl.misek.toafmobile.modules.interfaces.AdvanceControlShowResult
import pl.misek.toafmobile.modules.interfaces.IModuleEwsKeys
import pl.misek.toafmobile.network.UserInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

private data class EwsKeysReadResult(val isn : String?, val password : String?, val keys : List<EwsKey>?)

@Composable
private fun ewsKeyStatusText(status : Int) : String = stringResource(
    when (status) {
        0 -> Res.string.advance_control_ews_keys_status_locked_unused
        1 -> Res.string.advance_control_ews_keys_status_locked_used
        2 -> Res.string.advance_control_ews_keys_status_free_unused
        else -> Res.string.advance_control_ews_keys_status_free_used
    }
)

@Composable
fun AdvanceControlEwsKeys(userInfo : UserInfo, onShowResult : AdvanceControlShowResult) {
    val modulesManager : ModulesManager = koinInject()
    val moduleEws = modulesManager.getCurrentModules().firstOrNull { it.getKoinName() == KoinNames.ModuleR40Ews } as? IModuleEwsKeys
    var keys by remember { mutableStateOf<List<EwsKey>>(emptyList()) }
    var isn by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf<String?>(null) }
    var showProgressDialog by remember { mutableStateOf(false) }
    val readSuccessful = stringResource(Res.string.advance_control_ews_keys_read_successful)
    val readFailed = stringResource(Res.string.advance_control_ews_keys_read_failed)

    AdvanceControlEwsKeysContent(
        keys = keys,
        isn = isn,
        password = password,
        onReadClick = { showProgressDialog = true }
    )

    if (showProgressDialog) {
        ToafProgressDialog(
            action = { EwsKeysReadResult(isn = moduleEws?.readIsn(), password = moduleEws?.readPassword(), keys = moduleEws?.readKeys()) },
            onSuccess = { actionResult ->
                val result = actionResult as? EwsKeysReadResult
                if (result?.keys != null)
                    keys = result.keys
                if (result?.isn != null)
                    isn = result.isn
                if (result?.password != null)
                    password = result.password
                onShowResult(result?.keys != null, readSuccessful, readFailed)
            },
            onError = { onShowResult(false, null, readFailed) },
            onDismissRequest = { showProgressDialog = false }
        )
    }
}

@Composable
private fun AdvanceControlEwsKeysContent(
    keys : List<EwsKey>,
    isn : String?,
    password : String?,
    onReadClick : () -> Unit)
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
                text = stringResource(Res.string.advance_control_ews_keys_title),
                textAlign = TextAlign.Center,
                style = TextStyle(color = Color.White, fontSize = 16.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 2.dp)
            )
            Text(
                text = stringResource(Res.string.advance_control_ews_keys_isn) + ": " + (isn ?: "--"),
                textAlign = TextAlign.Center,
                style = TextStyle(color = Color.White, fontSize = 14.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
            Text(
                text = stringResource(Res.string.advance_control_ews_keys_password) + ": " + (password ?: "--"),
                textAlign = TextAlign.Center,
                style = TextStyle(color = Color.White, fontSize = 14.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )

            Spacer(Modifier.height(8.dp))

            EwsKeysTable(
                keys = keys,
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
                    buttonText = Res.string.advance_control_ews_keys_read,
                    modifier = Modifier.weight(1f),
                    onClick = { onReadClick() }
                )
            }
        }
    }
}

@Composable
private fun EwsKeysTable(keys : List<EwsKey>, modifier : Modifier)
{
    val listState = rememberLazyListState()
    val yes = stringResource(Res.string.advance_control_ews_keys_yes)
    val no = stringResource(Res.string.advance_control_ews_keys_no)
    Column(
        modifier = modifier
            .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
            .background(ToafColors.toafBackground, RoundedCornerShape(6.dp))
    )
    {
        EwsKeysTableRow(
            number = stringResource(Res.string.advance_control_ews_keys_col_number),
            id = stringResource(Res.string.advance_control_ews_keys_col_id),
            status = stringResource(Res.string.advance_control_ews_keys_col_status),
            selfInit = stringResource(Res.string.advance_control_ews_keys_col_selfinit),
            isHeader = true
        )
        HorizontalDivider(color = Color.LightGray)
        if (keys.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.advance_control_ews_keys_no_data),
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
                    itemsIndexed(keys) { index, key ->
                        EwsKeysTableRow(
                            number = key.number.toString(),
                            id = key.id,
                            status = ewsKeyStatusText(key.status),
                            selfInit = if (key.selfInit) yes else no,
                            isHeader = false
                        )
                        if (index < keys.lastIndex)
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
private fun EwsKeysTableRow(number : String, id : String, status : String, selfInit : String, isHeader : Boolean)
{
    val style = TextStyle(
        color = Color.White,
        fontSize = if (isHeader) 13.sp else 14.sp,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    )
    {
        Text(text = number, style = style, modifier = Modifier.weight(0.6f))
        Text(text = id, style = style, modifier = Modifier.weight(2f))
        Text(text = status, style = style, modifier = Modifier.weight(1.6f))
        Text(text = selfInit, style = style, modifier = Modifier.weight(1f))
    }
}

@Preview(showBackground = true, backgroundColor = 0xff000000)
@Composable
fun AdvanceControlEwsKeysPreview()
{
    val sampleKeys = listOf(
        EwsKey(0, "1122334455667788", 3, selfInitDone = true, selfInit = false),
        EwsKey(1, "0000000000000000", 0, selfInitDone = false, selfInit = false),
        EwsKey(2, "AABBCCDDEEFF0011", 2, selfInitDone = true, selfInit = true),
    )
    AdvanceControlEwsKeysContent(
        keys = sampleKeys,
        isn = "1A2B",
        password = "0011223344AA",
        onReadClick = {}
    )
}
