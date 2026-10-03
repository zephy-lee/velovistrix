package com.regnius.photoprism.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.regnius.photoprism.core.data.AlbumRepository
import com.regnius.photoprism.core.model.Album
import com.regnius.photoprism.core.model.AlbumSort
import com.regnius.photoprism.core.model.sortedForDisplay
import com.regnius.photoprism.core.settings.AppSettingsRepository
import com.regnius.photoprism.core.ui.ThumbnailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data class Error(val message: String) : AlbumsUiState
    data class Loaded(
        val all: List<Album>,
        val sort: AlbumSort,
        val favoritesFirst: Boolean,
        /** Phase 2.10 당겨서 새로고침 중인가 — 목록은 그대로 보여준 채 인디케이터만 띄운다. */
        val isRefreshing: Boolean = false,
    ) : AlbumsUiState {
        val visible: List<Album> get() = all.sortedForDisplay(sort, favoritesFirst)
    }
}

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: AlbumRepository,
    private val settings: AppSettingsRepository,
    val thumbnails: ThumbnailLoader,
) : ViewModel() {

    private val _state = MutableStateFlow<AlbumsUiState>(AlbumsUiState.Loading)
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()

    init { load() }

    /**
     * §17.33 — **캐시부터 그리고 뒤에서 갱신한다.**
     *
     * 예전에는 곧장 [AlbumsUiState.Loading] 으로 비우고 서버 응답을 기다렸다.
     * 앨범 목록은 Room 에 이미 있는데도(§2.9) 앱을 켤 때마다 왕복이 끝나야
     * 화면이 나왔다 — 서버가 멀거나 느리면 그 시간이 통째로 빈 화면이다.
     * 시작 탭이 앨범이라(§5.1) 이게 곧 앱의 첫인상이 된다.
     *
     * 캐시가 있으면 그것으로 즉시 그리고 새로고침 표시만 띄운다. 캐시가
     * 없을 때만(첫 실행·로그아웃 직후) 예전처럼 로딩 화면을 보여준다.
     */
    private fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // §17.45 — 정렬 설정은 **저장된 값**에서 읽는다. 화면이 이미 떠
            // 있으면(재시도·당겨서 새로고침) 그 화면의 값이 최신이므로 그걸
            // 쓰고, 처음 들어온 것이면 기기에 저장해 둔 값을 쓴다.
            //
            // `first()` 로 한 번만 읽는 것이 중요하다. `stateIn` 으로 받으면
            // DataStore 를 읽기 전에 기본값이 먼저 나오는데, 그걸 진짜 값으로
            // 믿고 화면을 세우면 저장한 설정이 영영 안 먹는다 — §17.43 에서
            // 시작 화면이 그래서 안 바뀌었다.
            val (sort, favoritesFirst) = _state.value.sortPrefs()
                ?: (settings.albumSort.first() to settings.albumFavoritesFirst.first())

            val cached = repository.cachedAlbums()
            _state.value = if (cached.isEmpty()) {
                AlbumsUiState.Loading
            } else {
                AlbumsUiState.Loaded(cached, sort, favoritesFirst, isRefreshing = true)
            }

            repository.albums(forceRefresh).fold(
                onSuccess = { albums -> _state.value = AlbumsUiState.Loaded(albums, sort, favoritesFirst) },
                onFailure = { e ->
                    // 캐시를 이미 보여주고 있으면 에러 화면으로 밀어내지 않는다 —
                    // 그 목록은 여전히 유효하다.
                    _state.value = (_state.value as? AlbumsUiState.Loaded)?.copy(isRefreshing = false)
                        ?: AlbumsUiState.Error(e.message ?: e.javaClass.simpleName)
                },
            )
        }
    }

    fun retry() = load(forceRefresh = true)

    /**
     * 당겨서 새로고침(Phase 2.10). [retry] 와 달리 화면을 [AlbumsUiState.Loading]
     * 으로 되돌리지 않는다 — 이미 보이는 목록을 그대로 둔 채 인디케이터만
     * 띄우고, 새 목록이 오면 그때 교체한다. `retry` 는 에러 화면에서 처음부터
     * 다시 시도하는 용도라 화면을 비워도 되지만, 당겨서 새로고침은 이미 잘
     * 보이는 목록을 스피너로 잠깐 지워버리면 오히려 더 나쁜 경험이 된다.
     */
    fun refresh() {
        val current = _state.value as? AlbumsUiState.Loaded ?: return load(forceRefresh = true)
        _state.value = current.copy(isRefreshing = true)
        viewModelScope.launch {
            repository.albums(forceRefresh = true).fold(
                onSuccess = { albums ->
                    _state.value = current.copy(all = albums, isRefreshing = false)
                },
                onFailure = {
                    // 새로고침 실패는 조용히 무시한다 — 이미 보여주고 있던 목록이
                    // 여전히 유효하므로, 에러 화면으로 밀어낼 이유가 없다.
                    _state.value = current.copy(isRefreshing = false)
                },
            )
        }
    }

    /**
     * 정렬 기준을 바꾸는 건 **네트워크 요청이 아니다.** 이미 받아둔 목록을
     * 메모리에서 다시 정렬할 뿐이라, 사용자가 정렬을 눌렀다 바꿨다 해도
     * 서버를 다시 두드리지 않는다.
     */
    fun setSort(sort: AlbumSort) {
        _state.update { (it as? AlbumsUiState.Loaded)?.copy(sort = sort) ?: it }
        viewModelScope.launch { settings.setAlbumSort(sort) }
    }

    fun setFavoritesFirst(value: Boolean) {
        _state.update { (it as? AlbumsUiState.Loaded)?.copy(favoritesFirst = value) ?: it }
        viewModelScope.launch { settings.setAlbumFavoritesFirst(value) }
    }

    /** 화면이 아직 안 떴으면 null — 그때는 저장된 값을 읽어 온다. */
    private fun AlbumsUiState.sortPrefs(): Pair<AlbumSort, Boolean>? =
        (this as? AlbumsUiState.Loaded)?.let { it.sort to it.favoritesFirst }
}
