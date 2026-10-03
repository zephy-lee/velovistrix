package com.regnius.photoprism.feature.slideshow

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.regnius.photoprism.R
import com.regnius.photoprism.core.model.MediaFile
import com.regnius.photoprism.core.model.MediaType
import com.regnius.photoprism.core.model.Photo
import com.regnius.photoprism.core.network.url.ServerUrl
import com.regnius.photoprism.core.network.url.WebDavUrlFactory
import com.regnius.photoprism.core.settings.VideoPlaybackMode
import com.regnius.photoprism.core.ui.ThumbnailLoader
import com.regnius.photoprism.feature.viewer.VideoPlayerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import okhttp3.Interceptor
import java.util.concurrent.TimeUnit
import kotlin.math.min
import okhttp3.Credentials as OkHttpCredentials

private const val LOG_TAG = "SlideshowVideo"

/**
 * 시작 지점을 여기보다 뒤로는 잡지 않는다.
 *
 * 시작 지점은 길이의 20% 인데, 아주 긴 영상(수 분짜리)에서는 그게 1분을 넘는
 * 지점이 된다. 재생 URL 은 서버가 실시간으로 변환해 주는 스트림이라 그만큼
 * 뒤로 시킹하면 버퍼링이 슬라이드 한 장 시간을 통째로 잡아먹는다.
 */
private const val MAX_VIDEO_START_MS = 30_000L

/** 시작 지점을 길이의 몇 지점으로 잡을지. */
private const val VIDEO_START_FRACTION = 0.2

/**
 * 슬라이드쇼에서 동영상을 어디서부터 재생할지 (Phase 3.1).
 *
 * **처음부터 재생하지 않는다.** 폰으로 찍은 영상의 앞 1~2초는 거의 항상 폰을
 * 들어 올리며 프레임을 잡는 구간이라, 3~5초짜리 슬라이드에서는 그 흔들리는
 * 도입부만 보다가 끝난다.
 *
 * 그래서 길이의 20% 지점에서 시작한다. 짧은 영상(대부분의 폰 영상)에서는 딱
 * 도입부만 잘라내는 값이고, 정중앙(50%)처럼 짧은 영상에서 절정을 지나쳐
 * 버리지도 않는다. 긴 영상에서는 [MAX_VIDEO_START_MS] 로 잘라 시킹 비용이
 * 예측 가능한 범위에 머물게 한다.
 *
 * 마지막으로 "끝에서 [clipMs] 만큼 앞" 보다는 뒤로 가지 않게 눌러 준다 —
 * 안 그러면 재생하자마자 영상이 끝나 버린다. 라이브 포토처럼 [clipMs] 보다
 * 짧은 영상은 이 규칙에 걸려 자연스럽게 0(처음부터 전체)이 된다.
 *
 * @param durationMs 영상 전체 길이. 0 이하면(길이를 모르면) 0 을 준다.
 * @param clipMs 이 슬라이드에서 재생할 길이 = 슬라이드 간격.
 */
internal fun slideshowVideoStartMs(durationMs: Long, clipMs: Long): Long {
    if (durationMs <= 0L || clipMs <= 0L) return 0L
    val clip = min(clipMs, durationMs)
    val latestStart = (durationMs - clip).coerceAtLeast(0L)
    val target = min((durationMs * VIDEO_START_FRACTION).toLong(), MAX_VIDEO_START_MS)
    return target.coerceIn(0L, latestStart)
}

/**
 * 슬라이드쇼용 플레이어 두 개를 번갈아 쓴다.
 *
 * 하나로는 부족하다. 서버가 실시간 변환해 주는 스트림에 시킹까지 걸면 준비에
 * 몇 초가 걸릴 수 있는데, 그걸 슬라이드가 화면에 올라온 뒤에 시작하면 3초짜리
 * 슬라이드가 통째로 버퍼링만 하다 끝난다. 그래서 **앞 슬라이드가 보이는 동안
 * 다음 영상을 미리 준비**해 둬야 하고, 앞 슬라이드도 영상이면(앨범의 동영상
 * 탭에서는 늘 그렇다) 플레이어가 두 개여야 한다.
 *
 * 슬롯은 **사진 인덱스가 아니라 슬라이드 순번**(한 장 넘어갈 때마다 1씩 느는
 * 값)의 홀짝으로 나눈다. 인덱스로 나누면 셔플에서 연속한 두 장의 홀짝이 같아질
 * 수 있고, 그러면 다음 장을 준비하다가 지금 재생 중인 걸 덮어쓴다. 순번은 항상
 * 1씩 늘어나므로 N 번과 N+1 번이 같은 슬롯을 쓰는 일이 구조적으로 없다.
 */
