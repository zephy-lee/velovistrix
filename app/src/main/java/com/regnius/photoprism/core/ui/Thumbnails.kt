package com.regnius.photoprism.core.ui

import android.content.Context
import androidx.compose.runtime.Immutable
import coil3.ImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.regnius.photoprism.core.imageloading.ThumbSizeFallbackInterceptor
import com.regnius.photoprism.core.model.ThumbSize
import com.regnius.photoprism.core.network.PhotoPrismClientProvider
import com.regnius.photoprism.core.network.url.ThumbUrlFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 썸네일 요청을 한 곳에서 만든다.
 *
 * URL 조립과 **캐시 키 지정**이 흩어지면 성능이 조용히 무너진다. 특히
 * `diskCacheKey` 를 빠뜨리면, 서버가 previewToken 을 회전하는 순간 URL 이 전부
 * 바뀌어 512MB 디스크 캐시가 통째로 무효화된다 (§4.2). 그런 사고는 재현이
 * 어렵고 원인을 찾기도 어렵다. 그래서 요청 생성 경로를 여기 하나로 좁힌다.
 */
@Singleton
class ThumbnailLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clientProvider: PhotoPrismClientProvider,
    private val imageLoader: ImageLoader,
) {
    private val factory: ThumbUrlFactory? get() = clientProvider.thumbUrlFactory()

    fun url(fileHash: String, size: ThumbSize): String? =
        factory?.thumb(fileHash, size)

    fun albumCoverUrl(albumUid: String, size: ThumbSize = ThumbSize.ALBUM_COVER): String? =
        factory?.albumCover(albumUid, size)

    fun videoUrl(fileHash: String): String? = factory?.video(fileHash)

    /**
     * **디스크 캐시 키만 직접 정한다.**
     *
     * 디스크 쪽은 URL 에 미리보기 토큰이 섞여 있어(토큰이 바뀌면 캐시가 통째로
     * 무효화된다) 해시+사이즈로 고정해야 한다. 반면 메모리 캐시 키는 Coil 이
     * 정하도록 **놔둔다** — Coil 의 기본 키에는 디코딩한 크기가 들어가는데,
     * 우리가 해시+사이즈로 덮어쓰면 그 정보가 사라져서 같은 타일을 쓰는 서로
     * 다른 밀도가 비트맵을 공유해 버린다. 큰 셀에서 만든 비트맵이 작은 셀에
     * 재사용되면 메모리가 몇 배로 뜨고, 반대 방향이면 흐려진다(§17.34).
     */
    fun request(
        fileHash: String,
        size: ThumbSize,
        crossfade: Boolean = true,
        /**
         * 이 크기가 없을 때 **캐시의 다른 크기로 대신하지 말라**는 뜻. §17.44
         *
         * 프리페치가 켠다. 프리페치의 존재 이유가 제 크기를 미리 받아 두는
         * 것이라, 대체로 만족해 버리면 캐시가 영영 안 채워진다.
         */
        exactSizeOnly: Boolean = false,
    ): ImageRequest? {
        val url = url(fileHash, size) ?: return null
        return ImageRequest.Builder(context)
            .data(url)
            .diskCacheKey(ThumbUrlFactory.diskCacheKey(fileHash, size))
            .crossfade(crossfade)
            .apply { if (exactSizeOnly) extras[ThumbSizeFallbackInterceptor.SKIP_FALLBACK] = true }
            .build()
    }

    fun albumCoverRequest(albumUid: String, size: ThumbSize = ThumbSize.ALBUM_COVER): ImageRequest? {
        val url = albumCoverUrl(albumUid, size) ?: return null
        return ImageRequest.Builder(context)
            .data(url)
            .diskCacheKey(ThumbUrlFactory.albumCoverDiskCacheKey(albumUid, size))
            .crossfade(true)
            .build()
    }

    /**
     * 아직 화면에 없는 썸네일을 미리 받아둔다. §12.3
     *
     * Paging 의 prefetchDistance 는 **데이터(JSON)** 만 당겨온다. 이미지는 별도로
     * 당겨야 하고, 그걸 안 하면 빠르게 스크롤할 때 회색 사각형만 지나간다.
     */
    fun prefetch(fileHashes: List<String>, size: ThumbSize): List<Disposable> =
        fileHashes.mapNotNull { hash ->
            // `exactSizeOnly` — 미리 받는 목적이 **제 크기를 채워 두는 것**이다.
            // 대체 크기로 만족하면 캐시가 영영 안 채워진다 (§17.44).
            request(hash, size, exactSizeOnly = true)?.let { imageLoader.enqueue(it) }
        }
}

