package com.regnius.photoprism.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.regnius.photoprism.BuildConfig
import com.regnius.photoprism.R
import com.regnius.photoprism.core.pro.Funding
import com.regnius.photoprism.core.settings.SlideshowInterval
import com.regnius.photoprism.core.settings.StartTab
import com.regnius.photoprism.core.settings.ThemeMode
import com.regnius.photoprism.core.settings.VideoPlaybackMode
import com.regnius.photoprism.core.ui.GridDensity

/**
 * Phase 2.11 — 시작 화면 · 그리드 밀도 · 테마 · 슬라이드쇼 · 캐시 관리 · 로그아웃.
 *
 * 여기 있는 값은 전부 **기기 로컬**이다. 서버로 동기화하지 않는다 — §1 의
 * 프라이버시 원칙과 같은 이유로, 기기를 바꾸면 다시 고르면 그만인 값까지
 * 서버 왕복을 만들 이유가 없다.
 *
 * 원래 하단 "메뉴" 탭의 본문이었는데, 상단 앱바의 오버플로(⋮)에서 여는 별도
 * 화면으로 옮겼다 — 하단 탭은 "라이브러리를 어떤 축으로 훑을 것인가"를 고르는
 * 자리라, 앱 환경설정이 그 넷 중 하나로 앉아 있는 게 어색했다. 그래서 이제
 * 자기 제목 표시줄과 뒤로가기를 직접 들고 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        SettingsList(contentPadding = padding, viewModel = viewModel)
    }
}

@Composable
private fun SettingsList(
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val startTab by viewModel.startTab.collectAsStateWithLifecycle()
    val gridDensity by viewModel.gridDensity.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val videoPlaybackMode by viewModel.videoPlaybackMode.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val slideshowInterval by viewModel.slideshowInterval.collectAsStateWithLifecycle()
    val slideshowKenBurns by viewModel.slideshowKenBurns.collectAsStateWithLifecycle()
    val slideshowShuffle by viewModel.slideshowShuffle.collectAsStateWithLifecycle()

    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { SectionHeader(stringResource(R.string.settings_section_display)) }
        item {
            SettingsRow(
                icon = Icons.Filled.Home,
                title = stringResource(R.string.settings_start_screen),
                value = startTab.label(),
                onClick = { dialog = SettingsDialog.StartTab },
            )
        }
        item {
            SettingsRow(
                icon = Icons.Filled.GridView,
                title = stringResource(R.string.settings_grid_density),
                value = gridDensity.label(),
                onClick = { dialog = SettingsDialog.GridDensity },
            )
        }
        item {
            SettingsRow(
                icon = Icons.Filled.Brightness6,
                title = stringResource(R.string.settings_theme),
                value = themeMode.label(),
                onClick = { dialog = SettingsDialog.Theme },
            )
        }

        item { HorizontalDivider() }
        item { SectionHeader(stringResource(R.string.settings_section_video)) }
        item {
            SettingsRow(
                icon = Icons.Filled.Videocam,
                title = stringResource(R.string.settings_playback_mode),
                value = videoPlaybackMode.label(),
                onClick = { dialog = SettingsDialog.VideoPlaybackMode },
            )
        }

        item { HorizontalDivider() }
        item { SectionHeader(stringResource(R.string.settings_section_slideshow)) }
        item {
            SettingsRow(
                icon = Icons.Filled.Timer,
                title = stringResource(R.string.settings_slide_duration),
                value = slideshowInterval.label(),
                onClick = { dialog = SettingsDialog.SlideshowInterval },
            )
        }
        item {
            SettingsToggleRow(
                icon = Icons.Filled.Slideshow,
                title = stringResource(R.string.settings_ken_burns),
                value = stringResource(R.string.settings_ken_burns_desc),
                checked = slideshowKenBurns,
                onCheckedChange = viewModel::setSlideshowKenBurns,
            )
        }
        item {
            SettingsToggleRow(
                icon = Icons.Filled.Shuffle,
                title = stringResource(R.string.settings_shuffle),
                value = stringResource(R.string.settings_shuffle_desc),
                checked = slideshowShuffle,
                onCheckedChange = viewModel::setSlideshowShuffle,
            )
        }

        item { HorizontalDivider() }
        item { SectionHeader(stringResource(R.string.settings_section_storage)) }
        // §17.40 — 썸네일과 오프라인 목록을 **한 줄로 합쳤다.** 둘은 같이
        // 있어야만 쓸모가 있어서, 따로 지우면 반쪽 상태만 남았다.
        item {
            SettingsRow(
                icon = Icons.Filled.DeleteSweep,
                title = stringResource(R.string.settings_clear_cache),
                value = cacheSizeBytes
                    ?.let { stringResource(R.string.settings_clear_cache_desc, formatBytes(it)) }
                    ?: stringResource(R.string.settings_calculating),
                onClick = { dialog = SettingsDialog.ClearCacheConfirm },
            )
        }

        item { HorizontalDivider() }
        item { SectionHeader(stringResource(R.string.settings_section_sponsor)) }
        item {
            SettingsRow(
                icon = Icons.Filled.VolunteerActivism,
                title = "GitHub Sponsors",
                value = stringResource(R.string.settings_sponsor_desc),
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Funding.GITHUB_SPONSORS)))
                },
            )
        }

        item { HorizontalDivider() }
        item { SectionHeader(stringResource(R.string.settings_section_account)) }
        item {
            SettingsRow(
                icon = Icons.Filled.Logout,
                title = stringResource(R.string.settings_sign_out),
                value = null,
                onClick = { dialog = SettingsDialog.LogoutConfirm },
            )
        }

        item {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    "${stringResource(R.string.app_name_full)} · ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    when (dialog) {
        SettingsDialog.StartTab -> RadioDialog(
            title = stringResource(R.string.settings_start_screen),
            options = StartTab.entries,
            selected = startTab,
            label = { it.label() },
            onSelect = { viewModel.setStartTab(it); dialog = null },
            onDismiss = { dialog = null },
        )

        SettingsDialog.GridDensity -> RadioDialog(
            title = stringResource(R.string.settings_grid_density),
            options = GridDensity.entries,
            selected = gridDensity,
            label = { it.label() },
            onSelect = { viewModel.setGridDensity(it); dialog = null },
            onDismiss = { dialog = null },
        )

        SettingsDialog.Theme -> RadioDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = themeMode,
            label = { it.label() },
            onSelect = { viewModel.setThemeMode(it); dialog = null },
            onDismiss = { dialog = null },
        )

        SettingsDialog.VideoPlaybackMode -> RadioDialog(
            title = stringResource(R.string.settings_playback_mode),
            options = VideoPlaybackMode.entries,
            selected = videoPlaybackMode,
            label = { it.label() },
            onSelect = { viewModel.setVideoPlaybackMode(it); dialog = null },
            onDismiss = { dialog = null },
        )

        SettingsDialog.SlideshowInterval -> RadioDialog(
            title = stringResource(R.string.settings_slide_duration),
            options = SlideshowInterval.entries,
            selected = slideshowInterval,
            label = { it.label() },
            onSelect = { viewModel.setSlideshowInterval(it); dialog = null },
            onDismiss = { dialog = null },
        )

        SettingsDialog.LogoutConfirm -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_sign_out)) },
            text = { Text(stringResource(R.string.settings_sign_out_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(); dialog = null }) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) } },
        )

        SettingsDialog.ClearCacheConfirm -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_clear_cache)) },
            text = {
                Text(
                    stringResource(R.string.settings_clear_cache_body),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCache(); dialog = null }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) } },
        )

        null -> Unit
    }
}

private enum class SettingsDialog {
    StartTab, GridDensity, Theme, VideoPlaybackMode, SlideshowInterval,
    LogoutConfirm, ClearCacheConfirm
}

/** `1234` -> `"1 KB"`, `1234567` -> `"1.2 MB"`. */
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun StartTab.label() = stringResource(
    when (this) {
        StartTab.ALBUMS -> R.string.start_screen_albums
        StartTab.PHOTOS -> R.string.start_screen_photos
        StartTab.FAVORITES -> R.string.start_screen_favorites
    },
)

