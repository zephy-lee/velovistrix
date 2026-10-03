package com.regnius.photoprism.feature.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.regnius.photoprism.R
import com.regnius.photoprism.core.data.FeedKey
import com.regnius.photoprism.core.model.MediaFile
import com.regnius.photoprism.core.model.MediaType
import com.regnius.photoprism.core.model.Photo
import com.regnius.photoprism.core.model.ThumbSize
import com.regnius.photoprism.core.ui.getAt
import com.regnius.photoprism.core.ui.peekAt
import com.regnius.photoprism.core.ui.photoSharedKey
import com.regnius.photoprism.feature.photos.PhotoFeedViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** 이 거리 이상 아래로 끌면 닫는다. */
private const val DISMISS_THRESHOLD_PX = 320f

/**
 * "다음 동영상" 을 찾을 때 훑어볼 최대 항목 수(§17.26). 사진이 이만큼 연달아
 * 나오면 다음 동영상은 없는 것으로 보고 버튼을 비활성으로 둔다 — 만 장짜리
 * 목록에서 동영상 한 편을 찾겠다고 끝까지 훑는 비용이 더 아깝다.
 */
private const val VIDEO_SCAN_LIMIT = 100

/**
 * [from] 에서 앞(혹은 뒤)으로 훑어 가장 가까운 동영상 페이지를 찾는다.
 * 없으면 null — 그러면 플레이어의 그 방향 버튼이 비활성으로 남는다.
 *
 * 아직 안 불러온 자리(placeholder, §17.18)를 만나면 거기서 멈춘다. 건너뛰고
 * 더 가면 그 사이에 있을지 모르는 동영상을 지나쳐 **순서를 어기는** 이동이
 * 되기 때문이다.
 */
private fun LazyPagingItems<Photo>.nearestVideoPage(from: Int, forward: Boolean): Int? {
    val step = if (forward) 1 else -1
    var index = from + step
    var scanned = 0
    while (index in 0 until itemCount && scanned < VIDEO_SCAN_LIMIT) {
        val photo = peekAt(index) ?: return null
        if (photo.type == MediaType.VIDEO) return index
        index += step
        scanned++
    }
    return null
}

