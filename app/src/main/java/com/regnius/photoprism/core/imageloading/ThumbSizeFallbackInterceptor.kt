package com.regnius.photoprism.core.imageloading

import coil3.Extras
import coil3.ImageLoader
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.regnius.photoprism.core.model.ThumbSize
import com.regnius.photoprism.core.network.url.ThumbUrlFactory
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 요청한 크기가 없으면 **캐시에 있는 다른 크기라도** 먼저 보여준다. §17.36 · §17.44
 *
 * 그리드 밀도마다 다른 타일을 쓰기로 하면서(§17.34) 부작용이 생겼다. 크기가
 * 다르면 URL 도 디스크 캐시 키도 달라서, 6칸으로만 훑어 둔 뒤 2칸으로 바꾸면
 * **그 크기는 받아온 적이 없다.** 사진은 분명 기기에 있는데 화면은 비어 버린다.
 *
 * ### §17.44 — 순서를 뒤집었다
 *
 * 처음에는 이 대체를 **실패 경로**에 달았다. `chain.proceed()` 를 끝까지
 * 기다렸다가 실패했을 때만 다른 크기를 찾는 방식이다. 비행기 모드에서는
 * 즉시 실패하니 잘 보였지만, 네트워크가 *살아는 있는데 느릴 때* 는 응답을
 * 끝까지 기다리느라 디스크에 멀쩡한 타일을 두고도 몇 초씩 백지였다.
 *
 * 하필 가장 나쁠 때 가장 오래 빈다. 서버가 제일 느린 순간은 **없는 크기를
 * 원본에서 생성할 때**인데(§17.37), 그게 정확히 "요청한 크기가 캐시에 없는"
 * 순간이다 — 대체가 가장 필요한 바로 그 순간이다.
 *
 * > 비행기 모드가 약한 네트워크보다 더 잘 동작한다는 것 자체가 신호였다.
 * > 완전히 끊겼을 때는 우아하게 물러나면서 반쯤 연결됐을 때 멎는다면,
 * > 실패 감지를 잘못된 신호에 걸어 둔 것이다.
 *
 * 그래서 **"맞는 크기, 그때까지 백지"** 를 **"지금 있는 걸 먼저, 맞는 건 곧"**
 * 으로 바꿨다:
 *
 * 1. 요청한 크기를 **캐시에서만** 본다 → 있으면 그대로. 데워진 경우 비용 0
 * 2. 없으면 다른 크기를 캐시에서 찾는다 → 있으면 **즉시** 그것을 준다
 * 3. 그것도 없을 때만 네트워크를 기다린다 (예전 동작)
 *
 * ### 늘려 쓴 경우에만 뒤늦게 제 크기를 받아 둔다
 *
 * 2단계로 끝내면 캐시에 `tile_224` 가 있는 한 2칸은 **영영 부옇다** — 제 크기를
 * 받을 일이 없어진다. 그래서 대체가 **확대**일 때만(=눈에 띄게 무를 때만) 제
 * 크기를 백그라운드로 받아 둔다. 축소는 그냥 둔다 — 10칸에서 `tile_224` 를
 * 줄여 쓰는 건 티가 안 나고, 칸이 200개가 넘는 밀도라 여기서 요청을 만들면
 * 그게 곧 §17.38 의 대기열 문제다.
 *
 * 그 백그라운드 요청도 [MAX_UPGRADES] 개까지만 동시에 띄우고 넘치면 **버린다.**
 * 줄을 세우지 않으므로 밀린 요청이 쌓일 수가 없다 — §17.38 에서 프리페치가
 * 눈앞의 칸을 밀어냈던 일을 여기서 되풀이하지 않으려는 것이다.
 */
