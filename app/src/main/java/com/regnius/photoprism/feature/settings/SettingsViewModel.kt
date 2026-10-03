package com.regnius.photoprism.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.regnius.photoprism.core.auth.AuthRepository
import com.regnius.photoprism.core.data.AlbumRepository
import com.regnius.photoprism.core.data.PhotoFeedController
import com.regnius.photoprism.core.data.local.AppDatabase
import com.regnius.photoprism.core.settings.AppSettingsRepository
import com.regnius.photoprism.core.settings.SlideshowInterval
import com.regnius.photoprism.core.settings.StartTab
import com.regnius.photoprism.core.settings.ThemeMode
import com.regnius.photoprism.core.settings.VideoPlaybackMode
import com.regnius.photoprism.core.ui.GridDensity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
    private val authRepository: AuthRepository,
    private val imageLoader: ImageLoader,
    private val photoFeedController: PhotoFeedController,
    private val albumRepository: AlbumRepository,
    private val database: AppDatabase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val startTab: StateFlow<StartTab> = settings.startTab
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StartTab.ALBUMS)

    /**
     * 시작 탭을 **처음 한 번 적용할 때만** 쓰는 값. 실제 값이 오기 전에는 null.
     *
     * §17.43 — [startTab] 은 `stateIn` 의 초기값으로 `ALBUMS` 를 먼저 내보낸다.
     * 그건 설정값이 아니라 **아직 안 읽었다는 뜻의 자리표시자**인데, 시작 탭을
     * 적용하는 쪽이 그걸 진짜 값으로 받아 첫 프레임에 써 버리고 "적용 완료" 로
     * 잠갔다. 그래서 무엇으로 바꿔 두든 늘 앨범으로 열렸다.
     *
     * null 로 시작해 **"아직 모른다"** 를 타입으로 표현하면 그 혼동이 구조적으로
     * 불가능해진다. `Eagerly` 인 것은 화면이 구독하기 전에 미리 읽어 두어
     * 첫 프레임에서 탭이 한 번 튀는 것을 줄이기 위해서다.
     */
    val initialStartTab: StateFlow<StartTab?> = settings.startTab
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val gridDensity: StateFlow<GridDensity> = settings.gridDensity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridDensity.DEFAULT)

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val videoPlaybackMode: StateFlow<VideoPlaybackMode> = settings.videoPlaybackMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoPlaybackMode.AUTO)

    val slideshowInterval: StateFlow<SlideshowInterval> = settings.slideshowInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SlideshowInterval.S5)

    val slideshowKenBurns: StateFlow<Boolean> = settings.slideshowKenBurns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val slideshowShuffle: StateFlow<Boolean> = settings.slideshowShuffle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 이 앱이 기기에 쌓아 둔 **전부**의 용량. null = 아직 계산 전.
     *
     * §17.40 — 예전에는 Room 파일만 셌다. 그런데 Room 은 7MB 남짓이고 썸네일
     * 캐시는 상한이 512MB 다. 설정 화면이 **앱이 실제로 쓰는 공간을 한 번도
     * 안 알려주고 있었다** — "이 앱 왜 이렇게 커?" 하고 들어온 사람이 두
     * 자릿수 틀린 숫자를 보고 나갔다.
     */
    private val _cacheSizeBytes = MutableStateFlow<Long?>(null)
    val cacheSizeBytes: StateFlow<Long?> = _cacheSizeBytes.asStateFlow()

    init {
        viewModelScope.launch { refreshCacheSize() }
    }

    fun setStartTab(tab: StartTab) = viewModelScope.launch { settings.setStartTab(tab) }
    fun setGridDensity(density: GridDensity) = viewModelScope.launch { settings.setGridDensity(density) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setVideoPlaybackMode(mode: VideoPlaybackMode) = viewModelScope.launch { settings.setVideoPlaybackMode(mode) }
    fun setSlideshowInterval(interval: SlideshowInterval) =
        viewModelScope.launch { settings.setSlideshowInterval(interval) }
    fun setSlideshowKenBurns(enabled: Boolean) =
        viewModelScope.launch { settings.setSlideshowKenBurns(enabled) }
    fun setSlideshowShuffle(enabled: Boolean) =
        viewModelScope.launch { settings.setSlideshowShuffle(enabled) }

    /**
     * 기기에 쌓인 캐시를 **한꺼번에** 지운다. §17.40
     *
     * 예전에는 버튼이 둘이었다 — 썸네일(Coil 디스크·메모리)과 오프라인 목록
     * (Room). 서로를 안 건드려서 한쪽만 지우면 **아무도 원하지 않는 반쪽
     * 상태**가 남았다. 썸네일만 지우면 목록은 있는데 그림이 없고(오프라인에서
     * 빈 격자), 목록만 지우면 그림은 있는데 목록이 없다(오프라인이면 복구도
     * 안 되고 이미지는 죽은 짐이 된다). 둘은 **같이 있어야만 쓸모가 있다.**
     *
     * 서버 데이터는 안 건드린다 — 다시 접속하면 처음부터 다시 채워진다.
     * 다만 지운 직후 오프라인이면 그때까지 보이던 것이 다 사라지므로,
     * 화면에서 확인 대화상자를 거친다.
     */
    fun clearCache() {
        viewModelScope.launch {
            imageLoader.memoryCache?.clear()
            // 디스크 캐시 삭제는 파일 I/O 다 — 메인 스레드에서 하면 안 된다.
            withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
            photoFeedController.clear()
            albumRepository.clear()
            withContext(Dispatchers.IO) { reclaimDatabaseFile() }
            refreshCacheSize()
        }
    }

    /**
     * 지운 만큼을 **파일에서도** 돌려받는다. §17.42
     *
     * SQLite 는 행을 지워도 파일을 안 줄인다 — 페이지를 freelist 에 넣어 두고
     * 재사용한다. 그래서 캐시를 다 비운 직후에도 "1.1 MB" 가 그대로 찍혔고,
     * 삭제 버튼이 안 먹은 것처럼 보였다. 기기 DB 로 재보니 840행짜리 1,096KB
     * 를 전부 지우기만 하면 1,096KB 그대로고, `VACUUM` 까지 하면 **56KB** 다.
     *
     * 표시할 때 "빈 DB 크기" 를 빼는 방법도 있지만 그건 거짓말이다 — 그 공간은
     * 실제로 차지하고 있다. 숫자를 고치는 게 아니라 **공간을 실제로 돌려주는**
     * 쪽이 맞다.
     *
     * `VACUUM` 은 트랜잭션 안에서 못 돈다. WAL 파일은 따로 잘라 줘야 줄어드는데,
     * 다른 읽기가 걸려 있으면 실패할 수 있어 실패해도 그냥 넘어간다(다음
     * 자동 체크포인트가 정리한다).
     */
    private fun reclaimDatabaseFile() {
        val db = database.openHelper.writableDatabase
        db.execSQL("VACUUM")
        runCatching { db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)") }
    }

    private suspend fun refreshCacheSize() {
        _cacheSizeBytes.value = withContext(Dispatchers.IO) {
            val db = DB_FILE_SUFFIXES.sumOf { suffix ->
                context.getDatabasePath(AppDatabase.DB_NAME + suffix).length()
            }
            db + (imageLoader.diskCache?.size ?: 0L)
        }
    }

    fun logout() = viewModelScope.launch { authRepository.logout() }

    private companion object {
        // Room 은 기본적으로 WAL 저널 모드를 쓴다 — 실제 데이터가 -wal 에
        // 아직 체크포인트되지 않고 남아있을 수 있어 메인 파일만 재면 용량이
        // 실제보다 작게 보인다. -journal 은 WAL 이 아닐 때(구버전 기기 등)
        // 를 대비한 방어적 포함이다.
        val DB_FILE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")
    }
}
