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
import com.regnius.photoprism.core.ui.PhotoGrid
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
    }
}
