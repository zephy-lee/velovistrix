package com.regnius.photoprism

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.regnius.photoprism.core.auth.SessionStore
import com.regnius.photoprism.core.designsystem.VeloVistrixTheme
import com.regnius.photoprism.core.settings.AppSettingsRepository
import com.regnius.photoprism.core.settings.ThemeMode
import com.regnius.photoprism.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var settingsRepository: AppSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val session by sessionStore.session.collectAsState()
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val useDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            VeloVistrixTheme(darkTheme = useDarkTheme) {
                // 세션이 "쓸 수 있는" 상태인지로 판단한다. public 모드에서는
                // accessToken 이 비어 있는 것이 정상이라 토큰 유무로 보면 안 된다.
                AppNavigation(isAuthenticated = session?.isUsable == true)
            }
        }
    }
}
