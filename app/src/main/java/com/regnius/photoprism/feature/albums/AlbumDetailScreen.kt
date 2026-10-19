package com.regnius.photoprism.feature.albums

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.regnius.photoprism.core.data.FeedKey
import com.regnius.photoprism.feature.photos.PhotoTabsScreen

/**
 * 앨범 상세 — 이미지 / 동영상 탭 분리 (§5.3).
 *
 * 화면 자체는 [PhotoTabsScreen] 이 그린다. 여기서 하는 일은 **이 앨범의 두
 * 피드 키를 만드는 것**뿐이다 — 라벨·인물 결과도 같은 화면을 쓰게 되면서
 * (2026-10-10, 사용자 보고) 공통 부분을 그쪽으로 옮겼다.
 *
 * 전체 장수를 라우트로 받아 넘기는 이유는 [com.regnius.photoprism.navigation.AlbumDetailRoute]
 * 주석에 있다 — 앨범 목록 응답에 이미 들어 있는 값이고, PhotoPrism 검색 API 는
 * 전체 개수를 주지 않으므로 **상세 화면에서 알 수 있는 유일한 총량**이다.
 * 달력·폴더 같은 자동 앨범은 서버가 이 값을 0 으로 주는데, 그때는
 * [PhotoTabsScreen] 이 표시를 생략한다.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumDetailScreen(
    albumUid: String,
    title: String,
    photoCount: Int,
    onBack: () -> Unit,
    onPhotoClick: (feedKey: FeedKey, index: Int) -> Unit,
    onStartSlideshow: (feedKey: FeedKey, startIndex: Int) -> Unit = { _, _ -> },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    PhotoTabsScreen(
        title = title,
        totalCount = photoCount,
        imagesKey = remember(albumUid) { FeedKey.albumImages(albumUid) },
        videosKey = remember(albumUid) { FeedKey.albumVideos(albumUid) },
        onBack = onBack,
        onPhotoClick = onPhotoClick,
        onStartSlideshow = onStartSlideshow,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}