@Composable
private fun GridDensity.label() = stringResource(
    when (this) {
        GridDensity.COMFORTABLE -> R.string.density_wide
        GridDensity.DEFAULT -> R.string.density_default
        GridDensity.COMPACT -> R.string.density_dense
        GridDensity.VERY_COMPACT -> R.string.density_densest
    },
)

@Composable
private fun ThemeMode.label() = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun SlideshowInterval.label() =
    pluralStringResource(R.plurals.settings_seconds, seconds, seconds)

@Composable
private fun VideoPlaybackMode.label() = stringResource(
    when (this) {
        VideoPlaybackMode.AUTO -> R.string.playback_auto
        VideoPlaybackMode.FORCE_WEBDAV -> R.string.playback_original
    },
)

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 20.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 값이 켬/끔 둘뿐인 항목. [SettingsRow] 처럼 대화상자를 띄우면 두 번 탭해야
 * 하는데, 그만한 선택지가 아니라 그 자리에서 바로 뒤집는다.
 */
@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 20.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> RadioDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // §17.43 — **스크롤이 있어야 한다.** 가로 모드에서는 대화상자가
            // 쓸 수 있는 높이가 절반 이하로 줄어, 스크롤이 없으면 뒤쪽 항목이
            // 그냥 잘려 나간다 — 그리드 밀도의 "아주 촘촘하게"(4번째)와
            // 슬라이드쇼 간격의 뒤쪽 초들이 화면에서 사라졌다. 항목이 안 보이면
            // 없는 기능이 된다.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Text(label(option), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