@Singleton
class ThumbSizeFallbackInterceptor @Inject constructor(
    /**
     * `Provider` 여야 한다. [ImageLoader] 를 만들 때 이 인터셉터가 필요하므로
     * 직접 주입하면 순환이 된다. 실제로 꺼내 쓰는 시점에는 이미 다 만들어져 있다.
     */
    private val imageLoader: Provider<ImageLoader>,
) : Interceptor {

    private val upgradesInFlight = AtomicInteger(0)

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data as? String
        val parsed = url?.let { ThumbUrlFactory.parseThumb(it) }

        // 썸네일이 아니거나(뷰어의 `fit_*`, 앨범 커버) 대체를 쓰지 말라고 표시된
        // 요청은 손대지 않는다.
        if (parsed == null || request.getExtra(SKIP_FALLBACK)) return chain.proceed()
        val (hash, requested) = parsed

        // ① 요청한 크기를 캐시에서만 본다. 데워져 있으면 여기서 끝이고,
        //    이 경로에 추가 비용은 없다 — 어차피 하던 메모리·디스크 조회다.
        val cached = chain.withRequest(
            request.newBuilder().networkCachePolicy(CachePolicy.DISABLED).build()
        ).proceed()
        if (cached is SuccessResult) return cached

        // ② 캐시에 있는 다른 크기를 **가까운 것부터** 찾는다.
        for (alternative in FALLBACKS[requested].orEmpty()) {
            val alternativeRequest = request.newBuilder()
                .data(ThumbUrlFactory.withSize(url, alternative))
                .diskCacheKey(ThumbUrlFactory.diskCacheKey(hash, alternative))
                .networkCachePolicy(CachePolicy.DISABLED)
                .build()
            val fallback = chain.withRequest(alternativeRequest).proceed()
            if (fallback is SuccessResult) {
                if (alternative.pixels < requested.pixels) {
                    warmRealSize(request.context, url, hash, requested)
                }
                return fallback
            }
        }

        // ③ 기기에 아무것도 없다. 그때는 네트워크를 기다리는 수밖에 없다.
        return chain.proceed()
    }

    /**
     * 제 크기를 조용히 받아 **디스크 캐시에만** 넣어 둔다. 다음에 이 사진을 볼
     * 때는 ① 에서 바로 걸린다.
     *
     * 화면에 붙이지 않으려고 요청을 새로 만든다 — 원본 요청을 `newBuilder` 로
     * 베끼면 Compose 가 준 target 과 size resolver 가 따라온다. 그 size resolver
     * 는 **처음 그려질 때** 크기가 정해지는 종류라, 그리지 않는 요청에 달면
     * 영원히 기다린다(§17.39 에서 뷰어 밑그림을 알파로 숨긴 것과 같은 이유).
     */
    private fun warmRealSize(
        context: coil3.PlatformContext,
        url: String,
        hash: String,
        size: ThumbSize,
    ) {
        if (upgradesInFlight.get() >= MAX_UPGRADES) return
        upgradesInFlight.incrementAndGet()
        val warm = ImageRequest.Builder(context)
            .data(url)
            .diskCacheKey(ThumbUrlFactory.diskCacheKey(hash, size))
            // 노리는 것은 디스크다. 화면에 안 쓸 비트맵으로 메모리 캐시를
            // 밀어내면 정작 보이는 칸이 쫓겨난다.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .apply { extras[SKIP_FALLBACK] = true }
            .listener(
                onCancel = { upgradesInFlight.decrementAndGet() },
                onError = { _, _ -> upgradesInFlight.decrementAndGet() },
                onSuccess = { _, _ -> upgradesInFlight.decrementAndGet() },
            )
            .build()
        imageLoader.get().enqueue(warm)
    }

    companion object {
        /**
         * 이 요청에는 대체를 쓰지 말라는 표시.
         *
         * 두 군데서 쓴다. 하나는 위 [warmRealSize] — 대체를 허용하면 자기가
         * 자기를 다시 부르는 무한 고리가 된다. 다른 하나는 프리페치다.
         * 프리페치의 존재 이유가 **제 크기를 미리 받아 두는 것**인데 대체로
         * 만족해 버리면 캐시가 영영 안 채워진다.
         */
        val SKIP_FALLBACK = Extras.Key(default = false)

        /**
         * 동시에 띄울 "제 크기 받아 두기" 개수. 작게 잡는다 — 이건 있으면 좋은
         * 일이지 지금 필요한 일이 아니고, 보이는 칸의 대역을 뺏으면 안 된다.
         */
        private const val MAX_UPGRADES = 3

        /**
         * 크기별로 대신 쓸 후보. **가까운 크기부터** 본다 — 너무 작은 것을
         * 크게 늘리면 눈에 띄게 뭉개진다.
         *
         * 뷰어가 쓰는 `fit_*` 는 넣지 않는다. 뷰어는 이미 작은 그림을 밑에
         * 까는 방식으로 같은 문제를 해결해 뒀다(§17.31 · §17.39).
         */
        private val FALLBACKS = mapOf(
            ThumbSize.GRID_TINY to listOf(ThumbSize.GRID_COMPACT, ThumbSize.GRID_DENSE),
            ThumbSize.GRID_COMPACT to listOf(ThumbSize.GRID_DENSE, ThumbSize.GRID_TINY),
            ThumbSize.GRID_DENSE to listOf(ThumbSize.GRID_COMPACT, ThumbSize.GRID_TINY),
        )
    }
}