@UnstableApi
internal class SlideshowVideoPlayers(
    context: Context,
    dataSourceFactory: DataSource.Factory,
) {
    private val loadedUri = arrayOfNulls<String>(SLOTS)
    private val loadedSlide = arrayOfNulls<Int>(SLOTS)

    /** 길이를 몰라 준비 후에 시킹해야 하는 슬롯의 클립 길이. */
    private val pendingSeekClipMs = arrayOfNulls<Long>(SLOTS)

    private val players: List<ExoPlayer> = List(SLOTS) { slot ->
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                // 3초씩 임의의 지점에서 소리가 튀어나왔다 끊기는 건 꽤 거슬린다.
                // 슬라이드쇼는 여럿이 같이 보는 상황이 많기도 하다.
                volume = 0f
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState != Player.STATE_READY) return
                        // 목록 응답에 길이가 없었던 경우(§17.8 의 merged=true 가
                        // 파일을 흘리는 케이스). 준비가 끝나면 플레이어 자신이
                        // 길이를 알고 있으므로 그때 다시 시킹한다.
                        val clip = pendingSeekClipMs[slot] ?: return
                        pendingSeekClipMs[slot] = null
                        val duration = this@apply.duration
                        if (duration != C.TIME_UNSET && duration > 0) {
                            this@apply.seekTo(slideshowVideoStartMs(duration, clip))
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        // 화면에는 아무것도 안 띄운다 — 아래 정지 프레임이 이미
                        // 깔려 있어서, 실패하면 "안 움직이는 사진"으로 조용히
                        // 물러나는 게 슬라이드쇼 리듬을 안 깬다.
                        Log.w(LOG_TAG, "slideshow playback failed: ${error.errorCodeName}", error)
                    }
                })
            }
    }

    fun playerFor(slideSeq: Int): ExoPlayer = players[slideSeq.mod(SLOTS)]

    /**
     * [slideSeq] 번 슬라이드의 영상을 준비한다. 이미 같은 영상이 올라가 있으면
     * 아무것도 하지 않는다 — 재구성 때마다 다시 준비하면 재생이 계속 끊긴다.
     */
    fun prepare(slideSeq: Int, uri: String, durationMs: Long, clipMs: Long) {
        val slot = slideSeq.mod(SLOTS)
        if (loadedUri[slot] == uri && loadedSlide[slot] == slideSeq) return

        val player = players[slot]
        loadedUri[slot] = uri
        loadedSlide[slot] = slideSeq
        player.playWhenReady = false
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        if (durationMs > 0L) {
            pendingSeekClipMs[slot] = null
            player.seekTo(slideshowVideoStartMs(durationMs, clipMs))
        } else {
            pendingSeekClipMs[slot] = clipMs
        }
    }

    fun release() {
        players.forEach { it.release() }
    }

    private companion object {
        const val SLOTS = 2
    }
}

/**
 * 준비된 플레이어를 화면에 붙인다.
 *
 * 재생 준비가 끝나기 전에는 아무것도 그리지 않는다 — 그 동안은 이 컴포저블
 * 아래 깔린 정지 프레임이 그대로 보인다. 슬라이드 시간 안에 준비가 안 끝나면
 * 그 장은 그냥 정지 사진으로 지나간다.
 */
