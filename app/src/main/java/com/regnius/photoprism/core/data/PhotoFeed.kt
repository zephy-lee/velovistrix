package com.regnius.photoprism.core.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.regnius.photoprism.core.data.local.AppDatabase
import com.regnius.photoprism.core.data.local.dao.AlbumDao
import com.regnius.photoprism.core.data.local.toDomain
import com.regnius.photoprism.core.data.local.toEntity
import com.regnius.photoprism.core.di.ApplicationScope
import com.regnius.photoprism.core.model.Album
import com.regnius.photoprism.core.model.Photo
import com.regnius.photoprism.core.network.PhotoPrismClientProvider
import com.regnius.photoprism.core.network.PhotoQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사진 페이징 스트림을 만들고 **앱 스코프에 캐시한다.**
 *
 * 캐시가 핵심이다. 그리드에서 사진을 탭해 뷰어로 들어갈 때, 뷰어가 같은 목록을
 * 서버에서 다시 받으면 열자마자 빈 화면이 뜨고 몇백 ms 를 기다려야 한다.
 * 여기서 [cachedIn] 으로 붙들어 두면 뷰어는 이미 로드된 페이지를 그대로 받아
 * 즉시 렌더링된다 (§12.5 목표: 그리드 → 뷰어 100ms).
 *
 * **캐시 슬롯이 하나가 아니라 여러 개다.** 앨범 상세는 이미지·동영상 두 탭을
 * [androidx.compose.foundation.pager.HorizontalPager] 로 나란히 두는데, Pager 는
 * 스와이프를 부드럽게 하려고 현재 페이지 옆의 탭도 미리 구성해 둔다. 슬롯이
 * 하나뿐이면 두 탭이 번갈아 `feed()` 를 부를 때마다 서로의 캐시를 밀어내고,
 * 그 상태에서 사진을 눌러 뷰어를 열면 뷰어가 그리드와 다른(밀려난) 스트림을
 * 받아 **처음부터 다시 불러오게** 된다 — §12.5 가 막으려던 바로 그 상황이다.
 * 최근 사용한 [MAX_CACHED_KEYS] 개까지 살려 두면 이미지 탭·동영상 탭·뷰어가
 * 동시에 살아 있어도 서로를 밀어내지 않는다.
 *
 * Phase 2.9 — 실제 데이터는 이제 Room([AppDatabase])에 영속 캐싱된다.
 * [PhotoFeedRemoteMediator] 가 네트워크→Room 을 채우고, Room 이 생성한
 * [androidx.paging.PagingSource] 를 Paging 3 가 관찰한다 — 오프라인에서
 * REFRESH 가 실패해도 Room 에 남은 이전 목록이 그대로 화면에 보인다.
 * 이 메모이제이션은 그대로 유지한다 — 없으면 그리드↔뷰어 왕복마다
 * [RemoteMediator.initialize][androidx.paging.RemoteMediator.initialize] 가
 * 다시 돌며 불필요한 네트워크 REFRESH 가 중복된다.
 */
