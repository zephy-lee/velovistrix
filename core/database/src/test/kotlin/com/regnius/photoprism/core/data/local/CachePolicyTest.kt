package com.regnius.photoprism.core.data.local

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 오프라인 캐시 저장공간 상한 (Phase 2.9 [CachePolicy]).
 *
 * Phase 2.9 때 §17.10 으로 쓰다가 **버렸던 테스트**를 `:core:database`
 * 분리(Phase 3.4) 후 되살린 것이다. 여기서 검증하는 두 가지는 실기에서
 * 확인하기가 특히 어렵다 — 상한을 넘기려면 피드를 수십 개 열어야 한다.
 */
class CachePolicyTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = createTestDatabase() }
    @After fun tearDown() { db.close() }

    @Test
    fun `피드 수가 상한을 넘으면 가장 오래된 것부터 지운다`() = runTest {
        // 상한 + 3개. lastRefreshedAt 이 작을수록 오래된 것.
        val total = CachePolicy.MAX_CACHED_FEEDS + 3
        repeat(total) { i -> db.seedFeed("feed-$i", count = 1, lastRefreshedAt = i.toLong()) }

        CachePolicy.enforceCaps(db)

        assertEquals(CachePolicy.MAX_CACHED_FEEDS, db.feedRemoteKeyDao().feedCount())
        // 가장 오래된 셋(0,1,2)이 사라지고 나머지는 남는다.
        assertFalse(db.feedRemoteKeyDao().get("feed-0") != null)
        assertFalse(db.feedRemoteKeyDao().get("feed-2") != null)
        assertTrue(db.feedRemoteKeyDao().get("feed-3") != null)
    }

    @Test
    fun `방금 갱신한 피드는 지워지지 않는다`() = runTest {
        val total = CachePolicy.MAX_CACHED_FEEDS + 5
        repeat(total) { i -> db.seedFeed("feed-$i", count = 1, lastRefreshedAt = i.toLong()) }
        // 가장 오래됐던 피드를 방금 갱신한 것으로 만든다.
        db.feedRemoteKeyDao().upsert(feedKey("feed-0", lastRefreshedAt = Long.MAX_VALUE))

        CachePolicy.enforceCaps(db)

        // 정리는 항상 REFRESH 성공 트랜잭션 **끝에서** 도는데, 그 피드는
        // 방금 lastRefreshedAt 을 갱신했으므로 자기 자신을 지우는 일이
        // 구조적으로 불가능해야 한다.
        assertTrue(db.feedRemoteKeyDao().get("feed-0") != null)
    }

    @Test
    fun `피드를 지우면 그 피드만 가리키던 사진도 같이 사라진다`() = runTest {
        val total = CachePolicy.MAX_CACHED_FEEDS + 1
        repeat(total) { i -> db.seedFeed("feed-$i", count = 2, lastRefreshedAt = i.toLong()) }
        assertEquals(total * 2, db.photoDao().countAll())

        CachePolicy.enforceCaps(db)

        // 고아 행이 남으면 "목록은 없는데 용량만 먹는" 사진이 쌓인다.
        assertEquals(CachePolicy.MAX_CACHED_FEEDS * 2, db.photoDao().countAll())
    }

    @Test
    fun `여러 피드가 공유하는 사진은 한쪽이 지워져도 남는다`() = runTest {
        // photos 는 uid 로 전역 중복 제거되므로, 타임라인과 앨범이 같은 사진을
        // 가리키는 게 정상이다. 한쪽 피드를 정리하면서 이걸 지우면 남은 피드에
        // 구멍이 뚫린다.
        db.photoDao().upsertAll(listOf(photo("shared")))
        db.photoFeedDao().insertEntries(entries("keep", listOf("shared")))
        db.photoFeedDao().insertEntries(entries("drop", listOf("shared")))
        db.feedRemoteKeyDao().upsert(feedKey("keep", lastRefreshedAt = 100))

        db.photoFeedDao().clearFeeds(listOf("drop"))
        db.photoDao().deleteOrphans()

        assertEquals(1, db.photoDao().countAll())
    }

    @Test
    fun `상한 안이면 아무것도 지우지 않는다`() = runTest {
        repeat(3) { i -> db.seedFeed("feed-$i", count = 2, lastRefreshedAt = i.toLong()) }

        CachePolicy.enforceCaps(db)

        assertEquals(3, db.feedRemoteKeyDao().feedCount())
        assertEquals(6, db.photoDao().countAll())
    }
}
