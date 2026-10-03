package com.regnius.photoprism.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.regnius.photoprism.core.model.AlbumSort
import androidx.datastore.preferences.preferencesDataStore
import com.regnius.photoprism.core.ui.GridDensity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/** 앱을 열었을 때 처음 보이는 탭. 문자열로 저장해 [core.settings] 가 네비게이션 계층을 몰라도 되게 한다. */
enum class StartTab { ALBUMS, PHOTOS, FAVORITES }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 동영상 재생 방식. §17.1 — 일부 서버에서 트랜스코딩 스트리밍이 실패하는
 * 문제의 워크어라운드로, WebDAV로 원본을 직접 재생하는 경로를 강제할 수 있게
 * 한다.
 */
enum class VideoPlaybackMode { AUTO, FORCE_WEBDAV }

/**
 * 슬라이드쇼에서 한 장이 머무는 시간 (Phase 3.1).
 *
 * 자유 입력이 아니라 고정 선택지인 이유는, 이 값이 Ken Burns 애니메이션의
 * 지속 시간이기도 해서다 — 1초 같은 값을 넣을 수 있게 하면 화면이 계속
 * 튀는 느낌이 되고, 반대로 아주 큰 값은 애니메이션이 멈춘 것처럼 보인다.
 * 실제로 쓸 만한 구간만 남겼다.
 */
enum class SlideshowInterval(val seconds: Int) {
    S3(3), S5(5), S8(8), S15(15), S30(30);

    val millis: Long get() = seconds * 1000L
}

/**
 * Phase 2.11 설정 화면이 다루는 값들.
 *
 * 전부 로컬 기기 설정이라 서버로 전송되지 않는다 — §1 개인정보 원칙과 같은 이유로
 * 굳이 서버 동기화를 만들지 않는다. 기기를 바꾸면 다시 고르면 된다.
 */
