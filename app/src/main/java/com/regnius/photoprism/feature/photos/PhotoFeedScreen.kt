package com.regnius.photoprism.feature.photos

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.regnius.photoprism.R
import com.regnius.photoprism.core.data.FeedKey
import com.regnius.photoprism.core.model.Photo
import com.regnius.photoprism.core.ui.FeedStateOverlay
import com.regnius.photoprism.core.ui.LoadedCount
import androidx.compose.ui.Alignment
import com.regnius.photoprism.core.ui.PhotoGrid
import com.regnius.photoprism.core.ui.rememberPhotoSelection
import kotlinx.coroutines.launch

/**
 * 사진 그리드 화면. [key] 만 바꿔 타임라인/앨범탭/즐겨찾기로 재사용된다.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoFeedScreen(
    key: FeedKey,
    onPhotoClick: (index: Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    emptyMessage: String = stringResource(R.string.photos_empty),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onLoadedCountChanged: (LoadedCount) -> Unit = {},
    /** 화면 맨 위에 보이는 사진의 인덱스 — 슬라이드쇼 시작 위치용(Phase 3.1). */
    onVisibleStartIndexChange: (Int) -> Unit = {},
    /**
     * 길게 눌러 여러 장을 고를 수 있게 할지 (Phase 4.3).
     *
     * 끌 수 있게 둔 이유는 슬라이드쇼처럼 **고르는 것이 의미 없는 맥락**이
     * 생길 수 있기 때문이다. 지금은 모든 그리드가 켜 둔다.
     */
    selectable: Boolean = true,
    viewModel: PhotoFeedViewModel = hiltViewModel(),
) {
    val flow = remember(key) { viewModel.feed(key) }
    val photos = flow.collectAsLazyPagingItems()
    val density by viewModel.gridDensity.collectAsStateWithLifecycle()
    val sharedPhotoUid by viewModel.sharedPhotoUid.collectAsStateWithLifecycle()

    val appendState = photos.loadState.append
    LaunchedEffect(photos.itemCount, appendState) {
        val complete = appendState is LoadState.NotLoading && appendState.endOfPaginationReached
        onLoadedCountChanged(LoadedCount(photos.itemCount, complete))
    }

    // Phase 2.10 — 당겨서 새로고침. 이미 목록이 있는 상태에서만 인디케이터를
    // 보여준다 — 첫 로딩 스피너([FeedStateOverlay])와 겹치지 않게 하려는
    // 것이다.
    val isRefreshing = photos.loadState.refresh is LoadState.Loading && photos.itemCount > 0
    val scope = rememberCoroutineScope()

    // 선택 모드 (Phase 4.3). 뒤로가기로 빠져나온다 — 선택을 켜 둔 채 뒤로
    // 누르면 화면이 닫히는 게 아니라 선택이 풀리는 것이 안드로이드 관례다.
    val selection = rememberPhotoSelection()
    BackHandler(enabled = selection.active) { selection.clear() }

    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                // `photos.refresh()` 만 부르면 RemoteMediator 는 이게 진짜
                // 새로고침 요청인지, 자기 자신의 APPEND 쓰기로 인한
                // 자기무효화인지 구분할 수 없다(PhotoFeedRemoteMediator
                // 문서 참고) — 먼저 이 피드의 remote key 를 지워 "진짜
                // 새로고침"임을 알린 뒤에 새 세대를 요청한다.
                scope.launch {
                    viewModel.invalidateFeed(key)
                    photos.refresh()
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            PhotoGrid(
                items = photos,
                thumbnails = viewModel.thumbnails,
                onPhotoClick = { index, _ -> onPhotoClick(index) },
                contentPadding = contentPadding,
                density = density,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onVisibleStartIndexChange = onVisibleStartIndexChange,
                sharedPhotoUid = sharedPhotoUid,
                onSharedPhotoChange = viewModel::showSharedPhoto,
                selection = selection.takeIf { selectable },
                modifier = Modifier.fillMaxSize(),
            )
        }
        FeedStateOverlay(
            loadState = photos.loadState,
            itemCount = photos.itemCount,
            emptyMessage = emptyMessage,
            onRetry = photos::retry,
            modifier = Modifier.padding(contentPadding),
        )

        // 선택 바는 화면의 상단 바를 가리고 올라온다 — 다섯 군데 화면이 각자
        // 상단 바를 분기하지 않아도 되는 이유다([SelectionBar] 문서 참고).
        if (selection.active) {
            SelectionBar(
                count = selection.count,
                onClose = selection::clear,
                onSave = {
                    viewModel.saveToGallery(selection.photos)
                    selection.clear()
                },
                onShare = {
                    viewModel.share(selection.photos)
                    selection.clear()
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
