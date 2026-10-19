package com.regnius.photoprism.feature.catalog

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.regnius.photoprism.core.data.FeedKey
import com.regnius.photoprism.feature.photos.PhotoTabsScreen

/**
 * 검색 쿼리 하나의 사진 — 이미지 / 동영상 탭 (Phase 3.5~3.6).
 *
 * 라벨이나 사람을 탭하면 열리는 화면이다. 앨범 상세와 **같은 화면**을 쓴다
 * ([PhotoTabsScreen]) — 처음에는 탭 없이 그리드 하나만 뒀는데, 앨범에만 탭이
 * 있으면 같은 그리드인데 화면마다 되는 게 다르다는 인상을 준다는 지적을
 * 받았다(사용자 보고, 2026-10-10).
 *
 * 전체 장수는 넘기지 않는다(0). PhotoPrism 검색 API 는 전체 개수를 주지 않고,
 * 라벨·인물 목록이 주는 `PhotoCount` 는 **이미지와 동영상을 합친 수**라
 * 두 탭 어느 쪽의 기준도 아니다 — 탭에 보이는 "불러온 개수" 가 더 정확하다.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PhotoQueryScreen(
    title: String,
    query: String,
    onBack: () -> Unit,
    onPhotoClick: (FeedKey, Int) -> Unit,
    onStartSlideshow: (FeedKey, startIndex: Int) -> Unit = { _, _ -> },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    PhotoTabsScreen(
        title = title,
        totalCount = 0,
        imagesKey = remember(query) { FeedKey.queryImages(query) },
        videosKey = remember(query) { FeedKey.queryVideos(query) },
        onBack = onBack,
        onPhotoClick = onPhotoClick,
        onStartSlideshow = onStartSlideshow,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}
