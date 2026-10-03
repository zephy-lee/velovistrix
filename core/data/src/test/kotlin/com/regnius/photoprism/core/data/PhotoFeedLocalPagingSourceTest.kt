package com.regnius.photoprism.core.data

import androidx.paging.PagingSource
import com.regnius.photoprism.core.data.local.dao.PhotoDao
import com.regnius.photoprism.core.data.local.dao.PhotoFeedDao
import com.regnius.photoprism.core.data.local.entity.PhotoEntity
import com.regnius.photoprism.core.data.local.entity.PhotoFeedEntryEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [PhotoFeedLocalPagingSource] 의 오프셋/키/placeholder 계산을 검증한다 —
 * §17.12 때 실기에서 터졌던 것과 같은 종류의 버그(다른 loadSize 로 다시
 * 불렸을 때 prevKey/nextKey 가 어긋나는 것)를 회귀 테스트로 잡아 둔다.
 */
class PhotoFeedLocalPagingSourceTest {

    private class FakePhotoDao(private val rows: List<PhotoEntity>) : PhotoDao {
        override suspend fun page(feedKey: String, limit: Int, offset: Int): List<PhotoEntity> =
            rows.drop(offset).take(limit)

        override suspend fun upsertAll(photos: List<PhotoEntity>) = error("unused")
        override suspend fun deleteAll() = error("unused")
        override suspend fun countAll(): Int = error("unused")
        override suspend fun deleteOrphans() = error("unused")
    }

    private class FakePhotoFeedDao(private val total: Int) : PhotoFeedDao {
        override suspend fun countForFeed(feedKey: String): Int = total

        override suspend fun clearFeeds(feedKeys: List<String>) = error("unused")
        override suspend fun insertEntries(entries: List<PhotoFeedEntryEntity>) = error("unused")
        override suspend fun maxSortIndex(feedKey: String): Int? = error("unused")
        override suspend fun uidsForFeed(feedKey: String): List<String> = error("unused")
        override suspend fun deleteAllEntries() = error("unused")
    }

    /** 매 호출마다 다른(늘어나는) 총량을 돌려준다 — 세대 중간에 RemoteMediator 가 계속 쓰는 상황 흉내. */
    private class GrowingFakePhotoFeedDao(private val totals: List<Int>) : PhotoFeedDao {
        private var callIndex = 0
        override suspend fun countForFeed(feedKey: String): Int =
            totals[callIndex.coerceAtMost(totals.size - 1)].also { callIndex++ }

        override suspend fun clearFeeds(feedKeys: List<String>) = error("unused")
        override suspend fun insertEntries(entries: List<PhotoFeedEntryEntity>) = error("unused")
        override suspend fun maxSortIndex(feedKey: String): Int? = error("unused")
        override suspend fun uidsForFeed(feedKey: String): List<String> = error("unused")
        override suspend fun deleteAllEntries() = error("unused")
    }

    private fun photo(uid: String) = PhotoEntity(
        uid = uid, title = uid, hash = uid, type = "image", takenAtLocal = null,
        favorite = false, width = 100, height = 100, cameraMake = null, cameraModel = null,
        lens = null, iso = null, fNumber = null, exposure = null, latitude = null,
        longitude = null, filesJson = "[]",
    )

    private fun source(total: Int) = PhotoFeedLocalPagingSource(
        "feed",
        FakePhotoDao((0 until total).map { photo("p$it") }),
        FakePhotoFeedDao(total),
    )

