package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.modules.interfaces.IModule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import pl.misek.toafmobile.coding.CodingElement
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import io.sentry.BuildConfig
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.BuildKonfig
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.TopBarModel
import pl.misek.toafmobile.activities.composeModels.dialogs.CodingBackupDialog
import pl.misek.toafmobile.activities.composeModels.dialogs.CodingReadBackupDialog
import pl.misek.toafmobile.activities.composeModels.menus.MenuCoding
import pl.misek.toafmobile.activities.composeModels.menus.ToafVerticalScrollbar
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.snackbar.ToafSnackBarHost
import pl.misek.toafmobile.backup.BackupElement
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.network.ToafApi
import pl.misek.toafmobile.tools.JsonSupport
import pl.misek.toafmobile.tools.Tools.byteArrayToHexString
import pl.misek.toafmobile.tools.Tools.stringToByteArray
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import kotlin.collections.emptyList

private const val tag = "ActivityModuleCodingModel"
private enum class BackupType
{
    None,
    Public,
    Private
}
@Composable
fun ActivityModuleCodingModel(moduleKoinName : KoinNames, onBackClick : () ->Unit) {
    val modulesManager : ModulesManager = koinInject()
    val currentModule = modulesManager.getCurrentModules().firstOrNull {it.getKoinName() == moduleKoinName}
    val ewsModule = modulesManager.getCurrentModules().firstOrNull {it.getKoinName() == KoinNames.ModuleR40Ews }
    val ipkModule = modulesManager.getCurrentModules().firstOrNull {it.getKoinName() == KoinNames.ModuleR40Ipk }
    var showProgressDialog by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var actionType by remember {mutableStateOf (ActionTypeEnum.None)}
    val toafApi : ToafApi = koinInject()
    val scope = rememberCoroutineScope()
    val snackBarHostState = remember { SnackbarHostState() }
    var codingElements by remember { mutableStateOf<List<CodingElement>?>(emptyList()) }
    var backupElements by remember { mutableStateOf<List<BackupElement>?>(emptyList()) }
    var currentCoding by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var isServerCommunication by remember { mutableStateOf(false) }
    var showSaveBackupDialog by remember { mutableStateOf(false) }
    var showReadBackupDialog by remember { mutableStateOf(false) }
    var backupType by remember {mutableStateOf(BackupType.None)}
    val backupLoadSuccessTest = stringResource(Res.string.dialog_backup_read_list_data_loaded)
    val backupLoadFailText = stringResource(Res.string.dialog_backup_read_list_data_corrupted)
    val backupNameTooShortText = stringResource(Res.string.dialog_make_backup_description_to_short)
    val backupSaveSuccessText = stringResource(Res.string.dialog_make_backup_backup_saved)
    val backupSaveFailText = stringResource(Res.string.dialog_make_backup_save_error)
    val writeCodingSuccessText = stringResource(Res.string.message_coding_save_ok)
    val writeCodingFailText = stringResource(Res.string.message_coding_save_not_ok)
    val readCodingFailText = stringResource(Res.string.message_coding_read_not_ok)

    LaunchedEffect(Unit, backupType) {
        if (backupType != BackupType.None) {
            backupElements = withContext(Dispatchers.IO)
            {
                readBackupList(toafApi = toafApi, currentModule, backupType)

            }
            backupType = BackupType.None
            isServerCommunication = false
        }
    }
    LaunchedEffect(Unit)
    {
        action = {
            currentModule?.let {
                val moduleIdentification = it.getIdentification()
                codingElements = JsonSupport.getCodingList(
                    moduleAddress = it.getModuleAddress(),
                    moduleCodingIndex = moduleIdentification?.ci?.toInt() ?: 0
                )
                (currentModule as IModuleCoding).readCoding()
            }
        }
        actionType = ActionTypeEnum.ReadCoding
        showProgressDialog = true
    }
    Scaffold(
        modifier = Modifier
            .padding(all = 0.dp)
            .fillMaxWidth()
            .fillMaxHeight(),
        containerColor = ToafColors.toafBackground,
        snackbarHost = { ToafSnackBarHost(snackBarHostState) },
        topBar = {
            TopBarModel(
                titleResource = Res.string.activity_coding_subtitle,
                onBackClicked = { onBackClick() })
            MenuCoding.Menu(
                onSaveBackupClick = {
                    showSaveBackupDialog = true
                },
                onReadPrivateBackupClick = {
                    backupType = BackupType.Private
                    showReadBackupDialog = true
                },
                onReadPublicBackupClick = {
                    backupType = BackupType.Public
                    showReadBackupDialog = true
                }
            )
        },
        bottomBar =
            {
                Column(modifier = Modifier
                    .padding(1.dp)
                    .background(ToafColors.toafBackground))
                {
                    ToafButtons.GreenButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(1.dp),
                        buttonText = Res.string.activity_coding_button_read,
                        onClick = {
                            action = {
                                currentModule?.let {
                                    val moduleIdentification = it.getIdentification()
                                    codingElements = JsonSupport.getCodingList(
                                        moduleAddress = it.getModuleAddress(),
                                        moduleCodingIndex = moduleIdentification?.ci?.toInt() ?: 0
                                    )
                                    (currentModule as IModuleCoding).readCoding()
                                }
                            }
                            actionType = ActionTypeEnum.ReadCoding
                            showProgressDialog = true
                        }
                    )
                    ToafButtons.RedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(1.dp),
                        buttonText = Res.string.activity_coding_button_save,
                        onClick = {
                            actionType = ActionTypeEnum.WriteCoding
                            action = {
                                (currentModule as IModuleCoding).writeCoding(currentCoding)
                            }
                            showProgressDialog = true
                        })
                }
            },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .background(ToafColors.toafBackground)
            ) {
                CodingList(
                    items = codingElements,
                    currentCoding = currentCoding,
                    onRefreshNeeded = { newCoding ->
                        currentCoding = newCoding.toList()
                    })
            }
            if (showSaveBackupDialog)
            {
                CodingBackupDialog(
                    dataTransfer = isServerCommunication,
                    onCancelClick = { showSaveBackupDialog = false},
                    onSaveClick = { description, isPublic ->
                        if (description.isEmpty()) {
                            showResult(
                                scope = scope,
                                snackBarHostState = snackBarHostState,
                                result = false,
                                onSuccess = null,
                                onFail = backupNameTooShortText
                            )
                        } else {
                            actionType = ActionTypeEnum.SaveCodingBackup
                            action = {
                                doBackup(
                                    toafApi = toafApi,
                                    currentModule = currentModule,
                                    ewsModule = ewsModule,
                                    ipkModule = ipkModule,
                                    currentCoding = currentCoding,
                                    description = description,
                                    isPublic = isPublic
                                )
                            }
                            showProgressDialog = true
                        }
                    }
                )
            }
            if (showReadBackupDialog)
            {
                CodingReadBackupDialog(
                    busy = isServerCommunication,
                    moduleName = currentModule?.getModuleName() ?: "Unknown",
                    items = backupElements,
                    onCancelClick = {
                        backupType = BackupType.None
                        showReadBackupDialog = false
                    },
                    onLoadClick = {backupId ->
                        actionType = ActionTypeEnum.ReadCodingBackup
                        action = { readBackup(toafApi = toafApi, backupId = backupId) }
                        showProgressDialog = true
                        showReadBackupDialog = false
                    }
                )
            }
            if (showProgressDialog)
            {
                ToafProgressDialog(
                    action = { action() },
                    onSuccess = {result ->
                        when(actionType){
                            ActionTypeEnum.ReadCodingBackup -> {
                                val data = (result as? List<*>?)?.filterIsInstance<ByteArray>()
                                val isSuccess = !data.isNullOrEmpty()
                                data?.let {
                                    currentCoding = it.toList()
                                }
                                showResult(
                                    scope = scope,
                                    snackBarHostState = snackBarHostState,
                                    result = isSuccess,
                                    onSuccess = backupLoadSuccessTest,
                                    onFail = backupLoadFailText
                                )
                            }
                            ActionTypeEnum.SaveCodingBackup ->
                            {
                                val success = result as Boolean
                                showResult(
                                    scope = scope,
                                    snackBarHostState = snackBarHostState,
                                    result = success,
                                    onSuccess = backupSaveSuccessText,
                                    onFail = backupSaveFailText
                                )
                                if (success)
                                    showSaveBackupDialog = false
                            }
                            ActionTypeEnum.ReadCoding ->
                            {
                                val data = (result as? List<*>)?.filterIsInstance<ByteArray>()
                                if (data.isNullOrEmpty()) {
                                    showResult(
                                        scope = scope,
                                        snackBarHostState = snackBarHostState,
                                        result = false,
                                        onSuccess = null,
                                        onFail = readCodingFailText
                                    )
                                } else {
                                    val codingToBackup = data.toList()
                                    currentCoding = codingToBackup
                                    // Autobackup w tle, bez okna postepu
                                    scope.launch {
                                        doBackup(
                                            toafApi = toafApi,
                                            currentModule = currentModule,
                                            ewsModule = ewsModule,
                                            ipkModule = ipkModule,
                                            currentCoding = codingToBackup,
                                            description = "Autobackup",
                                            isPublic = false
                                        )
                                    }
                                }
                            }
                            ActionTypeEnum.WriteCoding ->
                            {
                                val success = result as Boolean
                                showResult(
                                    scope = scope,
                                    snackBarHostState = snackBarHostState,
                                    result = success,
                                    onSuccess = writeCodingSuccessText,
                                    onFail = writeCodingFailText
                                )
                            }
                            else -> {}

                        }
                    },
                    onError = {
                        val failText = when (actionType) {
                            ActionTypeEnum.ReadCoding -> readCodingFailText
                            ActionTypeEnum.WriteCoding -> writeCodingFailText
                            ActionTypeEnum.SaveCodingBackup -> backupSaveFailText
                            ActionTypeEnum.ReadCodingBackup -> backupLoadFailText
                            else -> null
                        }
                        failText?.let {
                            showResult(
                                scope = scope,
                                snackBarHostState = snackBarHostState,
                                result = false,
                                onSuccess = null,
                                onFail = it
                            )
                        }
                    },
                    onDismissRequest = {showProgressDialog = false}
                )
            }
        }
    )
}

