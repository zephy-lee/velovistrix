package com.regnius.photoprism.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.regnius.photoprism.core.data.CatalogRepository
import com.regnius.photoprism.core.model.CatalogItem
import com.regnius.photoprism.core.model.CatalogKind
import com.regnius.photoprism.core.ui.ThumbnailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Error(val message: String) : CatalogUiState
    data class Loaded(
        val items: List<CatalogItem>,
        val isRefreshing: Boolean = false,
    ) : CatalogUiState
}

/**
 * 라벨 또는 인물 목록 (Phase 3.5~3.6).
 *
 * [AlbumsViewModel][com.regnius.photoprism.feature.albums.AlbumsViewModel] 과
 * 같은 "캐시부터 그리고 뒤에서 갱신" 순서를 따른다(§17.33) — 캐시가 있으면
 * 그걸 먼저 그리고 새로고침 표시만 띄우고, 없을 때만 로딩 화면을 보여준다.
 *
 * 정렬은 없다. 서버가 이름순으로 주고, 그게 이 목록에서 사람들이 기대하는
 * 유일한 순서다 — 라벨을 "장수 많은 순" 으로 보고 싶다는 요구가 생기면
 * 그때 [com.regnius.photoprism.core.model.AlbumSort] 처럼 붙이면 된다.
 */
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    val thumbnails: ThumbnailLoader,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * 라벨인가 사람인가 — 라우트 인자에서 읽는다
     * ([com.regnius.photoprism.navigation.CatalogRoute.kind]).
     *
     * 앨범 쪽([com.regnius.photoprism.feature.albums.AlbumsViewModel.kind])과
     * 같은 방식이다. 라우트 인자는 프로퍼티 이름 그대로 [SavedStateHandle] 에
     * 들어오므로, 화면이 ViewModel 에 아무것도 넘겨주지 않아도 된다.
     */
    val kind: CatalogKind = CatalogKind.from(savedStateHandle.get<String>("kind"))

    private val _state = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    init { load() }

    private fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val cached = repository.cachedItems(kind)
            _state.value = if (cached.isEmpty()) {
                CatalogUiState.Loading
            } else {
                CatalogUiState.Loaded(cached, isRefreshing = true)
            }

            repository.items(kind, forceRefresh).fold(
                onSuccess = { items -> _state.value = CatalogUiState.Loaded(items) },
                onFailure = { e ->
                    // 캐시를 이미 보여주고 있으면 에러 화면으로 밀어내지 않는다.
                    _state.value = (_state.value as? CatalogUiState.Loaded)?.copy(isRefreshing = false)
                        ?: CatalogUiState.Error(e.message ?: e.javaClass.simpleName)
                },
            )
        }
    }

    fun retry() = load(forceRefresh = true)

    fun refresh() {
        val current = _state.value as? CatalogUiState.Loaded ?: return load(forceRefresh = true)
        _state.value = current.copy(isRefreshing = true)
        viewModelScope.launch {
            repository.items(kind, forceRefresh = true).fold(
                onSuccess = { items -> _state.value = CatalogUiState.Loaded(items) },
                // 새로고침 실패는 조용히 무시한다 — 보여주던 목록이 여전히 유효하다.
                onFailure = { _state.value = current.copy(isRefreshing = false) },
            )
        }
    }
}