    @Test
    fun `꽉 찬 페이지는 받은 개수만큼 nextKey 를 전진시킨다`() = runTest {
        val result = source(total = 300).load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 120, placeholdersEnabled = true)
        ) as PagingSource.LoadResult.Page

        assertEquals(120, result.data.size)
        assertNull(result.prevKey)
        assertEquals(120, result.nextKey)
    }

    @Test
    fun `짧게 온 페이지는 로컬 소진으로 보고 nextKey 를 null 로 끝낸다`() {
        // merged=true 서버 응답처럼, 로컬 Room 에 남은 게 요청보다 적을 때의
        // 상황을 그대로 흉내낸다 — 이 null 이 RemoteMediator 의 APPEND 를
        // 트리거하는 신호다(§17.14).
        runTest {
            val result = source(total = 100).load(
                PagingSource.LoadParams.Append(key = 60, loadSize = 60, placeholdersEnabled = true)
            ) as PagingSource.LoadResult.Page

            assertEquals(40, result.data.size) // 100 - 60
            assertNull(result.nextKey)
            assertEquals(60, result.prevKey) // §17.21 — prevKey 는 항상 이 페이지의 시작 오프셋 자체
        }
    }

    @Test
    fun `APPEND 가 REFRESH 와 다른 loadSize 로 불려도 offset 계산이 어긋나지 않는다`() = runTest {
        // §17.12 버그 재현 시나리오: 첫 페이지는 initialLoadSize(120)로,
        // 이후 페이지는 pageSize(60)로 불린다 — prevKey/nextKey 는 항상
        // "이 호출의 offset/limit" 기준이어야지 다음 호출의 loadSize 를
        // 가정하면 안 된다.
        val src = source(total = 500)
        val first = src.load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 120, placeholdersEnabled = true)
        ) as PagingSource.LoadResult.Page
        assertEquals(120, first.nextKey)

        val second = src.load(
            PagingSource.LoadParams.Append(key = first.nextKey!!, loadSize = 60, placeholdersEnabled = true)
        ) as PagingSource.LoadResult.Page
        assertEquals(60, second.data.size)
        assertEquals(180, second.nextKey)
        assertEquals(120, second.prevKey) // §17.21 — prevKey 는 항상 이 페이지의 시작 오프셋 자체
    }

    @Test
    fun `PREPEND 의 key 는 배타적 상한이라 REFRESH 와 다른 loadSize 로 불려도 빈틈 없이 이어붙는다`() = runTest {
        // §17.21 회귀 테스트 — 이게 진짜 근본 원인이었다. REFRESH(loadSize
        // 120)로 읽힌 페이지의 prevKey 를, 나중에 pageSize(60)짜리 PREPEND
        // 가 그대로 소비할 때 두 페이지 사이에 빈틈/겹침이 생기면 안 된다.
        // 어긋나면 Paging 3 내부 절대 위치 계산이 꼬여 같은 사진이 그리드에
        // 두 번 나타나고 LazyVerticalGrid 가 "Key ... already used" 로
        // 죽는다(§17.13/§17.19 와 겉보기엔 같았던 진짜 원인).
        val src = source(total = 800)
        val refresh = src.load(
            PagingSource.LoadParams.Refresh(key = 200, loadSize = 120, placeholdersEnabled = true)
        ) as PagingSource.LoadResult.Page
        assertEquals(200, refresh.prevKey)

        val prepend = src.load(
            PagingSource.LoadParams.Prepend(key = refresh.prevKey!!, loadSize = 60, placeholdersEnabled = true)
        ) as PagingSource.LoadResult.Page

        // 빈틈도 겹침도 없이 정확히 refresh 페이지가 시작하는 지점에서 끝난다.
        assertEquals(200, prepend.itemsBefore + prepend.data.size)
        assertEquals(refresh.itemsBefore, prepend.itemsBefore + prepend.data.size)
    }

    @Test
    fun `itemsBefore·itemsAfter 는 로컬에 이미 캐싱된 진짜 총량을 반영한다`() = runTest {
        // §17.18 — 재시작 직후 initialLoadSize(120) 만 읽어도, 이미 800장이
        // 캐싱돼 있었다면 itemCount(=data+itemsBefore+itemsAfter)는 곧바로
        // 800이어야 한다. "120+" 부터 다시 시작하던 버그의 회귀 테스트.
        val result = source(total = 800).load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 120, placeholdersEnabled = true)
        ) as PagingSource.LoadResult.Page

        assertEquals(0, result.itemsBefore)
        assertEquals(680, result.itemsAfter) // 800 - 0 - 120
        assertEquals(800, result.itemsBefore + result.data.size + result.itemsAfter)
    }

    @Test
    fun `중간 오프셋에서 읽으면 itemsBefore 가 그 오프셋과 같다`() = runTest {
        val result = source(total = 800).load(
            PagingSource.LoadParams.Append(key = 300, loadSize = 60, placeholdersEnabled = true)
        ) as PagingSource.LoadResult.Page

        assertEquals(300, result.itemsBefore)
        assertEquals(440, result.itemsAfter) // 800 - 300 - 60
    }

    @Test
    fun `한 세대 안에서는 나중에 더 늘어난 총량이 반영되지 않는다`() = runTest {
        // §17.19 회귀 테스트. countForFeed 를 매 load() 마다 새로 쿼리하면,
        // 그 사이 RemoteMediator 가 백그라운드에서 새 행을 써 총량이
        // 늘어날 때 같은 세대의 서로 다른 페이지가 서로 다른 총량을
        // 전제로 itemsBefore/itemsAfter 를 계산하게 된다 — Paging 3 는
        // 한 세대의 모든 페이지가 같은 총량에 합의하고 있다고 가정하므로,
        // 이게 어긋나면 내부 위치 계산이 꼬여 같은 사진이 두 자리에 나타나고
        // LazyVerticalGrid 가 "Key ... was already used" 로 죽는다(실기
        // 크래시). 첫 호출에서 총량을 굳혀 두 번째 호출에도 그대로 쓰는지 확인한다.
        val src = PhotoFeedLocalPagingSource(
            "feed",
            FakePhotoDao((0 until 2000).map { photo("p$it") }),
            GrowingFakePhotoFeedDao(listOf(500, 900)),
        )
        val first = src.load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 120, placeholdersEnabled = true)
        ) as PagingSource.LoadResult.Page
        val firstTotal = first.itemsBefore + first.data.size + first.itemsAfter
        assertEquals(500, firstTotal)

        val second = src.load(
            PagingSource.LoadParams.Append(key = 120, loadSize = 60, placeholdersEnabled = true)
        ) as PagingSource.LoadResult.Page
        val secondTotal = second.itemsBefore + second.data.size + second.itemsAfter
        assertEquals(firstTotal, secondTotal)
    }

    @Test
    fun `getRefreshKey 는 앵커를 initialLoadSize 절반만큼 앞으로 당긴 오프셋이다`() {
        // Room 의 LimitOffsetPagingSource(getClippedRefreshKey)와 같은 공식 —
        // placeholder 를 켰으니 anchorPosition 이 곧 전체 목록 절대 오프셋이다.
        val pagingState = androidx.paging.PagingState<Int, PhotoEntity>(
            pages = emptyList(),
            anchorPosition = 200,
            config = androidx.paging.PagingConfig(pageSize = 60, initialLoadSize = 120),
            leadingPlaceholderCount = 0,
        )
        val refreshKey = source(total = 800).getRefreshKey(pagingState)
        assertEquals(140, refreshKey) // maxOf(0, 200 - 60)
    }

    @Test
    fun `앵커가 시작 부근이면 getRefreshKey 는 0 아래로 내려가지 않는다`() {
        val pagingState = androidx.paging.PagingState<Int, PhotoEntity>(
            pages = emptyList(),
            anchorPosition = 10,
            config = androidx.paging.PagingConfig(pageSize = 60, initialLoadSize = 120),
            leadingPlaceholderCount = 0,
        )
        val refreshKey = source(total = 800).getRefreshKey(pagingState)
        assertEquals(0, refreshKey) // maxOf(0, 10 - 60)
    }
}