@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.settingsDataStore

    /**
     * 앨범 목록의 정렬 기준과 "즐겨찾기 먼저". §17.45
     *
     * 예전에는 화면의 UI 상태에만 들고 있어서, 앨범 탭을 떠났다 오면 ViewModel
     * 이 새로 만들어지며 **기본값으로 되돌아갔다.** 사용자가 매번 다시 골라야
     * 했다 — 목록을 보는 방식은 한 번 정하면 그대로 두고 싶은 종류의 설정이다.
     */
    val albumSort: Flow<AlbumSort> = store.data.map { prefs ->
        prefs[KEY_ALBUM_SORT]?.let { runCatching { AlbumSort.valueOf(it) }.getOrNull() }
            ?: AlbumSort.DEFAULT
    }

    suspend fun setAlbumSort(sort: AlbumSort) {
        store.edit { it[KEY_ALBUM_SORT] = sort.name }
    }

    val albumFavoritesFirst: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_ALBUM_FAVORITES_FIRST] ?: false
    }

    suspend fun setAlbumFavoritesFirst(enabled: Boolean) {
        store.edit { it[KEY_ALBUM_FAVORITES_FIRST] = enabled }
    }

    val startTab: Flow<StartTab> = store.data.map { prefs ->
        prefs[KEY_START_TAB]?.let { runCatching { StartTab.valueOf(it) }.getOrNull() } ?: StartTab.ALBUMS
    }

    suspend fun setStartTab(tab: StartTab) {
        store.edit { it[KEY_START_TAB] = tab.name }
    }

    val gridDensity: Flow<GridDensity> = store.data.map { prefs ->
        prefs[KEY_GRID_DENSITY]?.let { runCatching { GridDensity.valueOf(it) }.getOrNull() } ?: GridDensity.DEFAULT
    }

    suspend fun setGridDensity(density: GridDensity) {
        store.edit { it[KEY_GRID_DENSITY] = density.name }
    }

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME_MODE] = mode.name }
    }

    val videoPlaybackMode: Flow<VideoPlaybackMode> = store.data.map { prefs ->
        prefs[KEY_VIDEO_PLAYBACK_MODE]?.let { runCatching { VideoPlaybackMode.valueOf(it) }.getOrNull() }
            ?: VideoPlaybackMode.AUTO
    }

    suspend fun setVideoPlaybackMode(mode: VideoPlaybackMode) {
        store.edit { it[KEY_VIDEO_PLAYBACK_MODE] = mode.name }
    }

    val slideshowInterval: Flow<SlideshowInterval> = store.data.map { prefs ->
        prefs[KEY_SLIDESHOW_INTERVAL]?.let { runCatching { SlideshowInterval.valueOf(it) }.getOrNull() }
            ?: SlideshowInterval.S5
    }

    suspend fun setSlideshowInterval(interval: SlideshowInterval) {
        store.edit { it[KEY_SLIDESHOW_INTERVAL] = interval.name }
    }

    /**
     * Ken Burns(느린 확대/이동) 효과. 기본값을 켜짐으로 둔다 — 정지 사진이
     * 몇 초씩 완전히 멈춰 있으면 화면이 꺼진 것처럼 보인다는 게 이 효과가
     * 슬라이드쇼의 사실상 표준이 된 이유다. 취향과 배터리를 이유로 끌 수 있게만
     * 해 둔다.
     */
    val slideshowKenBurns: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_SLIDESHOW_KEN_BURNS] ?: true
    }

    suspend fun setSlideshowKenBurns(enabled: Boolean) {
        store.edit { it[KEY_SLIDESHOW_KEN_BURNS] = enabled }
    }

    /**
     * 슬라이드쇼 무작위 순서. 기본은 꺼짐 — 앨범을 여는 사람은 대개 찍은
     * 순서대로 흘러가길 기대하고, 셔플은 "또 같은 순서"가 지겨워졌을 때
     * 켜는 것이라 기본값으로 강요할 성질이 아니다.
     */
    val slideshowShuffle: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_SLIDESHOW_SHUFFLE] ?: false
    }

    suspend fun setSlideshowShuffle(enabled: Boolean) {
        store.edit { it[KEY_SLIDESHOW_SHUFFLE] = enabled }
    }

    /**
     * 최근 검색어. 최신순으로 최대 [MAX_RECENT_SEARCHES] 개까지.
     *
     * DataStore 는 순서 있는 리스트 타입이 없어 구분자로 이어붙인 문자열 하나로
     * 저장한다 — 검색어에 이 구분자가 들어올 일은 없다(U+E000 은 사용자 입력
     * 영역이라 키보드로 칠 수 없다).
     */
    val recentSearches: Flow<List<String>> = store.data.map { prefs ->
        prefs[KEY_RECENT_SEARCHES]?.split(DELIMITER)?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun addRecentSearch(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        store.edit { prefs ->
            val current = prefs[KEY_RECENT_SEARCHES]?.split(DELIMITER)?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(MAX_RECENT_SEARCHES)
            prefs[KEY_RECENT_SEARCHES] = updated.joinToString(DELIMITER)
        }
    }

    suspend fun clearRecentSearches() {
        store.edit { it.remove(KEY_RECENT_SEARCHES) }
    }

    private companion object {
        val KEY_START_TAB = stringPreferencesKey("start_tab")
        val KEY_GRID_DENSITY = stringPreferencesKey("grid_density")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_VIDEO_PLAYBACK_MODE = stringPreferencesKey("video_playback_mode")
        val KEY_SLIDESHOW_INTERVAL = stringPreferencesKey("slideshow_interval")
        val KEY_SLIDESHOW_KEN_BURNS = booleanPreferencesKey("slideshow_ken_burns")
        val KEY_SLIDESHOW_SHUFFLE = booleanPreferencesKey("slideshow_shuffle")
        val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val KEY_ALBUM_SORT = stringPreferencesKey("album_sort")
        val KEY_ALBUM_FAVORITES_FIRST = booleanPreferencesKey("album_favorites_first")
        /** U+E000 (Private Use Area) — 키보드로 칠 수 없어 검색어와 절대 충돌하지 않는다. */
        const val DELIMITER = ""
        const val MAX_RECENT_SEARCHES = 10
    }
}