private suspend fun readBackupList(toafApi :ToafApi, module : IModule?, backupType: BackupType) : List<BackupElement>?
{
    return withContext(Dispatchers.IO)
    {
        val vin = module?.getVIN()
        val moduleInfo = module?.getIdentification()
        val moduleData = buildJsonObject {
            put("name", module?.getModuleName())
            put("ci", moduleInfo?.ci ?: "")
            put("vin", vin ?: "")
        }
        val jsonData = buildJsonObject {
            put("module", moduleData)
            put("toaf_version", BuildKonfig.VERSION_CODE.toString())
        }
        toafApi.getCodingList(jsonData, backupType == BackupType.Public)?.toList()
    }
}

private suspend fun readBackup(toafApi: ToafApi, backupId : Int) : List<ByteArray>?
{
    if (backupId < 0)
    {
        return null
    }
    val data = buildJsonObject {
        put("id", backupId)
        put("toaf_version", BuildKonfig.VERSION_CODE.toString())
    }
    val result = toafApi.getCoding(data)
    val checksum = result?.checksum?.toByte()
    val coding = result?.coding?.split(";".toRegex())?.dropLastWhile { it.isEmpty() }
        ?.toTypedArray()
    val codingList: MutableList<ByteArray> = ArrayList()
    if (coding != null) {
        for (s in coding) codingList.add(stringToByteArray(s))
    }
    var cs: Byte = 0
    for (d in codingList) for (b in d) cs = (cs.toInt() xor b.toInt()).toByte()
    return if (cs == checksum) codingList else null
}