/**
 * 아직 안 끝난 프리페치를 **취소할 수 있게** 들고 있는다. §17.38
 *
 * 프리페치는 큐에 넣고 끝이었는데, 그게 길게 튕기는 스크롤에서 화면을 통째로
 * 비워 버린다. 45번 밀면 지나쳐 온 수백 장이 전부 큐에 남고, 손을 뗀 뒤
 * **눈앞의 여덟 칸이 그 뒤에 줄을 선다.** 이미 지나가서 아무도 안 볼 사진을
 * 다 받을 때까지 기다리는 셈이다.
 *
 * 실측(2열, 캐시 없는 구간):
 * - 45번 밀기 → 0초 백지, 3초 백지, **10초에 채워짐**
 * - 4번 밀기 → **1.5초에 채워짐**
 *
 * 같은 크기·같은 서버·같은 캐시 상태다. 다른 건 큐에 쌓인 양뿐이었다.
 *
 * 창이 움직이면 직전 묶음은 이미 쓸모가 없으니 버린다. 남는 대기열이 한
 * 묶음(최대 [PREFETCH_MAX])을 넘지 않는다. 이미 끝난 요청을 버리는 건
 * 무해하다 — 받아 둔 것은 캐시에 그대로 있다.
 */
internal class PrefetchWindow {
    private var pending: List<Disposable> = emptyList()

    fun replace(next: List<Disposable>) {
        pending.forEach { if (!it.isDisposed) it.dispose() }
        pending = next
    }
}

/**
 * 그리드 밀도 — 설정에서 바꿀 수 있도록 값으로 표현한다 (Phase 2.11).
 *
 * **[thumbSize] 는 셀 크기에 맞춰 고른다.** `GridCells.Adaptive` 는 셀을
 * [minCellDp] 이상으로 만들므로 셀 픽셀 ≈ `minCellDp × 화면 배율` 이다.
 * 1080px/384dp 기기(배율 2.8) 기준으로:
 *
 * | | 열 | 셀 | 타일 |
 * |---|---|---|---|
 * | COMFORTABLE | 2 | 540px | `tile_500` |
 * | DEFAULT | 3 | 309px | `tile_500` |
 * | COMPACT | 6 | 169px | `tile_224` |
 * | VERY_COMPACT | 10 | 101px | `tile_100` |
 *
 * 예전에는 아래 셋이 전부 `tile_224` 였다(§17.34). DEFAULT 는 224px 를 309px
 * 로 늘려 써서 흐렸고, VERY_COMPACT 는 반대로 픽셀이 4배 남아 스크롤이
 * 버벅였다 — 한쪽은 화질, 한쪽은 성능 문제였는데 원인이 같았다.
 */
@Immutable
enum class GridDensity(val minCellDp: Int, val thumbSize: ThumbSize) {
    COMFORTABLE(140, ThumbSize.GRID_DENSE),

    /**
     * §17.37 — `tile_500` 으로 올렸다가 **되돌렸다.**
     *
     * 셀이 360px 라 224px 는 늘려 쓰는 셈이고 그래서 조금 무르다. 그 화질을
     * 노리고 `tile_500` 으로 바꿨는데, 서버가 그 크기를 아직 안 만들어 뒀으면
     * **요청받고 원본에서 생성**한다. 기본 화면이라 한 번에 수십 장을
     * 요청하니 서버가 포화돼서, 중앙값 500ms · 1초 초과 150건 · 접속
     * 타임아웃까지 나왔다. 아무도 흐리다고 하지 않은 것을 고치려다
     * 기본 화면을 느리게 만들었다.
     */
    DEFAULT(110, ThumbSize.GRID_COMPACT),
    /** 가로 약 6개 (Phase 2.11 수정) */
    COMPACT(60, ThumbSize.GRID_COMPACT),
    /** 가로 약 10개 (Phase 2.11 추가) */
    VERY_COMPACT(36, ThumbSize.GRID_TINY),
}

/**
 * 이 밀도에서 한 줄에 몇 칸이 들어가는지 어림한다.
 *
 * `GridCells.Adaptive` 가 실제 열 수를 정하므로 정확한 값은 레이아웃이 끝나야
 * 알 수 있지만, 프리페치 분량을 정하는 데는 어림수면 충분하다 — 매 프레임
 * `layoutInfo` 를 읽어 정확히 세는 쪽이 오히려 비싸다.
 */
internal fun GridDensity.columnsPerScreenEstimate(): Int =
    (TYPICAL_SCREEN_WIDTH_DP / minCellDp).coerceAtLeast(1)

/** 폰 세로 기준. 태블릿에서는 실제 열이 더 많지만 프리페치 하한으로는 안전하다. */
private const val TYPICAL_SCREEN_WIDTH_DP = 384
