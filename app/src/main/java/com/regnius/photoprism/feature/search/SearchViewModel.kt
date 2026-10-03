package com.regnius.photoprism.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.regnius.photoprism.core.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 검색 상태(열림 여부 · 검색어 · 필터)와 최근 검색어. §17.47
 *
 * 원래는 최근 검색어만 들고 있었고 나머지는 [MainShell] 의 지역 상태였다 —
 * "화면을 벗어나면 사라져도 되는 값" 이라고 봤는데, **그게 틀렸다.**
 *
 * 검색 결과에서 사진을 열면 뷰어는 별도 라우트라 `MainRoute` 의 컴포지션이
 * 떠난다. `rememberSaveable` 인 검색어는 살아 돌아오지만 그냥 `remember` 이던
 * 필터는 **초기화됐다.** 텍스트 없이 필터만으로 검색했다면 돌아왔을 때
 * `query.isBlank() && !filters.isActive` 가 참이 되어 결과 대신 "최근 검색어"
 * 목록이 뜬다 — 사용자가 본 "검색 결과가 없어짐" 이 이것이다.
 *
 * 필터를 `rememberSaveable` 로 못 바꾼 이유는 [SearchFilters] 가
 * `java.time.LocalDate` 를 들고 있어 기본 Bundle Saver 가 저장을 못 하기
 * 때문이다. 그래서 셋을 한꺼번에 ViewModel 로 올렸다 — 이 ViewModel 은
 * `MainRoute` 의 `NavBackStackEntry` 에 묶이므로 **뷰어를 다녀와도, 화면을
 * 돌려도** 그대로 살아 있다. 세 값이 한 군데 모이니 따로 노는 일도 없다.
 *
 * 프로세스가 죽으면 사라진다. 그건 그대로 둔다 — 되살리려면 [SearchFilters]
 * 를 직렬화해 `SavedStateHandle` 에 넣어야 하는데, 검색은 이어서 하려고
 * 앱을 다시 여는 종류의 상태가 아니다.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
) : ViewModel() {

    private val _active = MutableStateFlow(false)

    /** 검색 오버레이가 앱바를 차지하고 있는지. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    val recentSearches: StateFlow<List<String>> = settings.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun open() {
        _active.value = true
    }

    /**
     * 검색을 **끝낸다.** 검색어와 필터를 같이 비우는 것이 핵심이다 — 닫았다
     * 다시 열었을 때 지난 검색이 남아 있으면 새 검색을 하러 온 사용자가
     * 먼저 지워야 한다.
     *
     * 뷰어에 다녀오는 것은 닫는 것이 아니다. 그때는 이 함수가 안 불린다.
     */
    fun close() {
        _active.value = false
        _query.value = ""
        _filters.value = SearchFilters()
    }

    fun setQuery(text: String) {
        _query.value = text
    }

    fun setFilters(filters: SearchFilters) {
        _filters.value = filters
    }

    /** 검색을 실제로 실행했을 때만 기록한다 — 입력 중인 글자마다 남기면 목록이 무의미해진다. */
    fun commitSearch(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { settings.addRecentSearch(text) }
    }

    fun clearRecent() {
        viewModelScope.launch { settings.clearRecentSearches() }
    }
}
