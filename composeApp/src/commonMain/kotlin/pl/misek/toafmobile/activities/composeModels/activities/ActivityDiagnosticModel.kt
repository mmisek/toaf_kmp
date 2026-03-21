package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.misek.toafmobile.modules.interfaces.IModule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafComposeElements
import pl.misek.toafmobile.activities.composeModels.TopBarModel
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.enums.CarType
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun ActivityDiagnosticModel(onBackClick :() -> Unit, onClick: (KoinNames) -> Unit) {
    val database : Database = koinInject()
    val modulesManager : ModulesManager = koinInject()
    var isLoading by remember { mutableStateOf(false) }
    var subtitle by remember { mutableStateOf<StringResource?>(null) }

    LaunchedEffect(true) {
        val carType = database.getIntSettingValueByName(DatabaseSettingFields.TOAF_CURRENT_CAR_TYPE)
        subtitle = CarType.entries.find { it.value == carType }?.labelRes
    }
    Scaffold(
        modifier = Modifier
            .padding(all = 0.dp)
            .fillMaxWidth()
            .fillMaxHeight(),
        containerColor = ToafColors.toafBackground,
        topBar = {
            TopBarModel(
                titleResource = Res.string.activity_diagnostic_title,
                subTitle = subtitle,
                onBackClicked = { onBackClick()})
        },
        content = { paddingValues ->
            if (isLoading)
            {
                ToafComposeElements.LargeProgressIndicator()
            }
            else
            {
                Column(modifier = Modifier
                    .padding(paddingValues)
                    .background(ToafColors.toafBackground))
                {
                    ModulesGrid(modulesManager.getCurrentModules(), onClick = onClick)
                }
            }
        }
    )
}
@Composable
fun ModulesGrid(modules : List<IModule>, onClick : (index : KoinNames) -> Unit)
{
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val columnCount = if (isLandscape) 4 else 2
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            contentPadding = PaddingValues(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(modules.size) { index ->
                ToafButtons.ImageButton(
                    moduleName = modules[index].getModuleName(),
                    moduleDescriptor = modules[index].getModuleDescription(),
                    buttonImage = modules[index].getImageResource(),
                    onClick = { onClick(modules[index].getKoinName()) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.5f)
                )
            }
        }
    }
}
@Preview(showBackground = true, backgroundColor = 0x0FF00000)
@Composable
fun ActivityDiagnosticModelPreview()
{
    ActivityDiagnosticModel({},{})
}
