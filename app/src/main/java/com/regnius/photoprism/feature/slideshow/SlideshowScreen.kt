package com.regnius.photoprism.feature.slideshow

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.regnius.photoprism.R
import com.regnius.photoprism.core.data.FeedKey
import com.regnius.photoprism.core.model.Photo
import com.regnius.photoprism.core.model.ThumbSize
import com.regnius.photoprism.core.ui.getAt
import com.regnius.photoprism.core.ui.peekAt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** 컨트롤을 띄워 둔 채 이만큼 아무 조작이 없으면 다시 감춘다. */
private const val CONTROLS_HIDE_DELAY_MS = 3_500L

/**
 * 다음 장이 아직 안 불러와졌을 때 최대 이만큼 기다린다.
 *
 * 무한정 기다리면 오프라인처럼 그 자리가 영영 안 채워지는 상황에서 슬라이드쇼가
 * 조용히 멈춘 것처럼 보인다 — 그럴 바엔 한 장 건너뛰고 계속 도는 편이 낫다.
 */
private const val NEXT_SLIDE_WAIT_MS = 10_000L

/** 한 장 ↔ 다음 장 크로스페이드 길이. */
private const val CROSSFADE_MS = 700

/** Ken Burns 로 확대되는 최대 배율. */
private const val KEN_BURNS_SCALE = 1.10f

/** Ken Burns 로 밀리는 최대 거리(화면 짧은 변 대비 비율). */
private const val KEN_BURNS_PAN = 0.03f

/**
 * 슬라이드쇼 (Phase 3.1 / 3.2).
 *
 * 목록을 인자로 받지 않고 [FeedKey] 만 받는 것은 뷰어와 같은 이유다 — 그리드가
 * 이미 채워 둔 페이징 스트림을 그대로 물려받아, 시작하는 순간 네트워크를 다시
 * 치지 않는다.
 *
 * **동영상은 정지 프레임으로 보여준다.** 슬라이드쇼는 "정해진 간격"이 전제인데
 * 동영상을 실제로 재생하면 그 한 장만 길이가 제각각이 되고, 재생이 끝날 때까지
 * 기다릴지 자르고 넘어갈지에 정답이 없다. 목록에 동영상이 섞여 있어도 흐름이
 * 끊기지 않는 쪽을 골랐다.
 */
