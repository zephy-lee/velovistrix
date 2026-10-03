package com.regnius.photoprism.feature.search

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.regnius.photoprism.R
import com.regnius.photoprism.feature.photos.PhotoFeedScreen

/**
 * TopAppBar 의 title 슬롯을 통째로 대체하는 검색창 (Phase 2.1).
 *
 * 별도 화면으로 만들지 않고 [MainShell] 의 앱바 안에 인라인으로 두는 이유는,
 * 탭을 오가며 검색을 열고 닫을 때 화면 전환 애니메이션이 끼어들 이유가
 * 없어서다 — 검색은 지금 보던 탭 위에 얹히는 오버레이에 가깝다.
 */
@Composable
fun SearchTopBarContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    activeFilterCount: Int = 0,
    onOpenFilters: () -> Unit = {},
    /**
     * 열리자마자 키보드를 올릴지. §17.47
     *
     * 검색을 새로 열 때는 당연히 올려야 하지만, **뷰어에서 돌아온 경우엔
     * 올리면 안 된다** — 검색 상태가 살아 돌아오면서 이 앱바도 다시 구성되는데,
     * 무조건 포커스를 잡으면 보러 온 결과 위로 키보드가 덮인다.
     */
    autoFocus: Boolean = true,
) {
    // 끝에 여백을 조금 둔다 — 필터 버튼의 뱃지가 아이콘 우측 상단으로
    // 살짝 겹쳐 나오는데, Row 가 화면 끝까지 꽉 차 있으면 그 겹친 부분이
    // 앱바 가장자리에 잘려 보인다.
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.search_close))
        }
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = LocalContentColor.current),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(LocalContentColor.current),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.common_clear))
            }
        }
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 24.dp),
                    role = Role.Button,
                    onClick = onOpenFilters
                )
                .padding(12.dp)
        ) {
            BadgedBox(
                badge = {
                    if (activeFilterCount > 0) {
                        Badge { Text("$activeFilterCount") }
                    }
                }
            ) {
                Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.search_filters))
            }
        }
    }

    LaunchedEffect(Unit) { if (autoFocus) focusRequester.requestFocus() }
}

/**
 * 검색어가 비어 있으면 최근 검색어, 있으면 결과 그리드.
 *
 * PhotoPrism 의 `q=` 는 자유 텍스트와 필터 문법을 함께 받으므로, 사용자가
 * `type:video` 처럼 직접 입력해도 그대로 전달된다 (§4.3). [filters] 는
 * 필터 시트(Phase 2.2, 기간/타입/카메라/위치 유무)에서 고른 값을 이 자유
 * 텍스트와 합쳐 [FeedKey] 를 만든다 — [SearchFilters.toFeedKey] 참고.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SearchBody(
    query: String,
    filters: SearchFilters = SearchFilters(),
    onSubmitQuery: (String) -> Unit,
    onPhotoClick: (index: Int) -> Unit,
    contentPadding: PaddingValues,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    if (query.isBlank() && !filters.isActive) {
        RecentSearches(
            viewModel = viewModel,
            onTermClick = onSubmitQuery,
            contentPadding = contentPadding,
        )
        return
    }

    // 검색은 매 키 입력마다 서버를 두드리지 않는다 — onSubmitQuery 를 통해
    // "제출된" 검색어에 대해서만 FeedKey 가 바뀌고 결과를 조회한다.
    // 그리드 자체는 타임라인·즐겨찾기와 같은 PhotoFeedScreen 을 그대로 쓴다 —
    // 밀도 설정·로딩 상태·프리페치를 다시 구현할 이유가 없다.
    PhotoFeedScreen(
        key = filters.toFeedKey(query),
        onPhotoClick = onPhotoClick,
        contentPadding = contentPadding,
        emptyMessage = if (query.isBlank()) stringResource(R.string.search_no_filter_results) else stringResource(R.string.search_no_query_results, query),
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

@Composable
private fun RecentSearches(
    viewModel: SearchViewModel,
    onTermClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val recent by viewModel.recentSearches.collectAsStateWithLifecycle()

    if (recent.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding), Alignment.Center) {
            Text(
                stringResource(R.string.search_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.search_recent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = viewModel::clearRecent) { Text(stringResource(R.string.search_clear_all)) }
            }
        }
        items(count = recent.size, key = { index -> recent[index] }) { index ->
            val term = recent[index]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onTermClick(term) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp),
                )
                Text(term, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