@OptIn(ExperimentalPagingApi::class, FlowPreview::class)
@Singleton
class PhotoFeedController @Inject constructor(
    private val clientProvider: PhotoPrismClientProvider,
    private val db: AppDatabase,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    private val cache = object : java.util.LinkedHashMap<FeedKey, Flow<PagingData<Photo>>>(
        MAX_CACHED_KEYS, 0.75f, true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FeedKey, Flow<PagingData<Photo>>>) =
            size > MAX_CACHED_KEYS
    }

    @Synchronized
    fun feed(key: FeedKey): Flow<PagingData<Photo>> {
        cache[key]?.let { return it }
        val api = clientProvider.currentApi ?: return emptyFlow()
        val feedKeyString = key.cacheKey()
        // §17.15 — 이 피드 전용 무효화 스위치. Room 의 테이블 단위 자동
        // 무효화 대신, PhotoFeedRemoteMediator 가 *이 feedKeyString* 에
        // 실제로 새 행을 쓴 직후에만 이걸 부른다 — 그래야 앨범 상세의
        // 형제 탭(이미지/동영상)이 서로의 쓰기로 서로를 재생성하지 않는다.
        // 자세한 사정은 PhotoDao.page / PhotoFeedRemoteMediator 클래스 문서.
        val pagingSourceFactory = InvalidatingPagingSourceFactory {
            PhotoFeedLocalPagingSource(feedKeyString, db.photoDao(), db.photoFeedDao())
        }
        // §17.17 — 빠르게 연속 스크롤하면 APPEND 네트워크 응답이 짧은
        // 간격(수십~수백 ms)으로 연달아 도착해 매번 무효화 → 재생성이
        // 일어난다. `enablePlaceholders = false` 라 재생성 직후 창이
        // 앵커 근처로 다시 작게 잘리는데, 로컬이 그 창을 다시 넓히기도
        // 전에 다음 무효화가 덮쳐 화면 개수가 계속 줄었다 늘었다를
        // 반복했다(실기 로그로 확인). 무효화 신호를 짧게 debounce 해
        // 몰아친 여러 번을 한 번으로 묶는다 — 로컬이 다시 넓힐 시간을
        // 벌어준다. 데이터 자체는 이미 Room 에 다 쓰여 있으니, 화면
        // 반영이 최대 [INVALIDATE_DEBOUNCE_MS] 만큼 늦어질 뿐이다.
        val invalidateSignal = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        appScope.launch {
            invalidateSignal.debounce(INVALIDATE_DEBOUNCE_MS).collect { pagingSourceFactory.invalidate() }
        }
        var flow = Pager(
            config = pagingConfig(),
            remoteMediator = PhotoFeedRemoteMediator(
                key, feedKeyString, api, db,
                invalidate = { invalidateSignal.tryEmit(Unit) },
            ),
            pagingSourceFactory = pagingSourceFactory,
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }
        key.requireLocation?.let { required ->
            flow = flow.map { paging -> paging.filter { it.hasLocation == required } }
        }
        val cached = flow.cachedIn(appScope)
        cache[key] = cached
        return cached
    }

    /** 로그아웃/서버 변경 시 이전 서버의 데이터가 남지 않도록 비운다. */
    suspend fun clear() {
        synchronized(this) { cache.clear() }
        withContext(Dispatchers.IO) {
            db.photoDao().deleteAll()
            db.photoFeedDao().deleteAllEntries()
            db.feedRemoteKeyDao().deleteAll()
        }
    }

    /**
     * 당겨서 새로고침(2.10) 전용. [PhotoFeedRemoteMediator] 는 REFRESH 가
     * 자기 자신의 APPEND 쓰기로 인한 자기무효화인지, 사용자가 진짜로
     * 새로고침을 요청한 것인지 구분할 방법이 없다 — 이 피드의 remote key
     * 에 [FeedRemoteKeyEntity.forceRefresh] 를 세워서 "이건 진짜 새로고침"
     * 이라는 신호를 준다.
     *
     * **행을 지우지 않는다(§17.16)** — 예전엔 지웠는데, 그러면 이미 훨씬
     * 뒤까지 가 있던 `nextOffset` 진행률까지 같이 사라져서 다음 REFRESH 가
     * `offset=0` 부터 다시 시작했다. `photo_feed_entries` 는 그대로 남아
     * 있으니 결과적으로 "네트워크 진행률만 리셋되고 로컬 데이터는 안
     * 지워지는" 불일치가 생겨, 이후 APPEND 가 이미 로컬에 있는 구간을
     * 다시 훑으며 가끔 새로 겹치지 않는 몇 장을 찾을 때마다 무효화가
     * 일어나 화면 숫자가 줄었다 늘었다를 반복했다(실기 로그로 확인).
     */
    suspend fun invalidateFeed(key: FeedKey) {
        withContext(Dispatchers.IO) {
            db.feedRemoteKeyDao().markForceRefresh(key.cacheKey())
        }
    }

    companion object {
        /**
         * §12.1 — 페이징 설정.
         *
         * `initialLoadSize` 를 pageSize 의 2배로 잡아 첫 화면을 한 번에 채운다.
         * 이게 없으면 그리드가 60개로 잠깐 찼다가 스크롤도 안 했는데 다음
         * 페이지를 불러오는 깜빡임이 생긴다.
         *
         * `enablePlaceholders = true`(§17.18) — 예전엔 false 였다. 그 이유는
         * "PhotoPrism 검색 API 가 전체 개수를 정확히 주지 않아 placeholder
         * 개수를 알 수 없다" 였는데, 이건 **서버** 기준 총량 얘기다. Phase 2.9
         * 로 Room 캐시가 생긴 지금은 [PhotoFeedLocalPagingSource] 가 **로컬에
         * 이미 캐싱된 정확한 개수**(`PhotoFeedDao.countForFeed`)를 알고
         * 있으므로 그 값으로 placeholder 를 채울 수 있다. 이게 없으면 앱을
         * 재시작해 이미 다 캐싱해둔 피드를 다시 열어도 `initialLoadSize`
         * (120) 만큼만 읽혀서 "120+" 부터 다시 보였다(§17.18 버그 리포트).
         */
        fun pagingConfig() = PagingConfig(
            pageSize = 60,
            initialLoadSize = 120,
            prefetchDistance = 40,
            enablePlaceholders = true,
        )

        /**
         * 앨범 하나가 이미지+동영상 탭 2개를 동시에 쓴다(§ 클래스 문서) — 4로
         * 두면 앨범 두 개만 옮겨 다녀도 첫 번째 앨범이 밀려나 "분명 캐싱된
         * 적 있는데 다시 받아온다"는 것처럼 보인다. 타임라인/즐겨찾기/검색
         * 까지 오가며 앨범 여러 개를 둘러보는 평범한 사용 패턴에 여유가
         * 남도록 10으로 올린다.
         */
        private const val MAX_CACHED_KEYS = 10

        /** §17.17 — 무효화 신호 debounce 간격. `feed()` 문서 참고. */
        private const val INVALIDATE_DEBOUNCE_MS = 300L
    }
}

