package com.regnius.photoprism.feature.viewer

import android.util.Log
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.updatePadding
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.regnius.photoprism.R
import com.regnius.photoprism.core.auth.SessionStore
import com.regnius.photoprism.core.di.RawOkHttpClient
import com.regnius.photoprism.core.model.MediaFile
import com.regnius.photoprism.core.network.hasActiveNetwork
import com.regnius.photoprism.core.network.url.ServerUrl
import com.regnius.photoprism.core.network.url.WebDavUrlFactory
import com.regnius.photoprism.core.settings.AppSettingsRepository
import com.regnius.photoprism.core.settings.VideoPlaybackMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Credentials as OkHttpCredentials

private const val LOG_TAG = "VideoPlayer"

/** 스트리밍이 이 시간 안에 재생 준비가 안 되면 WebDAV 원본 재생으로 넘어간다. §17.1 */
private const val STREAMING_FALLBACK_TIMEOUT_MS = 4_000L

/**
 * `PlayerView(context)` 로 코드에서 바로 만들면 surface_type 이 기본값인
 * SurfaceView 로 잡힌다. SurfaceView 는 일반 뷰처럼 Compose 의 그리기 레이어와
 * 같은 경로로 합성되지 않는 별도 창이라, AndroidView 안에 넣으면 화면이 그냥
 * 새까맣게만 보인다(실제로 뷰어에서 재현되어 확인됨). `video_player_view.xml`
 * 에서 `surface_type="texture_view"` 를 강제해 이 문제를 피한다.
 */
private fun inflatePlayerView(context: android.content.Context): PlayerView =
    LayoutInflater.from(context).inflate(R.layout.video_player_view, null) as PlayerView

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VideoPlayerEntryPoint {
    fun okHttpClient(): OkHttpClient
    @RawOkHttpClient fun rawOkHttpClient(): OkHttpClient
    fun sessionStore(): SessionStore
    fun appSettingsRepository(): AppSettingsRepository
}

private enum class VideoSource { STREAMING, WEBDAV }

/**
 * 동영상 재생 (Phase 2.7).
 *
 * ExoPlayer 표준 컨트롤(재생/일시정지/탐색바)을 그대로 쓴다 — 이 화면은 재생
 * 자체가 목적이 아니라 갤러리 뷰어의 부속 기능이라, 커스텀 컨트롤을 새로
 * 만들 이유가 없다. 컨트롤 배치는 `res/layout/exo_custom_controller.xml` 로
 * 재배치했다 (§17.2 — 기본 배치는 중앙 컨트롤이 사진을 가리고, 진행바/시간이
 * 하단 내비게이션 바와 겹쳤다).
 *
 * @param isActive 이 페이지가 Pager 의 현재 페이지일 때만 true. 옆 페이지로
 *   스와이프해 나가면 재생을 멈춘다 — 안 그러면 화면 밖 동영상이 계속
 *   디코딩되며 배터리/발열을 먹는다. `remember(uri)` 가 우리를 위해 이전
 *   플레이어를 해제해 준다: uri 가 바뀌면 새 [DisposableEffect] 가 걸리기
 *   전에 이전 것의 onDispose 가 먼저 불린다.
 * @param videoFile 스트리밍(트랜스코딩) 재생이 실패했을 때 WebDAV 원본 재생으로
 *   폴백하기 위한 원본 파일 정보(§17.1). null 이면 폴백 없이 스트리밍만 시도한다.
 * @param onSkipToNext 컨트롤의 "다음" 버튼(§17.26). null 이면 버튼이 비활성으로
 *   남는다 — 이 플레이어는 항상 동영상 **한 편**만 들고 있어서(플레이리스트가
 *   없다) ExoPlayer 혼자서는 다음으로 갈 곳을 모른다. 대신 뷰어가 "목록에서
 *   다음 동영상이 몇 번째 페이지인지"를 알고 있으므로, 그 이동을 여기로
 *   넘겨받아 버튼에 연결한다.
 * @param onSkipToPrevious 위와 같되 "이전" 버튼.
 * @param controlsVisible 재생 컨트롤을 지금 보여야 하는지. 뷰어의 "화면 장식"
 *   상태(상단 바)와 **같은 값**이다 — §17.48 참고.
 * @param onControlsVisibilityChange 컨트롤이 실제로 나타나거나 사라졌을 때.
 *   [PlayerView] 가 탭을 직접 먹기 때문에, 뷰어는 이 신호로 상단 바를 맞춘다.
 */
