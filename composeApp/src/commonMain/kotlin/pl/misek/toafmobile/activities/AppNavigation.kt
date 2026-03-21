package pl.misek.toafmobile.activities

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import pl.misek.toafmobile.activities.composeModels.activities.ActivityDiagnosticModel
import pl.misek.toafmobile.activities.composeModels.activities.ActivityMainModel
import pl.misek.toafmobile.activities.composeModels.activities.ActivityModuleModel
import pl.misek.toafmobile.koin.KoinNames
import androidx.navigation.toRoute
import pl.misek.toafmobile.activities.composeModels.activities.ActivityControlModel
import pl.misek.toafmobile.activities.composeModels.activities.ActivityEepromProgrammingModel
import pl.misek.toafmobile.activities.composeModels.activities.ActivityFlashProgrammingModel
import pl.misek.toafmobile.activities.composeModels.activities.ActivityLiveDataModel
import pl.misek.toafmobile.activities.composeModels.activities.ActivityModuleAdvanceCodingModel
import pl.misek.toafmobile.activities.composeModels.activities.ActivityModuleCodingModel
import kotlin.reflect.typeOf

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ComposableMain
    )
    {
        composable<ComposableMain>{
            ActivityMainModel(
                onClickDiagnosticButton = {navController.navigate(ComposableDiagnostic)},
                onDeviceChanged = {},
                0)
        }
        composable<ComposableDiagnostic>{
            ActivityDiagnosticModel(
                onBackClick = {navController.popBackStack()},
                    onClick = {koinName -> navController.navigate(ComposableModule(koinName.name))}
            )
        }
        composable<ComposableModule>(){ backStackEntry ->
            val args = backStackEntry.toRoute<ComposableModule>()
            ActivityModuleModel(
                moduleKoinName = KoinNames.valueOf(args.moduleKoinName),
                onCodingClick = {navController.navigate(ComposableCoding(args.moduleKoinName))},
                onCodingAdvanceClick = {navController.navigate(ComposableAdvanceCoding(args.moduleKoinName))},
                onLiveDataClick = {navController.navigate(ComposableLiveData(args.moduleKoinName))},
                onControlClick = {navController.navigate(ComposableControl(args.moduleKoinName))},
                onEepromProgrammingClick = {navController.navigate(ComposableEeprom(args.moduleKoinName))},
                onFlashProgrammingClick = {navController.navigate(ComposableFlash(args.moduleKoinName))},
                onBackClick = { navController.popBackStack()})
        }
        composable<ComposableCoding> {backStackEntry ->
            val args = backStackEntry.toRoute<ComposableCoding>()
            ActivityModuleCodingModel(
                moduleKoinName = KoinNames.valueOf(args.moduleKoinName),
                onBackClick = { navController.popBackStack()})
        }

        composable<ComposableAdvanceCoding> {backStackEntry ->
            val args = backStackEntry.toRoute<ComposableAdvanceCoding>()
            ActivityModuleAdvanceCodingModel(
                moduleKoinName = KoinNames.valueOf(args.moduleKoinName),
                onBackClick = { navController.popBackStack()}
            )
        }
        composable<ComposableLiveData> {backStackEntry ->
            val args = backStackEntry.toRoute<ComposableLiveData>()
            ActivityLiveDataModel(
                moduleKoinName = KoinNames.valueOf(args.moduleKoinName),
                onBackClick = { navController.popBackStack()})
        }
        composable<ComposableControl> { backStackEntry ->
            val args = backStackEntry.toRoute<ComposableControl>()
            ActivityControlModel(
                moduleKoinName = KoinNames.valueOf(args.moduleKoinName),
                onBackClick = { navController.popBackStack()})
        }
        composable<ComposableEeprom> { backStackEntry ->
            val args = backStackEntry.toRoute<ComposableEeprom>()
            ActivityEepromProgrammingModel(
                moduleKoinName = KoinNames.valueOf(args.moduleKoinName),
                onBackClick = { navController.popBackStack()})
        }
        composable<ComposableFlash> { backStackEntry ->
            val args = backStackEntry.toRoute<ComposableFlash>()
            ActivityFlashProgrammingModel(
                moduleKoinName = KoinNames.valueOf(args.moduleKoinName),
                onBackClick = { navController.popBackStack()})
        }
    }
}

@Composable
fun EnumType(x0: Class<KoinNames>) {
    TODO("Not yet implemented")
}

@Serializable
object ComposableDiagnostic
@Serializable
object ComposableMain
@Serializable
data class ComposableModule(val moduleKoinName : String)
@Serializable
data class ComposableCoding(val moduleKoinName :String)
@Serializable
data class ComposableAdvanceCoding(val moduleKoinName :String)
@Serializable
data class ComposableLiveData(val moduleKoinName :String)
@Serializable
data class ComposableControl(val moduleKoinName :String)
@Serializable
data class ComposableEeprom(val moduleKoinName :String)
@Serializable
data class ComposableFlash(val moduleKoinName :String)