private suspend fun doBackup(toafApi : ToafApi, currentModule : IModule?, ewsModule : IModule?, ipkModule :IModule?, currentCoding: List<ByteArray>?, description: String, isPublic: Boolean): Boolean {

    val tag = "ActivityCodingModel.doBackup"
    var result = false
    var ewsVin = ""
    var ewsOdometer = ""
    var ipkVin = ""
    var ipkOdometer = ""
    var moduleVin = ""
    var moduleOdometer = ""
    var moduleInfo : ModuleInfo?

    ewsModule?.let {
        ewsVin = it.getVIN() ?: ""
        ewsOdometer = it.getOdometer() ?: ""
    }

    ipkModule?.let {
        ipkVin = it.getVIN() ?: ""
        ipkOdometer = it.getOdometer() ?: ""
    }

    currentModule.let {
        moduleInfo = it?.getIdentification()
        moduleVin = it?.getVIN() ?: ""
        moduleOdometer = it?.getOdometer() ?: ""
    }
    val codingData = StringBuilder()
    var cs = 0
    for (d in currentCoding!!)
        for (b in d)
            cs = cs xor b.toInt()
    for (d in currentCoding)
        codingData.append(byteArrayToHexString(d)).append(";")

    try {
        val coding = buildJsonObject {
            put("data", codingData.toString())
            put("checksum", String.format("%02X", cs and 0xFF))
        }
        val dataEWS = buildJsonObject {
            put("vin", ewsVin)
            put("odometer", ewsOdometer)
        }
        val dataIPK = buildJsonObject {
            put("vin", ipkVin)
            put("odometer", ipkOdometer)
        }
        val data = buildJsonObject {
            put("coding", coding)
            put("public", if (isPublic) "Y" else "N")
            put("description", description)
            put("ews", dataEWS)
            put("ipk", dataIPK)
            put("module", moduleInfo?.toJson(currentModule?.getModuleName(), moduleVin, moduleOdometer)?: JsonNull)
            put("toaf_version", BuildKonfig.VERSION_CODE.toString())
        }
        result = toafApi.addCoding(data)
    } catch (ex: Exception) {
        Logger.e {"$tag : ${ex.toString()}"}
    }
    return result
}

