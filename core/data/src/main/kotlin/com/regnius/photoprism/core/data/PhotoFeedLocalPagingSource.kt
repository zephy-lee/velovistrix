package com.regnius.photoprism.core.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.regnius.photoprism.core.data.local.dao.PhotoDao
import com.regnius.photoprism.core.data.local.dao.PhotoFeedDao
import com.regnius.photoprism.core.data.local.entity.PhotoEntity
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 한 피드의 Room 캐시를 오프셋 기준으로 읽는 손수 짠 [PagingSource].
 *
 * Room 자동 생성 버전을 쓰지 않는 이유는 [PhotoDao.page] 문서 참고 — 테이블
 * 단위 [androidx.room.InvalidationTracker] 가 다른 피드의 쓰기에도 이
 * 소스를 무효화시켜, 화면 밖에 살아 있는 형제 탭(앨범의 이미지/동영상 탭)이
 * 서로의 스크롤 때문에 계속 처음 크기로 리셋되는 문제가 있었다(§17.15).
 *
 * 대신 [com.regnius.photoprism.core.data.PhotoFeedController.feed] 가
 * `feedKey` 당 하나씩 만드는 `InvalidatingPagingSourceFactory` 를 통해서만
 * 무효화된다 — [PhotoFeedRemoteMediator] 가 **이 피드에** 실제로 새 행을
 * 쓴 직후에만 명시적으로 호출한다.
 *
 * `data.size < limit` 을 "로컬 소진" 신호로 쓴다 — `LIMIT n OFFSET k` 쿼리가
 * `m < n` 행을 돌려줬다면 그 피드엔 정확히 `k + m` 행이 전부라는 뜻이라
 * (동시에 더 쓰는 코드가 없는 한) 안전한 판정이다. 로컬이 소진됐다고
 * `nextKey = null` 을 반환하는 게 바로 Paging 3 가 [RemoteMediator] 의
 * APPEND 를 트리거하는 신호다 — §17.14 가 확인한 그 계약 그대로다.
 *
 * **PREPEND 의 key 는 "시작 오프셋"이 아니라 배타적 상한이다(§17.21).**
 * REFRESH/APPEND 는 `key` 를 "여기서부터 읽어라"로 쓰지만, PREPEND 는
 * Room `LimitOffsetPagingSource` 와 같은 관례로 `key` 를 "여기 **직전까지**
 * 읽어라"(배타적 상한)로 해석해야 한다. 처음엔 이걸 몰라서 PREPEND 도
 * `[key, key+loadSize)` 로 읽었는데, `initialLoadSize`(120)로 읽은 REFRESH
 * 페이지의 `prevKey` 를 나중에 `pageSize`(60)짜리 PREPEND 가 소비하면
 * 두 loadSize 가 달라 두 페이지 사이에 60개짜리 간격/겹침이 생겼다 —
 * Paging 3 는 한 세대의 모든 페이지가 절대 위치상 빈틈없이 이어붙는다고
 * 가정하므로, 이게 깨지면 내부 위치 계산이 꼬여 같은 사진이 그리드에
 * 두 번 나타나고 `LazyVerticalGrid` 가 "Key ... already used" 로 죽었다
 * (§17.13, §17.19 에서도 같은 증상이 반복됐던 진짜 원인 — 그 두 번의
 * 수정은 다른 원인을 잡은 것이었고 이게 세 번째이자 근본 원인이다).
 * 지금은 모든 페이지의 `prevKey` 를 "이 페이지가 시작하는 절대 오프셋"
 * (`offset`) 그 자체로 두고, PREPEND 요청이 오면 그 오프셋을 배타적
 * 상한으로 해석해 `[max(0, key - loadSize), key)` 를 읽는다 — 이러면
 * PREPEND 의 loadSize 가 원래 페이지의 limit 와 달라도 항상 정확히
 * 그 오프셋에서 끝나 빈틈 없이 이어붙는다.
 *
 * **placeholder 를 켠다(§17.18)** — [PhotoFeedDao.countForFeed] 로 이
 * 피드에 로컬로 이미 캐싱된 진짜 총량을 알 수 있으니, `itemsBefore`/
 * `itemsAfter` 를 채워 [androidx.paging.compose.LazyPagingItems.itemCount]
 * 가 "지금 이 세대가 우연히 읽어들인 창 크기" 가 아니라 "로컬에 실제로
 * 캐싱된 개수" 를 항상 반영하게 한다. 이게 없으면 (a) 앱을 재시작해 새
 * 세대가 열릴 때마다 `initialLoadSize`(120) 만큼만 읽혀서, 이미 수백
 * 장을 다 캐싱해둔 피드도 다시 열 때마다 "120+" 부터 보였고, (b) §17.15/
 * §17.17 이 완화한 "재생성 직후 창이 작게 잘리는" 현상도 placeholder가
 * 없을 때는 화면 개수 자체가 줄어드는 걸로 보였다 — placeholder 를 켜면
 * 재생성 직후에도 총량은 그대로 유지되고, 아직 다시 안 읽힌 자리만
 * 회색 칸(placeholder)으로 잠깐 보였다가 로컬 재읽기로 바로 채워진다.
 *
 * **총량은 세대당 딱 한 번만 재고, 그 뒤로는 고정한다(§17.19).** 처음엔
 * [PhotoFeedDao.countForFeed] 를 매 [load] 호출마다 새로 쿼리했는데,
 * 한 세대가 살아있는 동안(§17.17 의 debounce 로 그 수명이 늘어났다)
 * [PhotoFeedRemoteMediator] 가 백그라운드에서 계속 새 행을 쓰면 총량이
 * 호출마다 달라진다 — Paging 3 는 같은 세대의 여러 페이지가
 * `itemsBefore + data.size + itemsAfter` 로 계산되는 전체 총량에
 * **합의**하고 있다고 가정한다. 이게 어긋나면 내부 위치 계산이 꼬여
 * 같은 사진이 서로 다른 위치에 두 번 나타날 수 있고, `LazyVerticalGrid`
 * 의 키 기반 `items()` 가 "Key ... was already used" 로 죽는다(실기
 * 크래시로 확인). 이 세대의 첫 [load] 호출에서 값을 한 번 재서
 * [totalCount] 에 굳혀 두고, 그 뒤 이 세대가 사는 동안엔 그 값만 쓴다 —
 * 그 사이 늘어난 진짜 최신 총량은 다음 [invalidate] 가 만드는 **다음
 * 세대**가 다시 재서 알아낸다(이 클래스 전체가 "재생성이 곧 더 많은
 * 데이터를 알게 되는 방법"이라는 전제 위에 있다).
 */
class PhotoFeedLocalPagingSource(
    private val feedKey: String,
    private val photoDao: PhotoDao,
    private val photoFeedDao: PhotoFeedDao,
) : PagingSource<Int, PhotoEntity>() {

    // 진단용 — 이 소스 인스턴스(=세대)를 로그에서 구분하기 위한 번호.
    private val generation = GENERATION_COUNTER.incrementAndGet()

    private val totalCountMutex = Mutex()
    private var cachedTotalCount: Int? = null

    /** 이 세대에서 처음 불렸을 때만 실제로 쿼리하고, 그 뒤로는 고정값을 재사용한다. */
    private suspend fun totalCount(): Int = cachedTotalCount ?: totalCountMutex.withLock {
        cachedTotalCount ?: photoFeedDao.countForFeed(feedKey).also { cachedTotalCount = it }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PhotoEntity> {
        // §17.21 — PREPEND 만 key 를 배타적 상한으로 해석한다. 클래스 문서 참고.
        val (offset, limit) = when (params) {
            is LoadParams.Prepend -> {
                val exclusiveEnd = params.key
                val start = maxOf(0, exclusiveEnd - params.loadSize)
                start to (exclusiveEnd - start)
            }
            else -> (params.key ?: 0) to params.loadSize
        }
        return try {
            val data = photoDao.page(feedKey, limit, offset)
            val totalCount = totalCount()
            val result = LoadResult.Page(
                data = data,
                // 항상 "이 페이지가 시작하는 절대 오프셋" 그 자체다 — PREPEND
                // 가 이 값을 배타적 상한으로 다시 해석하므로, loadSize 가
                // 달라도 항상 정확히 여기서 끝나는 구간을 읽게 된다.
                prevKey = if (offset == 0) null else offset,
                nextKey = if (data.size < limit) null else offset + data.size,
                itemsBefore = offset,
                itemsAfter = maxOf(0, totalCount - offset - data.size),
            )
            result
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    /**
     * Room 의 `LimitOffsetPagingSource` 와 같은 공식이다(`getClippedRefreshKey`,
     * 내부 API) — placeholder 를 켰으니 [PagingState.anchorPosition] 이
     * 곧 전체 목록에서의 절대 오프셋이라, 앵커를 중심으로 `initialLoadSize`
     * 절반만큼 앞으로 당긴 지점부터 다시 읽으면 앵커가 새 창 안에 그대로
     * 남는다.
     */
    override fun getRefreshKey(state: PagingState<Int, PhotoEntity>): Int? {
        val anchor = state.anchorPosition ?: return null
        return maxOf(0, anchor - state.config.initialLoadSize / 2)
    }

    private companion object {
        val GENERATION_COUNTER = AtomicInteger(0)
    }
}
