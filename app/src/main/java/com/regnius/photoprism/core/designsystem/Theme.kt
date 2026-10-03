package com.regnius.photoprism.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * 갤러리 앱의 색은 사진을 방해하지 않아야 한다.
 *
 * 채도 높은 브랜드 컬러를 크게 쓰면 썸네일 그리드에서 사진 색과 싸운다.
 * 그래서 표면은 중성 회색 계열로 두고, 강조색은 작은 요소(선택 표시, 진행
 * 인디케이터)에만 쓴다.
 */
private val Accent = Color(0xFF4FC3F7)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00344A),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE2E6EA),
    surfaceVariant = Color(0xFF1B2026),
    onSurfaceVariant = Color(0xFFB9C0C7),
    background = Color(0xFF0B0E11),
    onBackground = Color(0xFFE2E6EA),
    outline = Color(0xFF3A424B),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00668B),
    onPrimary = Color.White,
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFEDEFF2),
    onSurfaceVariant = Color(0xFF41484D),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF191C1E),
    outline = Color(0xFF71787E),
)

private val AppTypography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = FontFamily.Default),
        // 사진 메타데이터(노출값, 파일명)는 자릿수 정렬이 읽기에 유리하다.
        labelSmall = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
        ),
    )
}

@Composable
fun VeloVistrixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // 엣지 투 엣지라 상태바 아이콘이 앱 배경 위에 그려진다.
            // 라이트 테마면 아이콘을 어둡게, 다크면 밝게 해야 보인다.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
    ) {
        // 모든 화면에 배경을 보장한다.
        //
        // 이게 없으면 Scaffold 를 쓰지 않는 화면(로그인)이 투명한 채로 남아
        // 액티비티의 창 배경이 그대로 비친다. 창 배경과 Compose 색 구성이
        // 어긋나 있으면 **어두운 배경 위에 어두운 글자**가 되어 아무것도 안
        // 보인다. 실제로 그 버그가 있었다.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