@UnstableApi
@Composable
fun SlideshowScreen(
    feedKey: FeedKey,
    startIndex: Int,
    onClose: () -> Boolean,
    viewModel: SlideshowViewModel = hiltViewModel(),
) {
    val photos = remember(feedKey) { viewModel.feed(feedKey) }.collectAsLazyPagingItems()
    val interval by viewModel.interval.collectAsStateWithLifecycle()
    val kenBurnsEnabled by viewModel.kenBurns.collectAsStateWithLifecycle()
    val shuffle by viewModel.shuffle.collectAsStateWithLifecycle()

    var index by rememberSaveable { mutableIntStateOf(startIndex) }

    // 슬라이드 순번 — 한 장 넘어갈 때마다 1씩 는다. 사진 인덱스와 따로 두는
    // 이유는 셔플 때문이다: 순번은 늘 1씩 늘어서 크로스페이드/Ken Burns 방향/
    // 동영상 플레이어 슬롯을 나눌 안정된 기준이 되지만, 사진 인덱스는 셔플에서
    // 아무 값이나 나오고 목록이 한 장뿐이면 아예 안 바뀐다.
    var slideSeq by rememberSaveable { mutableIntStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(true) }
    // 시작할 때 한 번 보여준다 — 화면이 검게 덮이고 아무 버튼도 없으면 나가는
    // 방법을 모른다. 아래 이펙트가 몇 초 뒤 알아서 감춘다.
    var controlsVisible by remember { mutableStateOf(true) }
    var advanceTick by remember { mutableIntStateOf(0) }

    // 순서(순차/셔플)와 앞뒤 이동은 전부 여기서 결정한다. 설정에서 셔플을
    // 켜고 끄면 지금 보고 있는 자리에서 새로 시작한다.
    val order = remember(shuffle) { SlideOrder(shuffle).also { it.start(index) } }

    fun goTo(target: Int) {
        index = target
        slideSeq++
    }

    val context = LocalContext.current
    val videoSource = rememberSlideshowVideoSource(viewModel.thumbnails)
    val players = remember(videoSource) { SlideshowVideoPlayers(context, videoSource.dataSourceFactory) }
    DisposableEffect(players) { onDispose { players.release() } }

    KeepAwakeAndImmersive()
    // 뷰어와 같은 이유로 한 번만 닫는다(§17.31) — 끝내기 버튼과 뒤로가기가
    // 겹쳐 들어오면 popBackStack() 이 두 번 돌아 그리드까지 닫힌다.
    var closing by remember { mutableStateOf(false) }
    fun close() {
        if (closing) return
        // §17.32 — 팝이 거부되면 `closing` 으로 넘어가지 않는다. 넘어가면
        // 뒤로가기가 이 함수 첫 줄에서 먹혀 슬라이드쇼에 갇힌다.
        if (onClose()) closing = true
    }
    BackHandler(onBack = ::close)

    // 새로고침으로 목록이 짧아지면 현재 자리가 목록 밖으로 나갈 수 있다.
    LaunchedEffect(photos.itemCount) {
        if (photos.itemCount in 1..index) {
            order.start(0)
            goTo(0)
        }
    }

    // 현재 자리와 그 다음 자리를 Paging 에 알리고(=로드 요청), 다음 장을 미리
    // 준비한다 — 사진은 원본 이미지를 프리페치하고, 동영상은 플레이어를 미리
    // 열어 시킹까지 끝내 둔다.
    //
    // 동영상 준비는 **이 슬라이드가 보이는 동안** 끝나야 한다. 서버가 실시간
    // 변환해 주는 스트림에 시킹까지 걸면 준비에 몇 초가 걸릴 수 있는데, 그걸
    // 다음 슬라이드가 화면에 올라온 뒤에 시작하면 3초짜리 슬라이드가 통째로
    // 버퍼링만 하다 끝난다.
    LaunchedEffect(slideSeq, index, photos.itemCount, players, interval) {
        photos.getAt(index)
        val next = order.peekNext(photos.itemCount)
        photos.getAt(next)

        photos.peekAt(index)?.let { photo ->
            videoSource.uriFor(photo)?.let { uri ->
                players.prepare(slideSeq, uri, photo.slideshowVideoDurationMs(), interval.millis)
            }
        }
        photos.peekAt(next)?.let { photo ->
            val uri = videoSource.uriFor(photo)
            if (uri == null) {
                viewModel.thumbnails.prefetch(listOf(photo.hash), ThumbSize.VIEWER_FULL)
                return@let
            }
            // 크로스페이드가 끝날 때까지 기다렸다 준비한다.
            //
            // 플레이어 슬롯은 순번의 홀짝으로 두 개를 번갈아 쓰므로, 지금
            // 준비하려는 N+1 번은 **한 장 전(N-1)이 쓰던 슬롯**이다. 그
            // 슬라이드는 아직 화면에서 사라지는 중이라, 여기서 바로 준비하면
            // 페이드아웃되는 영상이 그 자리에서 끊긴다.
            delay(CROSSFADE_MS.toLong())
            players.prepare(slideSeq + 1, uri, photo.slideshowVideoDurationMs(), interval.millis)
        }
    }

    // 자동 넘김.
    //
    // 타이머를 [index] 가 아니라 별도의 tick 으로 다시 건다. 인덱스로 걸면
    // 넘어간 자리가 마침 같을 때(사진이 한 장뿐이라 0 → 0 으로 도는 경우)
    // 상태가 안 바뀌어 이 이펙트가 재시작되지 않고 슬라이드쇼가 그대로 멈춘다.
    LaunchedEffect(advanceTick, playing, interval, photos) {
        if (!playing) return@LaunchedEffect
        delay(interval.millis)
        // 목록이 아직 안 왔으면(첫 페이지 도착 전) 도착할 때까지 기다린다.
        // 여기서 그냥 빠져나가면 나중에 사진이 도착해도 타이머를 다시 걸
        // 계기가 없어 영영 멈춘 채로 남는다.
        snapshotFlow { photos.itemCount }.first { it > 0 }
        val next = order.peekNext(photos.itemCount)
        // 아직 안 불러온 자리(placeholder)면 도착할 때까지 기다린다. 그냥
        // 넘어가면 빈 화면이 한 장 지나간다 — 다만 오프라인처럼 영영 안
        // 채워질 수도 있어 무한정 기다리지는 않는다.
        withTimeoutOrNull(NEXT_SLIDE_WAIT_MS) {
            snapshotFlow { photos.peekAt(next) != null }.first { it }
        }
        goTo(order.next(photos.itemCount))
        advanceTick++
    }

    // 컨트롤은 잠깐 보였다 사라진다 — 슬라이드쇼는 화면을 비워 두는 게 목적이라
    // 계속 띄워 두면 목적 자체가 없어진다.
    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    fun step(forward: Boolean) {
        val count = photos.itemCount
        if (count == 0) return
        // 셔플에서 stringResource(R.string.slideshow_previous)은 인덱스-1 이 아니라 **방금 전에 봤던 사진**이어야
        // 한다 — [SlideOrder] 가 지나온 순서를 기억하고 있다.
        goTo(if (forward) order.next(count) else order.previous(count))
        // 직접 넘겼으면 남은 시간이 아니라 처음부터 다시 센다 — 손으로 넘긴
        // 직후에 자동으로 또 넘어가면 두 장이 스쳐 지나간 것처럼 보인다.
        advanceTick++
    }

    Box(
        Modifier
            .fillMaxSize()
            // 뷰어(ViewerScreen)와 같은 색을 쓴다. 예전에는 여기만 `Color.Black`
            // 이라 라이트 테마에서 슬라이드쇼로 들어가는 순간 화면이 까맣게
            // 뒤집혔다 — 같은 사진을 보는 두 화면인데 배경이 달랐다.
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            },
    ) {
        val refreshing = photos.loadState.refresh is LoadState.Loading

        if (photos.itemCount == 0) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (refreshing) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        stringResource(R.string.photos_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Crossfade(
                targetState = slideSeq to index,
                animationSpec = tween(CROSSFADE_MS),
                label = "slide",
            ) { (seq, slideIndex) ->
                val photo = photos.peekAt(slideIndex)
                val videoUri = photo?.let { videoSource.uriFor(it) }
                val request = remember(photo?.hash) {
                    photo?.hash?.let {
                        viewModel.thumbnails.request(
                            fileHash = it,
                            size = ThumbSize.VIEWER_FULL,
                            crossfade = false,
                        )
                    }
                }

                // 원본이 도착하기 전의 검은 화면을 없애는 밑그림.
                //
                // 예전에는 `placeholderMemoryCacheKey` 로 그리드 썸네일을 깔았는데,
                // 그건 **메모리 캐시에 있을 때만** 동작한다 — 앱을 새로 띄웠거나
                // 그 사진을 그리드에서 본 적이 없으면 아무 소용이 없었다. 뷰어와
                // 같은 방식으로(§17.31) 실제 요청을 한 겹 밑에 깐다: 디스크에
                // 있으면 즉시 뜨고, 없으면 네트워크로 받되 원본보다 훨씬 빠르다.
                val underlayRequest = remember(photo?.hash) {
                    photo?.hash?.let {
                        viewModel.thumbnails.request(
                            fileHash = it,
                            size = ThumbSize.GRID_DENSE,
                            crossfade = false,
                        )
                    }
                }
                Box(Modifier.fillMaxSize().clipToBounds(), Alignment.Center) {
                    if (request == null) {
                        CircularProgressIndicator()
                    } else {
                        // §17.35 — 원본이 오기 전까지만. 밑그림은 정사각형으로
                        // 잘린 썸네일이라, 그대로 두면 세로 사진에서 뒤로
                        // 삐져나와 두 겹으로 보인다.
                        val fullPainter = rememberAsyncImagePainter(model = request)
                        val fullState by fullPainter.state.collectAsState()
                        if (fullState !is AsyncImagePainter.State.Success) {
                            AsyncImage(
                                model = underlayRequest,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        AsyncImage(
                            model = request,
                            contentDescription = photo?.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                // 동영상 슬라이드에는 걸지 않는다 — 영상은 이미
                                // 움직이고 있고, 그 위에 확대까지 겹치면 어지럽다.
                                .kenBurns(
                                    enabled = kenBurnsEnabled && videoUri == null,
                                    running = playing,
                                    slideSeq = seq,
                                    durationMillis = interval.millis,
                                ),
                        )
                    }
                    // 재생 준비가 끝나기 전에는 아무것도 안 그린다 — 그동안은
                    // 위의 정지 프레임이 그대로 보이고, 슬라이드 시간 안에
                    // 준비가 안 되면 그 장은 그냥 사진으로 지나간다.
                    if (videoUri != null) {
                        SlideshowVideo(
                            player = players.playerFor(seq),
                            uri = videoUri,
                            playing = playing,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        SlideshowControls(
            visible = controlsVisible,
            playing = playing,
            position = index,
            total = photos.itemCount,
            caption = photos.peekAt(index)?.takenAtLocal?.take(10).orEmpty(),
            onClose = ::close,
            onTogglePlay = { playing = !playing },
            onPrevious = { step(forward = false) },
            onNext = { step(forward = true) },
        )
    }
}

/**
 * Ken Burns — 한 장이 머무는 동안 아주 느리게 확대·이동한다 (Phase 3.1).
 *
 * 방향을 장마다 번갈아 뒤집는다: 짝수 번째는 원본 크기에서 확대되고, 홀수
 * 번째는 확대된 상태에서 원본 크기로 돌아온다. 늘 같은 방향이면 몇 장 지나지
 * 않아 패턴이 눈에 띄고, 이렇게 두면 매 홀수 장의 끝에서 사진 전체가 한 번씩
 * 온전히 보인다 — 효과를 켜 두어도 잘려서 못 보고 지나가는 사진이 없다.
 *
 * [running] 이 false 가 되면(일시정지) 애니메이션도 그 자리에서 멈추고, 다시
 * 재생하면 남은 시간만큼만 이어서 움직인다.
 */
@Composable
private fun Modifier.kenBurns(
    enabled: Boolean,
    running: Boolean,
    slideSeq: Int,
    durationMillis: Long,
): Modifier {
    if (!enabled) return this

    val progress = remember(slideSeq) { Animatable(0f) }
    LaunchedEffect(slideSeq, running, durationMillis) {
        if (!running) return@LaunchedEffect
        val remaining = ((1f - progress.value) * durationMillis).toInt()
        if (remaining <= 0) return@LaunchedEffect
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
    }

    val zoomIn = slideSeq % 2 == 0
    // 수평/수직 중 한 방향으로만 민다 — 두 축을 같이 쓰면 사선으로 흘러
    // 화면이 기울어진 것처럼 보인다.
    val horizontal = (slideSeq / 2) % 2 == 0
    val sign = if ((slideSeq / 4) % 2 == 0) 1f else -1f

    // progress 는 반드시 이 람다 **안에서** 읽는다. 밖에서 읽으면 애니메이션
    // 프레임마다 이 컴포저블이 재구성되지만, 안에서 읽으면 그리기 단계만 다시
    // 돈다 — 초당 60번 재구성되는 것과 아닌 것의 차이다.
    return this.graphicsLayer {
        val t = if (zoomIn) progress.value else 1f - progress.value
        val scale = 1f + (KEN_BURNS_SCALE - 1f) * t
        scaleX = scale
        scaleY = scale
        val shortestEdge = minOf(size.width, size.height)
        val pan = shortestEdge * KEN_BURNS_PAN * t * sign
        translationX = if (horizontal) pan else 0f
        translationY = if (horizontal) 0f else pan
    }
}

/**
 * 슬라이드쇼 중에는 화면이 꺼지지 않고, 상태바·내비게이션 바를 숨긴다 (Phase 3.2).
 *
 * 둘 다 화면을 떠날 때 반드시 되돌려야 한다 — 특히 `keepScreenOn` 을 켠 채
 * 남기면 앱을 쓰는 내내 화면이 안 꺼져 배터리를 조용히 태운다.
 */
@Composable
private fun KeepAwakeAndImmersive() {
    val view = LocalView.current
    val window = LocalActivity.current?.window

    DisposableEffect(view, window) {
        view.keepScreenOn = true
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.apply {
            // 숨긴 바를 스와이프로 잠깐 꺼내볼 수 있게 둔다. 완전히 못 꺼내게
            // 막으면 시계·배터리를 확인할 방법이 없어진다.
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SlideshowControls(
    visible: Boolean,
    playing: Boolean,
    position: Int,
    total: Int,
    caption: String,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    // 상단 — 닫기 + 위치 표시.
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
                .height(88.dp)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Filled.Close, stringResource(R.string.slideshow_exit), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                buildString {
                    if (total > 0) append("${position + 1} / $total")
                    if (caption.isNotEmpty()) append("  ·  $caption")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    // 하단 — 이전 / 재생·일시정지 / 다음.
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    )
                )
                .padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.SkipPrevious, stringResource(R.string.slideshow_previous), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (playing) stringResource(R.string.slideshow_pause) else stringResource(R.string.slideshow_play),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, stringResource(R.string.slideshow_next), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
            }
        }
    }
}
