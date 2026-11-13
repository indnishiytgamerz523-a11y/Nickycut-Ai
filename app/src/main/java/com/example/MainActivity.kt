package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.StudioAppContainer
import com.example.core.configureAppCheck
import com.example.ui.components.NickyCutBottomBar
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.AiScriptWriterScreen
import com.example.ui.screens.AiVideoEditorScreen
import com.example.ui.screens.AllToolsScreen
import com.example.ui.screens.AudioEditorScreen
import com.example.ui.screens.AutoCaptionsScreen
import com.example.ui.screens.BackgroundRemoverScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.KineticTypographyScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.ScriptToVideoScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TextToVoiceScreen
import com.example.ui.screens.VideoEditorScreen
import com.example.ui.screens.ViralEffectsScreen
import com.example.ui.theme.NickyCutTheme
import com.example.ui.theme.ObsidianBg

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        val container = StudioAppContainer(applicationContext)

        setContent {
            NickyCutTheme {
                NickyCutAppRoot(container = container)
            }
        }
    }
}

@Composable
fun NickyCutAppRoot(container: StudioAppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val topLevelRoute = remember(currentDestination) {
        when {
            currentDestination?.hasRoute<AppRoute.Home>() == true -> AppRoute.Home
            currentDestination?.hasRoute<AppRoute.Projects>() == true -> AppRoute.Projects
            currentDestination?.hasRoute<AppRoute.Tools>() == true -> AppRoute.Tools
            currentDestination?.hasRoute<AppRoute.Settings>() == true -> AppRoute.Settings
            else -> null
        }
    }

    fun navigateToRoute(route: AppRoute) {
        val isTopLevel = route is AppRoute.Home || route is AppRoute.Projects || route is AppRoute.Tools || route is AppRoute.Settings
        if (isTopLevel) {
            navController.navigate(route) {
                popUpTo(AppRoute.Home) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(route)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        bottomBar = {
            if (topLevelRoute != null) {
                NickyCutBottomBar(
                    currentRoute = topLevelRoute,
                    onNavigate = { navigateToRoute(it) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Splash,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable<AppRoute.Splash> {
                SplashScreen(
                    onFinished = {
                        navController.navigate(AppRoute.Home) {
                            popUpTo(AppRoute.Splash) { inclusive = true }
                        }
                    }
                )
            }

            composable<AppRoute.Home> {
                HomeScreen(
                    projectRepository = container.projectRepository,
                    onNavigate = { navigateToRoute(it) }
                )
            }

            composable<AppRoute.Tools> {
                AllToolsScreen(
                    onNavigate = { navigateToRoute(it) }
                )
            }

            composable<AppRoute.Projects> {
                ProjectsScreen(
                    projectRepository = container.projectRepository,
                    onNavigate = { navigateToRoute(it) }
                )
            }

            composable<AppRoute.Settings> {
                SettingsScreen(
                    settingsRepository = container.settingsRepository
                )
            }

            composable<AppRoute.VideoEditor> { entry ->
                val args = entry.toRoute<AppRoute.VideoEditor>()
                VideoEditorScreen(
                    projectId = args.projectId,
                    prefillJson = args.prefillJson,
                    projectRepository = container.projectRepository,
                    onBack = { navController.popBackStack() },
                    onExport = { payload -> navController.navigate(AppRoute.Export(payload)) }
                )
            }

            composable<AppRoute.KineticTypography> {
                KineticTypographyScreen(
                    onBack = { navController.popBackStack() },
                    onExport = { payload -> navController.navigate(AppRoute.Export(payload)) }
                )
            }

            composable<AppRoute.AutoCaptions> {
                AutoCaptionsScreen(
                    geminiRepository = container.geminiRepository,
                    onBack = { navController.popBackStack() },
                    onExport = { payload -> navController.navigate(AppRoute.Export(payload)) }
                )
            }

            composable<AppRoute.ScriptToVideo> { entry ->
                val args = entry.toRoute<AppRoute.ScriptToVideo>()
                ScriptToVideoScreen(
                    initialScript = args.initialScript,
                    onBack = { navController.popBackStack() },
                    onExport = { payload -> navController.navigate(AppRoute.Export(payload)) }
                )
            }

            composable<AppRoute.TextToVoice> { entry ->
                val args = entry.toRoute<AppRoute.TextToVoice>()
                TextToVoiceScreen(
                    initialText = args.initialText,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<AppRoute.ViralEffects> {
                ViralEffectsScreen(
                    onBack = { navController.popBackStack() },
                    onExport = { payload -> navController.navigate(AppRoute.Export(payload)) }
                )
            }

            composable<AppRoute.AudioEditor> {
                AudioEditorScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable<AppRoute.BackgroundRemover> {
                BackgroundRemoverScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable<AppRoute.AiScriptWriter> {
                AiScriptWriterScreen(
                    geminiRepository = container.geminiRepository,
                    projectRepository = container.projectRepository,
                    onBack = { navController.popBackStack() },
                    onOpenScriptToVideo = { script ->
                        navController.navigate(AppRoute.ScriptToVideo(initialScript = script))
                    },
                    onOpenTextToVoice = { text ->
                        navController.navigate(AppRoute.TextToVoice(initialText = text))
                    }
                )
            }

            composable<AppRoute.AiVideoEditor> {
                AiVideoEditorScreen(
                    geminiRepository = container.geminiRepository,
                    onBack = { navController.popBackStack() },
                    onApplyTimelineToEditor = { timelineJson ->
                        navController.navigate(AppRoute.VideoEditor(prefillJson = timelineJson))
                    }
                )
            }

            composable<AppRoute.Export> { entry ->
                val args = entry.toRoute<AppRoute.Export>()
                ExportScreen(
                    exportPayloadJson = args.exportPayloadJson,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