@UnstableApi
@Composable
fun VideoPlayer(
    uri: String,
    isActive: Boolean,
    videoFile: MediaFile? = null,
    zoomState: ZoomState? = null,
    onSkipToNext: (() -> Unit)? = null,
    onSkipToPrevious: (() -> Unit)? = null,
    controlsVisible: Boolean = true,
    onControlsVisibilityChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // §17.22 — 동영상은 오프라인 캐싱 대상이 아니라(§13) 재생은 항상 네트워크가
    // 필요하다. 이 체크 없이는 오프라인에서 스트리밍 시도 → 4초 대기 → WebDAV
    // 폴백 시도 → 알아보기 힘든 에러 코드로 끝나는 과정을 매번 그대로 거쳤다 —
    // 이미지처럼 "네트워크에 연결돼 있지 않다"는 걸 시도 전에 바로 보여준다.
    var hasNetwork by remember(uri) { mutableStateOf(context.hasActiveNetwork()) }

    if (!hasNetwork) {
        NoNetworkOverlay(modifier, onRetry = { hasNetwork = context.hasActiveNetwork() })
        return
    }

    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context, VideoPlayerEntryPoint::class.java)
    }
    val okHttpClient = remember { entryPoint.okHttpClient() }
    val sessionStore = remember { entryPoint.sessionStore() }
    val settingsRepository = remember { entryPoint.appSettingsRepository() }
    val videoPlaybackModeFlow = remember(settingsRepository) { settingsRepository.videoPlaybackMode }
    val videoPlaybackMode by videoPlaybackModeFlow.collectAsState(initial = VideoPlaybackMode.AUTO)

    // §17.1 — 스트리밍(트랜스코딩) 실패 시 WebDAV로 원본을 직접 받아 재생하는
    // 폴백 URL. 세션의 서버 주소 + 저장된 로그인 자격증명(Basic Auth 용)이 둘 다
    // 있어야 만들 수 있다 — public 모드처럼 자격증명이 없으면 폴백 자체가 불가능.
    val webDavUri = remember(uri, videoFile) {
        val session = sessionStore.session.value ?: return@remember null
        if (sessionStore.storedCredentials() == null) return@remember null
        val file = videoFile ?: return@remember null
        val base = ServerUrl.normalize(session.serverUrl) ?: return@remember null
        WebDavUrlFactory(base).original(file)
    }
    val forceWebDav = videoPlaybackMode == VideoPlaybackMode.FORCE_WEBDAV && webDavUri != null

    // ExoPlayer 에 특화된 OkHttpClient 생성.
    // 스트리밍 특성상 전체 시간을 제한하는 callTimeout 은 0(무제한)이어야 한다.
    // 진단을 위해 응답 헤더를 로깅하는 인터셉터를 추가한다.
    val videoOkHttpClient = remember(okHttpClient) {
        okHttpClient.newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (request.url.encodedPath.contains("/videos/")) {
                    Log.d(LOG_TAG, "Video Response: ${response.code} ${response.message} " +
                            "Content-Type: ${response.header("Content-Type")} " +
                            "Content-Length: ${response.header("Content-Length")}")
                }
                response
            })
            .build()
    }

    // WebDAV 는 Bearer 토큰이 아니라 계정 자격증명(Basic Auth)으로 인증한다 — 로그인 때
    // 쓴 것과 같은 사용자명/비밀번호(또는 앱 패스워드)를 그대로 재사용한다.
    //
    // 반드시 rawOkHttpClient(세션 인증이 안 붙은 클라이언트)에서 시작해야 한다.
    // 일반 okHttpClient 를 썼더니, WebDAV 가 401 을 돌려줄 때마다 공용
    // SessionAuthenticator 가 "토큰 만료" 로 오인해 재로그인을 시도하고,
    // Bearer 토큰으로는 WebDAV 가 여전히 401 이라 결국 세션 전체를 지워
    // 로그인 화면으로 튕겨버렸다 — 정상 스트리밍도 응답이 늦어 타임아웃으로
    // WebDAV 폴백을 타면 똑같이 튕겼다.
    val rawOkHttpClient = remember { entryPoint.rawOkHttpClient() }
    val webDavOkHttpClient = remember(rawOkHttpClient) {
        val credentials = sessionStore.storedCredentials()
        rawOkHttpClient.newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .apply {
                if (credentials != null) {
                    addInterceptor(Interceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("Authorization", OkHttpCredentials.basic(credentials.username, credentials.secret))
                                .build()
                        )
                    })
                }
            }
            .build()
    }

    // 가로 모드에서 시스템 내비게이션 바가 화면 아래가 아니라 좌/우 측면에
    // 붙는 기기가 많다. 네이티브 뷰(PlayerView) 쪽에 WindowInsets 리스너를
    // 직접 다는 방식은 Compose 의 AndroidView 안에 중첩된 뷰까지 실제
    // WindowInsets 디스패치가 안정적으로 내려온다는 보장이 없어서, 이미
    // 동작이 확인된 Compose 쪽 WindowInsets.navigationBars 값을 여기서
    // 읽어 AndroidView 의 update 블록에서 그대로 꽂아 넣는다(ViewerScreen.kt
    // 상단바의 정보 버튼도 같은 소스로 고쳐서 확인됨).
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val navBars = WindowInsets.navigationBars
    val navBarLeftPx = navBars.getLeft(density, layoutDirection)
    val navBarRightPx = navBars.getRight(density, layoutDirection)
    val navBarBottomPx = navBars.getBottom(density)

    // §17.1 — 본인 서버에서 일부 동영상만 재생되고 나머지는 그냥 정지 화면처럼
    // 보인다는 보고가 있었다. 실패를 눈에 보이게 만들어야 다음에 원인을 좁힐 수
    // 있어서, 에러를 조용히 삼키는 대신 화면에 보여주고 로그캣에도 남긴다.
    var error by remember(uri) { mutableStateOf<PlaybackException?>(null) }
    var isLoading by remember(uri) { mutableStateOf(true) }
    var source by remember(uri) { mutableStateOf(if (forceWebDav) VideoSource.WEBDAV else VideoSource.STREAMING) }
    // §17.23 — 항상 FIT(레터박스)이다, 잘라내지 않는다. 예전엔 화면(컨테이너)과
    // 영상의 방향이 엇갈릴 때만 FIT 이고 방향이 같으면 ZOOM(꽉 채우기, 남는
    // 부분을 잘라냄)이었는데, 세로 영상(예: 1080x1920)을 세로 화면에서 보면
    // "방향은 같다"고 판단해 ZOOM 을 썼다 — 화면 비율이 영상과 정확히 같지
    // 않은 기기(대부분)에서는 그 미세한 차이를 메우려고 영상 가로 일부가
    // 잘려나가 보였다. 화면을 꽉 채우는 것보다 영상 전체가 잘리지 않고
    // 보이는 게 더 중요하다는 판단으로, 방향 비교 없이 항상 FIT 을 쓴다.
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val exoPlayer = remember(uri, source) {
        val useWebDav = source == VideoSource.WEBDAV
        val effectiveUri = if (useWebDav) webDavUri ?: uri else uri
        val client = if (useWebDav) webDavOkHttpClient else videoOkHttpClient
        val dataSourceFactory = OkHttpDataSource.Factory(client)
            .setUserAgent("VeloVistrixPhotoPrism/1.0")

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(playbackException: PlaybackException) {
                        val sourceLabel = if (useWebDav) "webdav" else "streaming"
                        Log.w(LOG_TAG, "playback failed ($sourceLabel) for $effectiveUri: ${playbackException.errorCodeName}", playbackException)
                        // 스트리밍이 실패했고 WebDAV 폴백이 아직 남아 있으면 조용히
                        // 넘어간다 — 사용자에게는 로딩이 계속되는 것처럼 보인다.
                        if (!useWebDav && webDavUri != null) {
                            isLoading = true
                            source = VideoSource.WEBDAV
                        } else {
                            error = playbackException
                            isLoading = false
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isLoading = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                        if (playbackState == Player.STATE_READY) error = null
                    }
                })
                setMediaItem(MediaItem.fromUri(effectiveUri))
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // §17.26 — 컨트롤의 이전/다음 버튼.
    //
    // 이 플레이어에는 미디어 아이템이 항상 하나뿐이라(플레이리스트가 없다)
    // ExoPlayer 는 "다음이 있다"는 사실 자체를 모르고, `PlayerControlView` 는
    // `player.isCommandAvailable(COMMAND_SEEK_TO_NEXT)` 가 false 라 두 버튼을
    // 회색(비활성)으로 그린다. 하지만 **뷰어는 다음 동영상이 어디 있는지 알고
    // 있다** — 그래서 media3 가 UI 컴포넌트 커스터마이즈용으로 권장하는
    // [ForwardingPlayer] 로 실제 플레이어를 감싸, 두 커맨드를 "있다"고
    // 광고하고 실행은 뷰어가 넘겨준 콜백(페이지 이동)으로 돌린다.
    // PlayerView 에는 이 래퍼를 물려주고, 재생 제어(playWhenReady/prepare 등)는
    // 그대로 진짜 [exoPlayer] 에 직접 건다.
    //
    // 콜백은 [rememberUpdatedState] 로 최신 값만 읽는다 — 페이지를 넘길 때마다
    // 새 람다가 오는데 그때마다 래퍼를 새로 만들면 PlayerView 가 플레이어를
    // 다시 붙이느라 컨트롤이 깜빡인다. 래퍼는 "버튼을 켤지 말지"(hasNext/
    // hasPrevious)가 바뀔 때만 다시 만들어, 그 순간 PlayerView 가 커맨드
    // 목록을 새로 읽어가게 한다.
    val latestSkipToNext by rememberUpdatedState(onSkipToNext)
    val latestSkipToPrevious by rememberUpdatedState(onSkipToPrevious)
    val hasNext = onSkipToNext != null
    val hasPrevious = onSkipToPrevious != null
    val navigablePlayer = remember(exoPlayer, hasNext, hasPrevious) {
        object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .addIf(Player.COMMAND_SEEK_TO_NEXT, hasNext)
                    .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNext)
                    .addIf(Player.COMMAND_SEEK_TO_PREVIOUS, hasPrevious)
                    .addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, hasPrevious)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean =
                availableCommands.contains(command)

            override fun hasNextMediaItem(): Boolean = hasNext || super.hasNextMediaItem()

            override fun hasPreviousMediaItem(): Boolean = hasPrevious || super.hasPreviousMediaItem()

            override fun seekToNext() {
                latestSkipToNext?.invoke() ?: super.seekToNext()
            }

            override fun seekToNextMediaItem() {
                latestSkipToNext?.invoke() ?: super.seekToNextMediaItem()
            }

            override fun seekToPrevious() {
                latestSkipToPrevious?.invoke() ?: super.seekToPrevious()
            }

            override fun seekToPreviousMediaItem() {
                latestSkipToPrevious?.invoke() ?: super.seekToPreviousMediaItem()
            }
        }
    }

    LaunchedEffect(isActive) {
        exoPlayer.playWhenReady = isActive
        if (!isActive) exoPlayer.pause()
    }

    // 정상적인 경우 1초 안에 재생이 시작된다 — 그보다 훨씬 넉넉한 4초를 기다려도
    // 여전히 로딩 중이면(에러조차 안 나고 그냥 멈춰 있는 트랜스코딩 행 상태 포함)
    // WebDAV 원본으로 넘어간다. 스트리밍 쪽에서만 의미가 있다.
    LaunchedEffect(uri, source) {
        if (source == VideoSource.STREAMING && webDavUri != null) {
            delay(STREAMING_FALLBACK_TIMEOUT_MS)
            if (isLoading) {
                Log.w(LOG_TAG, "streaming timed out after ${STREAMING_FALLBACK_TIMEOUT_MS}ms for $uri, falling back to WebDAV")
                source = VideoSource.WEBDAV
            }
        }
    }

    // §17.49 — 하단 바(시간·설정)의 색은 **Compose 테마에서 읽어 코드로 칠한다.**
    // 예전엔 `@color/exo_bottom_bar_text` + `values-night/` 로 뒀는데, 리소스의
    // `-night` 한정자는 `Configuration.uiMode`(= 시스템 테마)를 보지 이 앱의
    // 테마 설정을 모른다. 시스템이 라이트인데 앱만 다크로 해 두면 배경은
    // 다크로 가고 글자는 라이트용 검정 그대로라 안 보였다.
    val bottomBarTint = MaterialTheme.colorScheme.onSurface.toArgb()

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { containerSize = it },
    ) {
        // key(source) — 스트리밍(트랜스코딩) ↔ WebDAV(원본) 전환 시 PlayerView 를
        // 통째로 새로 만든다. 이전엔 exoPlayer 만 새로 만들고 같은 PlayerView(의
        // TextureView)를 계속 재사용했는데, WebDAV 플레이어가 첫 프레임을 그리기
        // 전에 죽어버리면(트랜스코딩이 아직 안 끝난 서버가 준 불안정한 데이터 탓)
        // 화면엔 실패한 스트리밍 시도의 마지막 프레임·화면비가 그대로 얼어붙어
        // 남는다 — WebDAV 는 완전히 다른 소스인데 이전 시도의 상태를 이어받을
        // 이유가 없다.
        key(source) {
            AndroidView(
                factory = { ctx ->
                    inflatePlayerView(ctx).apply {
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setControllerAnimationEnabled(false)
                        // §17.23 — 항상 FIT, 잘라내지 않는다. 클래스 상단 문서 참고.
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        // §17.48 — 탭은 우리가 못 받는다. [PlayerView] 가
                        // `onTouchEvent` 에서 ACTION_DOWN 에 true 를 돌려주며
                        // 가져가 버리기 때문이다. 그래서 뺏으려 들지 않고
                        // **결과를 받아 따라간다** — 컨트롤이 떴다/사라졌다는
                        // 신호를 그대로 위로 올려 상단 바를 맞춘다.
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                onControlsVisibilityChange(visibility == android.view.View.VISIBLE)
                            }
                        )
                    }
                },
                update = { playerView ->
                    // 진짜 exoPlayer 가 아니라 §17.26 의 래퍼를 물린다 — 이전/다음
                    // 버튼을 켜기 위해서다.
                    if (playerView.player != navigablePlayer) {
                        playerView.player = navigablePlayer
                    }

                    // §17.48 — 반대 방향. 사진 페이지에서 장식을 숨긴 채로
                    // 동영상으로 넘어오면 컨트롤도 숨은 채여야 한다. 이미
                    // 맞는 상태면 건드리지 않는다 — 위 리스너와 서로를 부르며
                    // 도는 일이 없도록.
                    if (controlsVisible != playerView.isControllerFullyVisible) {
                        if (controlsVisible) playerView.showController() else playerView.hideController()
                    }

                    // 가로 모드에서 시스템 내비게이션 바가 좌/우 측면에 붙는
                    // 기기에서, 컨트롤 묶음이 그 막대에 가려지거나 반대로
                    // 불필요한 여백이 남지 않도록 실제 인셋을 반영한다.
                    playerView.findViewById<android.view.View>(R.id.exo_controls_root)?.updatePadding(
                        left = navBarLeftPx,
                        right = navBarRightPx,
                        bottom = navBarBottomPx,
                    )

                    // §17.23 — 항상 FIT, 레터박스는 가운데 정렬한다.
                    playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    val contentFrame = playerView.findViewById<androidx.media3.ui.AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
                    contentFrame?.let { frame ->
                        frame.layoutParams = (frame.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                            gravity = android.view.Gravity.CENTER
                        }
                    }

                    // 2. 줌 상태 반영 — videoSurfaceView 가 아니라 exo_content_frame
                    // 에 건다. FIT(레터박스)에선 exo_content_frame 자체가 영상
                    // 비율만큼만 작게 측정되는데, 그 안의 surface 를 아무리
                    // scaleX/Y 로 키워도 부모(exo_content_frame)의 원래(작은)
                    // 영역 밖으로는 그려지지 않아 "확대해도 처음 그 작은 영역
                    // 안에서만" 보이는 문제가 있었다. exo_content_frame 의
                    // 부모는 화면 전체 크기인 PlayerView 라서, frame 자체를
                    // 키우면 그 경계 안에서 자유롭게 확대된다. ZOOM 모드에선
                    // exo_content_frame 이 이미 화면 전체 크기라 동작은 같다.
                    contentFrame?.let { frame ->
                        if (zoomState != null) {
                            frame.scaleX = zoomState.scale
                            frame.scaleY = zoomState.scale
                            frame.translationX = zoomState.offset.x
                            frame.translationY = zoomState.offset.y
                        } else {
                            frame.scaleX = 1f
                            frame.scaleY = 1f
                            frame.translationX = 0f
                            frame.translationY = 0f
                        }
                    }

                    // 중앙 재생 컨트롤(재생/앞뒤 이동)은 테마와 무관하게 흰색
                    // 계열을 유지하되, 순백(#FFFFFF)이면 세로 영상이 레터박스로
                    // 보일 때 흰 배경 위에서 안 보인다 — 살짝 회색을 섞는다.
                    val centerControlTint = android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(playerView.context, R.color.exo_center_control_tint)
                    )
                    listOf(
                        androidx.media3.ui.R.id.exo_prev,
                        androidx.media3.ui.R.id.exo_play_pause,
                        androidx.media3.ui.R.id.exo_next,
                    ).forEach { id ->
                        playerView.findViewById<android.widget.ImageButton>(id)?.imageTintList = centerControlTint
                    }
                    // exo_rew_with_amount/exo_ffwd_with_amount 는 아이콘을
                    // 컴파운드 드로어블이 아니라 **배경 드로어블**로 그린다
                    // (ExoStyledControls.Button.Center.RewWithAmount 스타일의
                    // android:background + backgroundTint=exo_white). 그래서
                    // TextViewCompat.setCompoundDrawableTintList 는 아무 것도
                    // 안 바꿔서 계속 흰색으로 보였다 — 배경 틴트를 직접 덮어써야
                    // 한다.
                    listOf(
                        androidx.media3.ui.R.id.exo_rew_with_amount,
                        androidx.media3.ui.R.id.exo_ffwd_with_amount,
                    ).forEach { id ->
                        playerView.findViewById<android.widget.Button>(id)?.backgroundTintList = centerControlTint
                    }

                    // 진행바(exo_progress)도 같은 톤으로 — 기본값(played/scrubber
                    // 는 순백, buffered/unplayed 는 순백에 알파만 다름)을 중앙
                    // 컨트롤과 같은 회색 섞인 흰색으로 바꾸되, 재생/버퍼링/미재생
                    // 구간의 명암 차이가 드러나도록 미디어3 기본 알파값은 그대로
                    // 유지한다.
                    val progressRgb = androidx.core.content.ContextCompat.getColor(
                        playerView.context,
                        R.color.exo_center_control_tint,
                    ) and 0x00FFFFFF
                    playerView.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
                        setPlayedColor((0xFF shl 24) or progressRgb)
                        setScrubberColor((0xFF shl 24) or progressRgb)
                        setBufferedColor((0xCC shl 24) or progressRgb)
                        setUnplayedColor((0x33 shl 24) or progressRgb)
                    }

                    // §17.49 — 하단 바. 중앙 컨트롤과 달리 이쪽은 영상 위가
                    // 아니라 **테마 배경 위**에 앉으므로 테마를 따라야 한다.
                    //
                    // 시간은 자식을 훑어서 칠한다 — 구분점(`·`) TextView 에는
                    // id 가 없어서 findViewById 로는 못 집는다. 셋 중 하나만
                    // 색이 남으면 그게 더 눈에 띈다.
                    (playerView.findViewById<android.view.ViewGroup>(androidx.media3.ui.R.id.exo_time))?.let { row ->
                        for (i in 0 until row.childCount) {
                            (row.getChildAt(i) as? android.widget.TextView)?.setTextColor(bottomBarTint)
                        }
                    }
                    val bottomTintList = android.content.res.ColorStateList.valueOf(bottomBarTint)
                    listOf(
                        androidx.media3.ui.R.id.exo_subtitle,
                        androidx.media3.ui.R.id.exo_settings,
                    ).forEach { id ->
                        playerView.findViewById<android.widget.ImageButton>(id)?.imageTintList = bottomTintList
                    }
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isLoading && error == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(40.dp),
                strokeWidth = 3.dp
            )
        }

        val currentError = error
        if (currentError != null) {
            VideoErrorOverlay(
                error = currentError,
                onRetry = {
                    error = null
                    isLoading = true
                    val resetSource = if (forceWebDav) VideoSource.WEBDAV else VideoSource.STREAMING
                    if (source == resetSource) {
                        exoPlayer.prepare()
                    } else {
                        source = resetSource
                    }
                },
            )
        }
    }
}