/**
 * 전체화면 뷰어 (Phase 1.9~1.13).
 *
 * 목록을 인자로 받지 않고 [FeedKey] 만 받아 **그리드와 동일한 페이징 스트림**을
 * 다시 얻는다. PhotoFeedController 가 앱 스코프에 캐시해두었기 때문에 네트워크
 * 요청 없이 즉시 렌더링된다 (§12.5 목표: 100ms).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ViewerScreen(
    feedKey: FeedKey,
    startIndex: Int,
    onClose: () -> Boolean,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: PhotoFeedViewModel = hiltViewModel(),
) {
    val flow = remember(feedKey) { viewModel.feed(feedKey) }
    val photos = flow.collectAsLazyPagingItems()
    val density by viewModel.gridDensity.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { photos.itemCount },
    )


    // Paging 아이템이 0개에서 N개로 도착했을 때, PagerState 가 초기 0에 
    // 고정되어 있다면 startIndex 로 즉시 이동시킨다 (§5.2). 
    // scrollToPage(animate=false) 는 사용자 눈에 띄지 않게 한 프레임 안에 일어난다.
    LaunchedEffect(photos.itemCount) {
        if (photos.itemCount > startIndex && pagerState.currentPage != startIndex) {
            pagerState.scrollToPage(startIndex)
        }
    }

    var chromeVisible by remember { mutableStateOf(true) }
    var infoVisible by remember { mutableStateOf(false) }
    var dismissOffset by remember { mutableFloatStateOf(0f) }
    var isZoomed by remember { mutableStateOf(false) }

    // 뷰어를 닫는 경로가 세 가지다: 시스템 뒤로가기/제스처, 상단바 뒤로가기
    // 아이콘, 아래로 스와이프해서 닫기. 이 셋의 체감 속도가 달랐다(§17.3) —
    // 스와이프는 손을 뗄 때 이미 dismissOffset 이 커져 있어 배경이 거의
    // 투명해진 상태였지만, 나머지 둘은 dismissOffset 이 전혀 안 바뀐 채로
    // NavHost 의 popExitTransition 페이드 하나에만 의존해 배경이 온전히
    // 보이다가 서서히 없어졌다. `closing` 을 세 경로 모두에서 공통으로
    // true 로 세팅해 배경·상단바를 dismissOffset 과 무관하게 즉시 걷어내고,
    // NavHost 전환은 그 뒤에 남은 사진의 Shared Element 애니메이션만 위해
    // 짧게 남겨둔다.
    var closing by remember { mutableStateOf(false) }

    // §17.31 — **한 번만** 닫는다. onClose() 는 popBackStack() 이라
    // 두 번 불리면 뷰어를 닫은 뒤 그 아래 그리드까지 닫아버린다. 닫는 경로가
    // 셋(뒤로가기·상단바·아래로 스와이프)이라 빠른 조작에서 겹치기 쉽다.
    //
    // §17.32 — **실제로 팝됐을 때만** `closing` 으로 넘어간다. 전환 중이면
    // 팝이 거부되는데, 그때도 `closing` 을 세워 버리면 화면이 닫히지도
    // 살아있지도 않은 상태로 굳는다 — 상단바는 사라지고, 끌어내린 위치에
    // 이미지가 멈추고, 뒤로가기는 이 함수의 첫 줄에서 먹혀 영영 못 나간다.
    fun close() {
        if (closing) return
        if (onClose()) {
            closing = true
        } else {
            // 못 닫았으면 끌어내린 만큼을 되돌려 원래 화면으로 복구한다.
            dismissOffset = 0f
        }
    }

    // 닫는 중에도 **켜 둔다.** 끄면 퇴장 애니메이션이 도는 동안 들어온
    // 뒤로가기가 뒤 화면(그리드)의 핸들러로 새어나가 거기서 또 처리된다.
    // 켜 두고 close() 가 두 번째를 무시하게 하면, 그 입력은 여기서 조용히
    // 소비되고 끝난다.
    BackHandler(onBack = ::close)

    // 확대 중에는 페이지 넘김을 막는다. 안 않으면 패닝하다가 사진이 넘어간다.
    val pagerEnabled = !isZoomed

    // 배경을 끌어내린 만큼 투명하게 만든다.
    val dismissFraction = min(abs(dismissOffset) / DISMISS_THRESHOLD_PX, 1f)
    val backgroundAlpha = if (closing) 0f else 1f - dismissFraction * 0.85f

    // 범위 확인이 필요하다 — §17.30. 목록이 새 세대로 넘어가는 순간
    // itemCount 가 잠깐 0 이 되는데 pagerState.currentPage 는 그대로라,
    // 그냥 peek 하면 그 프레임에서 앱이 죽는다.
    val current = photos.peekAt(pagerState.currentPage)

    // §17.26 — 동영상 컨트롤의 이전/다음 버튼이 갈 곳. 목록에서 현재 페이지
    // 기준으로 가장 가까운 **동영상** 페이지를 찾는다(사진은 건너뛴다 —
    // 이 버튼은 동영상 플레이어 안에 있으니 "다음 동영상"이 자연스럽다).
    // derivedStateOf 라 페이지가 바뀌거나 목록이 더 로드될 때만 다시 계산된다.
    val scope = rememberCoroutineScope()
    val nextVideoPage by remember(photos) {
        derivedStateOf { photos.nearestVideoPage(pagerState.currentPage, forward = true) }
    }
    val previousVideoPage by remember(photos) {
        derivedStateOf { photos.nearestVideoPage(pagerState.currentPage, forward = false) }
    }

    // §17.45 — **지금 보는 사진**을 그리드에 알린다.
    //
    // 그리드는 §17.34 이후 `sharedBounds` 를 누른 한 칸에만 건다. 그런데 뷰어에서
    // 좌우로 넘기면 돌아갈 칸이 누른 칸이 아니게 되고, 그 칸에는 모디파이어가
    // 없으니 닫을 때 전환이 통째로 빠진다 — 들어갈 때는 커지는데 나올 때는
    // 툭 나타났다. 페이지가 바뀔 때마다 주인공을 옮겨 두면, 어느 사진에서
    // 나가든 그 칸으로 되돌아간다. **거는 칸은 여전히 하나**라 §17.34 의
    // 성능 수정은 그대로다.
    LaunchedEffect(pagerState, photos) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            photos.peekAt(page)?.uid?.let(viewModel::showSharedPhoto)
        }
    }

    // 다음/이전 사진을 미리 받아둔다. 없으면 스와이프할 때마다 회색 화면을 본다.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            listOf(page - 1, page + 1)
                .filter { it in 0 until photos.itemCount }
                .mapNotNull { photos.peek(it)?.hash }
                .let { viewModel.thumbnails.prefetch(it, ThumbSize.VIEWER_FULL) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = backgroundAlpha)),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = pagerEnabled,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dismissOffset
                    val s = 1f - dismissFraction * 0.15f
                    scaleX = s
                    scaleY = s
                },
        ) { page ->
            val photo = photos.getAt(page)
            if (photo == null) {
                // §17.31 — placeholder(§17.18) 를 켜 둬서, 아직 안 실린
                // 자리는 null 로 온다. 예전엔 여기서 그냥 빠져나가 **아무것도
                // 안 그렸고**, 그러면 뷰어 배경(라이트 테마에서는 흰색)만 남아
                // "흰 화면" 으로 보였다. 오프라인이면 채워질 일이 없어 그
                // 상태가 그대로 굳는다.
                UnloadedPage(
                    error = photos.loadState.refresh as? LoadState.Error
                        ?: photos.loadState.append as? LoadState.Error,
                    onRetry = photos::retry,
                )
                return@HorizontalPager
            }

            // ── 이 Box 가 Pager 에게 보이는 "페이지" 다 ─────────────────────
            //
            // `Modifier.sharedElement` 는 그걸 붙인 노드의 **측정 크기 자체**를
            // 전환 중간값으로 바꾼다 (그냥 그려지는 위치만 바뀌는 게 아니다).
            // 예전에는 sharedElement 를 Pager 페이지의 최상위 자식(지금의 이
            // Box 자리)에 직접 붙였다. 그러면 뒤로가기로 현재 페이지가 그리드
            // 크기로 줄어드는 동안, **그 줄어드는 크기가 Pager 의 페이지 배치
            // 계산(index × pageWidth)에 그대로 들어가** 좌우 페이지 위치가
            // 안쪽으로 끌려왔다 — 좌측 페이지는 좌측 가장자리 기준으로, 우측
            // 페이지는 우측 가장자리 기준으로 줄어드는 것처럼 보인 이유다.
            //
            // 이 Box 를 한 겹 끼워 Pager 에는 **항상 고정된 fillMaxSize** 만
            // 보고하게 하고, sharedElement 는 그 안쪽 자식(ZoomableImage)에만
            // 붙인다. 안쪽 노드의 애니메이션 크기는 이 Box 의 크기에 영향을
            // 주지 않으므로 Pager 의 배치 계산과 완전히 분리된다.
            Box(Modifier.fillMaxSize()) {

                // AsyncImage 에 넘길 요청을 페이지별로 안정된 키로 기억해 둔다.
                // 뷰어에서는 썸네일(그리드)에서 원본으로 바뀔 때 깜빡임을 없애기 위해 
                // crossfade 를 끈다 (§4.4).
                val request = remember(photo.hash, density) {
                    viewModel.thumbnails.request(
                        fileHash = photo.hash,
                        size = ThumbSize.VIEWER_FULL,
                        crossfade = false,
                    )
                }

                // §17.31 — 목록을 훑을 때 **디스크에** 받아 둔 그리드 썸네일.
                // 원본 요청이 실패해도(오프라인) 이게 밑에 깔려 있어 흰 화면이
                // 되지 않는다.
                val cachedRequest = remember(photo.hash, density) {
                    viewModel.thumbnails.request(
                        fileHash = photo.hash,
                        size = density.thumbSize,
                        crossfade = false,
                    )
                }

                // ── remember 는 반드시 조건 없이 호출한다 ──────────────────────
                val pageZoomState = remember { ZoomState() }

                // 현재 페이지의 줌 상태를 부모(Pager)에게 보고한다. 페이지마다
                // pageZoomState 가 따로 있어서, "지금 이 페이지가 currentPage 이고
                // 동시에 확대돼 있는가" 를 매번 다시 계산해 올려야 한다 — 페이지가
                // 바뀌는 순간에도 (current, zoomed) 조합을 이 페이지 입장에서 다시
                // 평가하므로, 스와이프로 떠나는 페이지는 자동으로 false 를 보고하고
                // (curr 가 이제 false 이므로) 새로 current 가 된 페이지가 자기 줌
                // 상태를 보고한다 — 순서와 무관하게 최종적으로는 항상 실제
                // current 페이지의 줌 상태로 수렴한다.
                LaunchedEffect(pageZoomState) {
                    snapshotFlow { (page == pagerState.currentPage) to pageZoomState.isZoomed }
                        .collect { (current, zoomed) -> isZoomed = current && zoomed }
                }

                val isCurrentPage = page == pagerState.currentPage
                val isInitialPage = page == startIndex

                // ── Shared Element 애니메이션 조건 (Phase 1.13 수정) ─────────────
                //
                // 그냥 `isCurrentPage` 만 쓰면, 스와이프 도중 페이지가 바뀌는 순간
                // 다음 페이지에 sharedElement 가 붙으면서 **그리드 위치에서부터
                // 날아오는(slide up)** 의도치 않은 애니메이션이 발생한다.
                //
                // 1. 첫 진입 시 (page == startIndex) 에는 날아와야 한다.
                // 2. 스와이프 중에는 애니메이션이 없어야 한다 (단순 이동).
                // 3. 닫을 때 (closing == true) 에는 현재 페이지가 그리드로 돌아가야 한다.
                val shouldAnimate = isCurrentPage && (isInitialPage || closing)

                val sharedContentState = sharedTransitionScope?.let { scope ->
                    with(scope) { rememberSharedContentState(key = photoSharedKey(photo.uid)) }
                }
                val imageModifier = if (
                    sharedTransitionScope != null &&
                    sharedContentState != null &&
                    animatedVisibilityScope != null &&
                    shouldAnimate
                ) {
                    with(sharedTransitionScope) {
                        // sharedElement 가 아니라 sharedBounds 를 쓴다. 뷰어는
                        // ContentScale.Fit(전체 사진), 그리드는 Crop(정사각형
                        // 셀에 꽉 채움)으로 서로 다른 화면비 처리를 쓰는데,
                        // sharedElement 는 전환 중 한쪽의 렌더링을 그대로
                        // 가져다 애니메이션되는 bounds에 늘려 그린다 — 뒤로가기
                        // 순간 그리드 쪽(Crop) 렌더링이 아직 화면 전체 크기인
                        // bounds에 덮여, 사진이 화면 전체에 꽉 차게(Crop처럼)
                        // 뿌려진 것처럼 보이는 첫 프레임 왜곡의 원인이었다.
                        // (스와이프 닫기는 dismissOffset 의 자체 축소
                        // graphicsLayer가 먼저 적용돼 있어 이 왜곡이 가려졌던
                        // 것으로 보인다.) sharedBounds 는 양쪽이 각자의
                        // contentScale로 독립적으로 렌더링하고 서로 크로스페이드
                        // 하므로 이 문제가 없다.
                        Modifier.sharedBounds(
                            sharedContentState,
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> tween(220) },
                        )
                    }
                } else Modifier

                // §17.8 — 페이징 목록 응답의 merged=true 가 페이지 경계에서
                // 파일을 하나 놓치는 경우가 실측으로 확인됐다: 사진 Type은
                // "video"인데 Files 배열엔 정지 이미지(사이드카) 하나만 오고
                // 진짜 비디오 파일이 빠져 있었다. 이 상태로 (Photo.videoFile
                // 의) 폴백을 그대로 재생 시도하면, 정지 이미지의 해시로
                // /videos/.../avc 를 요청하게 되어 서버가 이미지를 돌려주고
                // ExoPlayer가 PARSING_CONTAINER_MALFORMED 로 거부한다 —
                // "재생 시도 자체를 안 함"(구버전 폴백 부재) 대신 "엉뚱한
                // 파일로 재생 시도"(신버전 폴백)로 증상만 바뀐 것이었다.
                //
                // 페이징 응답에 진짜 Video:true 파일이 없을 때만, 페이지
                // 경계 문제가 없는 단건 상세 조회(GET /photos/{uid}, 이미
                // PhotoInfoSheet 가 같은 이유로 쓰고 있다)로 다시 받아
                // 진짜 파일을 찾는다.
                val hasGenuineVideoFile = remember(photo) { photo.files.any { it.isVideo } }
                var refetchedVideoFile by remember(photo.uid) { mutableStateOf<MediaFile?>(null) }
                LaunchedEffect(photo.uid, hasGenuineVideoFile) {
                    if (photo.type == MediaType.VIDEO && !hasGenuineVideoFile) {
                        refetchedVideoFile = viewModel.getPhotoDetails(photo.uid)?.videoFile
                    }
                }
                val videoFile = remember(photo, hasGenuineVideoFile, refetchedVideoFile) {
                    if (hasGenuineVideoFile) photo.videoFile else refetchedVideoFile
                }
                val videoHash = videoFile?.hash?.takeIf { it.isNotBlank() }
                val videoUrl = remember(videoHash) {
                    videoHash?.let { viewModel.thumbnails.videoUrl(it) }
                }

                // 모든 미디어 타입(사진, 동영상, 라이브 포토)에 줌 기능을 동일하게 적용함 (§UX 수정).
                // 이전에는 동영상 플레이어 컨트롤과의 터치 충돌 때문에 동영상은 줌을 제외했으나,
                // ZoomableImage 로 감싸고 내부에서 터치를 처리하도록 통합함.
                ZoomableImage(
                    photoKey = photo.uid,
                    zoomState = pageZoomState,
                    onDismissDrag = { dismissOffset = it },
                    onDismissRelease = { total ->
                        if (total > DISMISS_THRESHOLD_PX) close() else dismissOffset = 0f
                    },
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    onSwipeUpInfo = { infoVisible = true },
                    modifier = imageModifier,
                ) { transformModifier ->
                    if (photo.type == MediaType.VIDEO && videoUrl != null) {
                        // 비디오는 재생 컨트롤 위치를 고정하기 위해 전체에 transformModifier를 걸지 않고
                        // 비디오 화면(Surface)만 내부에서 줌을 처리함 (§UX 수정).
                        Box(Modifier.fillMaxSize()) {
                            ViewerImage(
                                full = request,
                                cached = cachedRequest,
                                contentDescription = photo.title,
                                modifier = transformModifier.fillMaxSize(),
                            )
                            VideoPlayer(
                                uri = videoUrl,
                                isActive = isCurrentPage,
                                videoFile = videoFile,
                                zoomState = pageZoomState,
                                // 현재 페이지인지로 가르지 않는다 — 옆 페이지의
                                // (안 보이는) 플레이어까지 같이 켜두는 게 낭비처럼
                                // 보이지만, 그렇게 해야 "현재 페이지가 되는 순간"에
                                // 버튼 활성 여부가 바뀌지 않는다. 바뀌면 그 순간
                                // PlayerView 가 플레이어를 다시 붙이면서(§17.26 래퍼
                                // 재생성) 영상 표면이 잠깐 떨어졌다 붙어 깜빡인다.
                                onSkipToNext = nextVideoPage?.let { target ->
                                    { scope.launch { pagerState.animateScrollToPage(target) } }
                                },
                                onSkipToPrevious = previousVideoPage?.let { target ->
                                    { scope.launch { pagerState.animateScrollToPage(target) } }
                                },
                                // §17.48 — 상단 바와 재생 컨트롤은 **한 상태**다.
                                // 동영상 위의 탭은 PlayerView 가 가져가므로 우리가
                                // 먼저 받을 수 없다. 대신 컨트롤이 사라졌다는 신호를
                                // 받아 상단 바를 같이 내린다. 되돌려 주는 것은 지금
                                // 보고 있는 페이지만 — 옆 페이지의 플레이어도 살아
                                // 있어서(위 주석), 그쪽 신호까지 받으면 보지도 않는
                                // 동영상이 상단 바를 움직인다.
                                controlsVisible = chromeVisible,
                                onControlsVisibilityChange = { visible ->
                                    if (isCurrentPage) chromeVisible = visible
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else if (photo.type == MediaType.LIVE && videoUrl != null) {
                        // 라이브 포토도 비디오와 동일하게 처리.
                        Box(Modifier.fillMaxSize()) {
                            ViewerImage(
                                full = request,
                                cached = cachedRequest,
                                contentDescription = photo.title,
                                modifier = transformModifier.fillMaxSize(),
                            )
                            if (isCurrentPage) {
                                LivePhotoPlayer(
                                    uri = videoUrl,
                                    isActive = true,
                                    zoomState = pageZoomState,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    } else {
                        ViewerImage(
                            full = request,
                            cached = cachedRequest,
                            contentDescription = photo.title,
                            modifier = transformModifier.fillMaxSize(),
                        )
                    }
                }
            } // Box(Modifier.fillMaxSize()) — 페이지 고정 크기 끝
        }

        AnimatedVisibility(
            visible = chromeVisible && dismissOffset == 0f && !closing,
            enter = fadeIn(),
            // 닫히는 중에는 이 AnimatedVisibility 자체의 기본 300ms exit 페이드가
            // 아니라 즉시(snap) 사라져야 한다 — 안 그러면 backgroundAlpha 는
            // 이미 0인데 상단바만 따로 300ms 페이드되는 새로운 이음매가 생긴다.
            exit = if (closing) fadeOut(snap()) else fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            // fillMaxWidth 여야 한다 — fillMaxSize 로 두면 이 Box 가 화면 전체를
            // 차지해서 "상단 바" 가 화면 한가운데에 그려지고, 스크림도 사진
            // 전체를 덮어 어둡게 만든다.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                Color.Transparent
                            ),
                        )
                    )
                    .statusBarsPadding()
                    // 가로 모드에서 시스템 내비게이션 바가 좌/우 측면에 붙는
                    // 기기에서, 우측 끝(정보) 버튼이 그 막대에 가려 눌리지
                    // 않던 문제 — 좌우 인셋만 반영한다(세로 모드의 하단 바는
                    // 이 상단 바와 안 겹치므로 bottom은 제외).
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 4.dp)
                    .height(56.dp),
            ) {
                IconButton(onClick = ::close, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    current?.takenAtLocal?.take(10).orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
                IconButton(
                    onClick = { infoVisible = true },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(Icons.Filled.Info, stringResource(R.string.viewer_info), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }

    if (infoVisible && current != null) {
        PhotoInfoSheet(photo = current, onDismiss = { infoVisible = false })
    }
}

/**
 * 아직 못 불러온 페이지 자리.
 *
 * 진행 표시만 두면 오프라인에서 영원히 도는 것처럼 보이므로, 실패했을 때는
 * 왜 비었는지 말해 주고 다시 시도할 길을 준다.
 */