@UnstableApi
@Composable
internal fun SlideshowVideo(
    player: ExoPlayer,
    uri: String,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    // 한 번 준비된 뒤로는 계속 붙여 둔다. STATE_READY 만 보고 켜고 끄면, 클립이
    // 슬라이드보다 짧아 재생이 끝나는 순간(STATE_ENDED) 마지막 프레임이 사라지고
    // 정지 사진으로 튄다.
    var started by remember(player, uri) { mutableStateOf(player.playbackState == Player.STATE_READY) }

    DisposableEffect(player, uri) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) started = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // 이 슬라이드를 떠나면 반드시 멈춘다 — 안 그러면 화면 밖 영상이
            // 계속 디코딩되며 배터리를 먹는다.
            player.pause()
        }
    }

    LaunchedEffect(player, uri, playing) {
        player.playWhenReady = playing
    }

    if (!started) return

    AndroidView(
        factory = { ctx ->
            // PlayerView 를 코드로 바로 만들면 SurfaceView 로 잡혀 Compose 안에서
            // 새까맣게 보인다 — VideoPlayer.kt 의 같은 이유로 texture_view 를
            // 강제한 레이아웃을 인플레이트한다.
            (android.view.LayoutInflater.from(ctx)
                .inflate(R.layout.video_player_view, null) as PlayerView).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { playerView ->
            if (playerView.player != player) playerView.player = player
        },
        onRelease = { it.player = null },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * 슬라이드쇼가 동영상을 어디서 어떻게 받아올지.
 *
 * @param dataSourceFactory 플레이어가 쓸 네트워크 스택. 스트리밍이냐 WebDAV
 *   원본이냐에 따라 인증 방식이 달라서(Bearer vs Basic) 클라이언트 자체가 다르다.
 * @param uriFor 이 사진을 재생할 URL. 동영상이 아니거나 재생할 파일을 못 찾으면 null.
 */
@UnstableApi
internal class SlideshowVideoSource(
    val dataSourceFactory: DataSource.Factory,
    val uriFor: (Photo) -> String?,
)

/** 동영상/라이브 포토에서 실제로 재생할 파일. 그 외 타입은 null. */
private fun Photo.slideshowVideoFile(): MediaFile? =
    if (type == MediaType.VIDEO || type == MediaType.LIVE) videoFile else null

/** 재생 길이(ms). 목록 응답에 안 들어오는 경우가 있어 0 이 나올 수 있다(§17.8). */
internal fun Photo.slideshowVideoDurationMs(): Long =
    (slideshowVideoFile()?.durationNanos ?: 0L) / 1_000_000L

/**
 * 설정의 재생 방식(§17.1)을 반영해 슬라이드쇼용 재생 소스를 만든다.
 *
 * 뷰어의 [com.regnius.photoprism.feature.viewer.VideoPlayer] 와 달리
 * **스트리밍 실패 → WebDAV 자동 폴백은 하지 않는다.** 뷰어는 4초를 기다렸다
 * 넘어가지만, 슬라이드쇼는 한 장이 3~5초라 그 4초가 슬라이드 전체다. 여기서는
 * 실패하면 그냥 정지 프레임으로 물러나는 것이 이미 자연스러운 폴백이다.
 * 다만 "항상 원본으로 재생"을 켜 둔 사용자는 스트리밍이 아예 안 되는 서버일 수
 * 있으므로, 그 설정만은 그대로 따른다.
 */
@UnstableApi
@Composable
internal fun rememberSlideshowVideoSource(thumbnails: ThumbnailLoader): SlideshowVideoSource {
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context, VideoPlayerEntryPoint::class.java)
    }
    val playbackMode by entryPoint.appSettingsRepository().videoPlaybackMode
        .collectAsState(initial = VideoPlaybackMode.AUTO)

    return remember(playbackMode, thumbnails) {
        val sessionStore = entryPoint.sessionStore()
        val credentials = sessionStore.storedCredentials()
        val webDavBase = sessionStore.session.value?.serverUrl?.let { ServerUrl.normalize(it) }
        // 자격증명이 없으면(public 모드 등) WebDAV 자체가 불가능하다 — 설정이
        // 켜져 있어도 스트리밍으로 돌아간다. 아니면 동영상이 통째로 안 나온다.
        val useWebDav = playbackMode == VideoPlaybackMode.FORCE_WEBDAV &&
            credentials != null && webDavBase != null

        val client = if (useWebDav) {
            // 반드시 인증이 안 붙은 클라이언트에서 시작한다 — 공용
            // SessionAuthenticator 가 WebDAV 의 401 을 "토큰 만료"로 오인해
            // 세션을 지워버리는 문제가 있다(VideoPlayer.kt 참고).
            entryPoint.rawOkHttpClient().newBuilder()
                .callTimeout(0, TimeUnit.SECONDS)
                .addInterceptor(Interceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header(
                                "Authorization",
                                OkHttpCredentials.basic(credentials.username, credentials.secret),
                            )
                            .build()
                    )
                })
                .build()
        } else {
            // 스트리밍은 전체 시간을 제한하면 안 된다 — 긴 영상은 callTimeout 에 걸린다.
            entryPoint.okHttpClient().newBuilder()
                .callTimeout(0, TimeUnit.SECONDS)
                .build()
        }

        SlideshowVideoSource(
            dataSourceFactory = OkHttpDataSource.Factory(client)
                .setUserAgent("VeloVistrixPhotoPrism/1.0"),
            uriFor = { photo ->
                val file = photo.slideshowVideoFile()
                when {
                    file == null -> null
                    useWebDav -> WebDavUrlFactory(webDavBase).original(file)
                    else -> file.hash.takeIf { it.isNotBlank() }?.let { thumbnails.videoUrl(it) }
                }
            },
        )
    }
}