/**
 * 앨범 목록.
 *
 * 사진과 달리 **페이징하지 않는다.** 앨범은 사용자당 많아야 수백 개
 * 수준이라 한 번에 다 받아오는 편이 오히려 낫다 — 정렬 기준을 바꿀 때마다
 * (§ [AlbumSort]) 서버에 다시 물어볼 필요 없이 메모리에서 즉시 재정렬된다.
 *
 * Phase 2.9 — 성공한 조회만 [AlbumDao] 에 통째로 교체 저장한다. 오프라인
 * 등으로 조회에 실패하면 Room 에 남은 마지막 목록으로 폴백한다.
 */
@Singleton
class AlbumRepository @Inject constructor(
    private val clientProvider: PhotoPrismClientProvider,
    private val albumDao: AlbumDao,
) {
    private var cached: List<Album>? = null

    /** 캐시가 있으면 그대로, 없으면 서버에서 받아온다. */
    suspend fun albums(forceRefresh: Boolean = false): Result<List<Album>> {
        cached?.let { if (!forceRefresh) return Result.success(it) }
        val api = clientProvider.currentApi ?: return offlineFallback()
        return runCatching {
            // count 는 넉넉히 — 앨범 수백 개까지는 한 번에 받는 편이 정렬
            // 재요청을 없애는 이득이 훨씬 크다.
            api.getAlbums(count = ALBUM_FETCH_LIMIT, type = "album", order = "name")
                .map { it.toDomain() }
        }.onSuccess { albums ->
            cached = albums
            withContext(Dispatchers.IO) {
                albumDao.replaceAll(albums.mapIndexed { i, a -> a.toEntity(orderIndex = i) })
            }
        }.recoverCatching { e ->
            offlineFallback().getOrElse { throw e }
        }
    }

    /**
     * 네트워크를 건드리지 않고 **캐시에 있는 것만** 돌려준다. 없으면 빈 목록.
     *
     * [albums] 는 서버를 먼저 부르고 실패했을 때만 Room 으로 내려가므로, 캐시가
     * 멀쩡히 있어도 콜드 스타트마다 서버 왕복을 기다리게 된다(§17.33). 화면이
     * 먼저 뭔가를 그릴 수 있도록 이 길을 따로 연다.
     *
     * **메모리 캐시([cached])는 채우지 않는다** — 채우면 뒤이은 [albums] 호출이
     * 그걸 그대로 돌려주고 서버를 아예 안 부른다.
     */
    suspend fun cachedAlbums(): List<Album> =
        withContext(Dispatchers.IO) { albumDao.getAll().map { it.toDomain() } }

    private suspend fun offlineFallback(): Result<List<Album>> {
        val local = withContext(Dispatchers.IO) { albumDao.getAll().map { it.toDomain() } }
        return if (local.isNotEmpty()) {
            cached = local
            Result.success(local)
        } else {
            Result.failure(IllegalStateException("no session"))
        }
    }

    suspend fun clear() {
        cached = null
        withContext(Dispatchers.IO) { albumDao.clear() }
    }

    suspend fun album(uid: String) =
        clientProvider.currentApi?.getAlbum(uid)?.toDomain()

    private companion object {
        const val ALBUM_FETCH_LIMIT = 1000
    }
}
