package app.lumira.wallpapers

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.lumira.wallpapers.data.prefs.Settings
import app.lumira.wallpapers.data.prefs.SettingsRepository
import app.lumira.wallpapers.di.ServiceLocator
import app.lumira.wallpapers.enhance.Notifications
import app.lumira.wallpapers.ui.nav.LumiraNavHost
import app.lumira.wallpapers.ui.theme.LumiraTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Only required for the foreground enhancement notification.
            runCatching {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 11)
            }
        }
        Notifications.ensureChannels(this)

        val settingsFlow: StateFlow<Settings> =
            ServiceLocator.settings(this).settings.stateIn(
                lifecycleScope,
                SharingStarted.Eagerly,
                Settings(),
            )

        setContent {
            val settings by settingsFlow.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.darkMode) {
                Settings.DarkMode.SYSTEM -> systemDark
                Settings.DarkMode.LIGHT -> false
                Settings.DarkMode.DARK -> true
            }

            LumiraTheme(
                darkTheme = dark,
                dynamicColor = settings.dynamicColor,
                amoled = settings.amoled,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AnimatedContent(
                        targetState = Unit,
                        transitionSpec = { fadeIn(tween(420)) togetherWith fadeOut(tween(200)) },
                        label = "appRoot",
                    ) {
                        LumiraNavHost()
                    }
                }
            }
        }
    }
}