@Composable
private fun UnloadedPage(error: LoadState.Error?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (error == null) {
            CircularProgressIndicator()
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    stringResource(R.string.viewer_photo_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.viewer_photo_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
            }
        }
    }
}

/**
 * 뷰어의 사진 한 장 — **두 겹으로 그린다.**
 *
 * 아래에 그리드에서 쓰던 작은 썸네일, 위에 원본 크기. 작은 쪽은 목록을 훑을 때
 * 이미 디스크 캐시에 들어와 있어 오프라인에서도 즉시 뜬다. 위쪽 요청이
 * 실패하면 (거칠지만) 사진이 남는다.
 *
 * 예전에는 `placeholderMemoryCacheKey` 하나로 이걸 대신했는데, 그건 이름 그대로
 * **메모리 캐시만** 본다. 앱을 새로 띄운 오프라인 상태에서는 메모리가 비어 있어
 * 아무 소용이 없었고, `AsyncImage` 에는 실패 표시가 없어서 **아무것도 안 그린
 * 채** 뷰어 배경(라이트 테마에서 흰색)만 남았다 — 이게 "오프라인에서 목록은
 * 나오는데 상세는 흰 화면" 의 정체다(§17.31).
 */
@Composable
private fun ViewerImage(
    full: ImageRequest?,
    cached: ImageRequest?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val cachedPainter = rememberAsyncImagePainter(model = cached)
    val cachedState by cachedPainter.state.collectAsState()
    val fullPainter = rememberAsyncImagePainter(model = full)
    val fullState by fullPainter.state.collectAsState()
    val fullLoaded = fullState is AsyncImagePainter.State.Success

    // §17.39 — 밑그림은 **원본이 늦을 때만** 보여준다.
    //
    // 원본이 캐시에 있으면 수십 ms 면 뜨는데, 그 사이에도 밑그림을 깔았다가
    // 걷어내니 필요 없을 때까지 한 번 깜빡였다. 그런데 밑그림은 정사각형으로
    // 잘린 그림이라(아래 참고) 세로로 긴 사진에서는 **흐린 같은 사진이 아니라
    // 다른 사진**처럼 보인다. 그래서 이 깜빡임이 유독 눈에 띄었다.
    var underlayDue by remember(full) { mutableStateOf(false) }
    LaunchedEffect(full) {
        delay(UNDERLAY_DELAY_MS)
        underlayDue = true
    }

    Box(modifier) {
        // §17.35 — 밑그림은 **원본이 뜨기 전까지만** 보여준다.
        //
        // 그리드 썸네일은 `tile_*`, 즉 **정사각형으로 잘린** 그림이다. 원본은
        // 원래 화면비 그대로다. 둘 다 Fit 으로 깔아 두면 세로로 긴 사진에서
        // 정사각형이 뒤로 삐져나와 사진이 두 겹으로 보인다. 밑그림은 빈 화면을
        // 막으려고 넣은 것이니(§17.31), 원본이 오면 할 일이 끝난 것이다.
        //
        // **알파로 숨긴다 — 컴포지션에서 빼지 않는다.** Coil 의 페인터는 처음
        // 그려질 때 크기가 정해져야 요청이 시작된다. 안 그리면 요청 자체가
        // 시작되지 않아서, 정작 밑그림이 필요해진 순간에 그때부터 받게 되고
        // (오프라인에서 늦어진다) 아래의 "사진이 아예 없다" 안내도 영영
        // 뜨지 않는다.
        Image(
            painter = cachedPainter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alpha = if (!fullLoaded && underlayDue) 1f else 0f,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = fullPainter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        // 작은 것마저 없으면 이 사진은 기기에 아예 없다 — 그때만 말해 준다.
        if (cachedState is AsyncImagePainter.State.Error && !fullLoaded) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            ) {
                Text(
                    stringResource(R.string.viewer_photo_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.viewer_photo_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


/** 밑그림을 깔기 전에 원본을 기다려 주는 시간. 캐시에 있으면 이 안에 뜬다. */
private const val UNDERLAY_DELAY_MS = 150L