@Composable
private fun CodingList(items: List<CodingElement>?, currentCoding: List<ByteArray>, onRefreshNeeded: (List<ByteArray>) -> Unit)
{
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize())
    {
        LazyColumn(
            state = listState,
            modifier = Modifier.background(ToafColors.toafBackground).fillMaxSize(),
            contentPadding = PaddingValues(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        )
        {
            items(items?.size ?: 0) { index ->
                if (items!![index].visible) {
                    CodingListPlaceholder(
                        item = items[index],
                        currentCoding = currentCoding,
                        onRefreshNeeded = onRefreshNeeded
                    )
                }
            }
        }
        Box(
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            ToafVerticalScrollbar(listState = listState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodingListPlaceholder(item : CodingElement, currentCoding : List<ByteArray>, onRefreshNeeded : (List<ByteArray>) -> Unit)
{
    //val codingOptions = item.getCodingValuesNames()
    val codingOptions = item.codingValues
    var expanded by remember { mutableStateOf(false) }
    var currentCodingOption by remember{ mutableStateOf<String?>("")}
    val codingValue = decodeCodingValue(item = item, currentCoding = currentCoding)
    if (codingValue < codingOptions.size)
        currentCodingOption = codingOptions[codingValue].second?.let { stringResource(it)}

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, Color.White),
        modifier = Modifier
            .padding(all = 3.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min))
    {
    Column (
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .background(ToafColors.toafBackground))
        {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(1.dp),
                fontSize = 16.sp,
                text = if (item.description != null ) stringResource(item.description!!) else item.name,
                color = ToafColors.toafTextColorWhite)
            if (item.codingCustomValues?.first == Int.MIN_VALUE)
            {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = currentCodingOption ?: "ERROR",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            CompositionLocalProvider(LocalContentColor provides Color.White) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedLabelColor = Color.White,
                            disabledBorderColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            //focusedIndicatorColor = Color.Transparent,
                            //unfocusedIndicatorColor = Color.Transparent,
                            //disabledIndicatorColor = Color.Transparent
                        ),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        modifier = Modifier
                            .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                            .padding(all = 1.dp)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        codingOptions.forEach { option ->
                            val opt: String? = option.second?.let { resource -> stringResource(resource) }
                            DropdownMenuItem(
                                text = { opt?.let {Text(it)}  },
                                onClick = {
                                    currentCodingOption = opt
                                    // Wartosc bezposrednio z wybranej opcji - teksty moga sie powtarzac lub byc puste
                                    currentCoding.withItemValue(item, option.first)?.let { onRefreshNeeded(it) }
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }
            else
            {
                val min = item.codingCustomValues?.first ?: 0
                val max = item.codingCustomValues?.second ?: 0
                val range = min..max
                val customValue = currentCoding.readItemValue(item) ?: min
               Row(
                   modifier = Modifier
                       .fillMaxWidth()
                       .padding(1.dp),
                   verticalAlignment = Alignment.CenterVertically,
                   horizontalArrangement = Arrangement.Center)
               {
                   IconButton(
                       onClick = {
                           val newCustomValue = (customValue-1).coerceIn(range)
                           currentCoding.withItemValue(item, newCustomValue)?.let { onRefreshNeeded(it) }
                       },
                       enabled = customValue > range.first)
                   {
                       Image(imageVector = Icons.Default.Remove, contentDescription = "-", colorFilter = ColorFilter.tint(Color.White))
                   }
                   OutlinedTextField(
                       value = customValue.toString(),
                       onValueChange = { input ->
                           if (input.all { it.isDigit() }) {
                               val newValue = input.toIntOrNull() ?: range.first
                               if (newValue <= range.last)
                               {
                                   val newCustomValue = (newValue).coerceIn(range)
                                   currentCoding.withItemValue(item, newCustomValue)?.let { onRefreshNeeded(it) }
                               }
                           }
                       },
                       singleLine = true,
                       keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                       modifier = Modifier
                           .width(100.dp)
                           .padding(1.dp),
                       textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = Color.White),
                   )
                   IconButton(
                       onClick = {
                           val newCustomValue = (customValue + 1).coerceIn(range)
                           currentCoding.withItemValue(item, newCustomValue)?.let { onRefreshNeeded(it) }
                        },
                       enabled =  customValue < range.last)
                   {
                       Image(imageVector = Icons.Default.Add, contentDescription = "+", colorFilter = ColorFilter.tint(Color.White))
                   }
               }

            }
        }
    }
}

private fun decodeCodingValue(item : CodingElement, currentCoding: List<ByteArray>?) : Int
{
    val value = currentCoding?.readItemValue(item) ?: return 0
    val index = item.codingValues.indexOfFirst { x ->x.first == value }
    return index.coerceAtLeast(0)
}

/** Wartosc elementu kodowania, null gdy brak bloku wskazanego w JSON */
private fun List<ByteArray>.readItemValue(item : CodingElement) : Int? =
    getOrNull(item.block)?.extractWithMask(item.byteInBlock, item.mask)

/** Kodowanie z nowa wartoscia elementu, null gdy brak bloku wskazanego w JSON */
private fun List<ByteArray>.withItemValue(item : CodingElement, value : Int) : List<ByteArray>?
{
    val block = getOrNull(item.block) ?: return null
    return toMutableList().also { it[item.block] = block.setBits(item.mask, value, item.byteInBlock) }
}

// Liczba bajtow pola wynika z maski (traktowanej bez znaku), bajty w kolejnosci big-endian
private fun maskBytes(mask: Int): Int {
    val unsignedMask = mask.toLong() and 0xFFFFFFFFL
    return when {
        unsignedMask <= 0xFF -> 1
        unsignedMask <= 0xFFFF -> 2
        unsignedMask <= 0xFFFFFF -> 3
        else -> 4
    }
}

private fun ByteArray.extractWithMask(pos: Int, mask :Int) :Int
{
    var rawValue = 0
    for (i in 0 until maskBytes(mask))
    {
        val index = pos + i
        val byteValue = if (index in indices) this[index].toInt() and 0xFF else 0
        rawValue = (rawValue shl 8) or byteValue
    }
    return (rawValue and mask) ushr mask.countTrailingZeroBits()
}

/**
 * Zwraca kopie z wartoscia [data] wpisana pod maske [mask] od bajtu [position].
 * Gdy pole nie miesci sie w bloku albo wartosc nie miesci sie w masce, zwraca niezmieniona kopie.
 */
fun ByteArray.setBits(mask: Int, data: Int, position: Int): ByteArray {
    val output = this.copyOf()
    if (mask == 0) return output

    val bytesNeeded = maskBytes(mask)
    if (position < 0 || position + bytesNeeded > output.size) {
        Logger.e { "$tag [setBits] field out of block: position=$position, bytes=$bytesNeeded, size=${output.size}" }
        return output
    }
    val shift = mask.countTrailingZeroBits()
    val maxValue = (mask.toLong() and 0xFFFFFFFFL) ushr shift
    val unsignedData = data.toLong() and 0xFFFFFFFFL
    if (unsignedData > maxValue) {
        Logger.e { "$tag [setBits] value 0x${unsignedData.toString(16)} does not fit mask 0x${(mask.toLong() and 0xFFFFFFFFL).toString(16)}" }
        return output
    }

    var currentMask = mask
    var currentData = (data shl shift) and mask
    for (currentPos in position + bytesNeeded - 1 downTo position) {
        val maskByte = currentMask and 0xFF
        if (maskByte != 0) {
            val originalByte = output[currentPos].toInt() and 0xFF
            output[currentPos] = ((originalByte and maskByte.inv()) or (currentData and maskByte)).toByte()
        }
        currentMask = currentMask ushr 8
        currentData = currentData ushr 8
    }
    return output
}

@Preview(showBackground = true, backgroundColor = 0x0FF00000)
@Composable
fun ActivityCodingModulePreview()
{
    ActivityModuleCodingModel(
        moduleKoinName = KoinNames.ModuleR40Bcu,
        onBackClick = {})
}