@Composable
private fun VideoErrorOverlay(error: PlaybackException, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.background), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                stringResource(R.string.video_error),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // errorCodeName 을 그대로 보여준다 — 서버마다 실패 원인이 다를 수
            // 있어서(§17.1), 사용자가 이 값을 알려주면 원인을 훨씬 빨리 좁힐 수 있다.
            Text(
                error.errorCodeName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}

/**
 * §17.22 — 동영상은 목록 메타데이터·썸네일과 달리 오프라인 캐싱 대상이
 * 아니라서(§13 결정 기록), 재생 시도 자체가 네트워크를 요구한다. 시도해서
 * 실패하는 걸 기다리는 대신 시도 전에 미리 걸러 보여준다 — 사진이 오프라인
 * 에서 캐시된 목록·썸네일을 곧바로 보여주는 것과 대칭되는 동작이다.
 */
@Composable
private fun NoNetworkOverlay(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Box(modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.background), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                Icons.Filled.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Text(
                stringResource(R.string.video_offline_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(R.string.video_offline_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}

/**
 * Live Photo (Phase 2.8) — 무음 · 반복 · 컨트롤 없음.
 *
 * PhotoPrism 의 "Live"(모션 사진)는 정지 프레임 하나와 그 순간을 감싼 짧은
 * 무음 영상으로 이뤄진다. 사용자가 손대지 않아도 보고 있는 동안 저절로
 * 움직이는 것이 iOS/Google Photos 양쪽의 공통 기대치라, 탭 없이 바로
 * 자동재생·반복한다.
 *
 * 재생 실패 시에도 에러 UI를 띄우지 않는다 — 이미 그 아래 정지 프레임이 깔려
 * 있어서, 실패하면 그냥 "움직이지 않는 사진"으로 조용히 물러나는 게 자연스럽다.
 * 다만 §17.1 조사를 위해 로그는 남긴다.
 */
@UnstableApi
@Composable
fun LivePhotoPlayer(
    uri: String,
    isActive: Boolean,
    zoomState: ZoomState? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val okHttpClient = remember {
        EntryPointAccessors.fromApplication(context, VideoPlayerEntryPoint::class.java).okHttpClient()
    }
    val videoOkHttpClient = remember(okHttpClient) {
        okHttpClient.newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    val exoPlayer = remember(uri) {
        val dataSourceFactory = OkHttpDataSource.Factory(videoOkHttpClient)
            .setUserAgent("VeloVistrixPhotoPrism/1.0")

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(playbackException: PlaybackException) {
                        Log.w(LOG_TAG, "live photo playback failed for $uri: ${playbackException.errorCodeName}", playbackException)
                    }
                })
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(isActive) {
        exoPlayer.playWhenReady = isActive
        if (!isActive) exoPlayer.pause()
    }

    AndroidView(
        factory = { ctx ->
            inflatePlayerView(ctx).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { playerView ->
            if (playerView.player != exoPlayer) {
                playerView.player = exoPlayer
            }

            // 라이브 포토도 줌 상태를 Surface 에 직접 반영함.
            playerView.videoSurfaceView?.let { surface ->
                if (zoomState != null) {
                    surface.scaleX = zoomState.scale
                    surface.scaleY = zoomState.scale
                    surface.translationX = zoomState.offset.x
                    surface.translationY = zoomState.offset.y
                } else {
                    surface.scaleX = 1f
                    surface.scaleY = 1f
                    surface.translationX = 0f
                    surface.translationY = 0f
                }
            }
        },
        onRelease = { it.player = null },
        modifier = modifier.fillMaxSize(),
    )
}
