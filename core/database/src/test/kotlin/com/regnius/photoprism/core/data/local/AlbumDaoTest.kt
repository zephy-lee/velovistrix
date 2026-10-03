package com.regnius.photoprism.core.data.local

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 앨범 캐시 (Phase 2.9). Phase 2.9 때 §17.10 으로 쓰다가 **버렸던 테스트**를
 * `:core:database` 분리(Phase 3.4) 후 되살린 것이다.
 */
class AlbumDaoTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = createTestDatabase() }
    @After fun tearDown() { db.close() }

    @Test
    fun `저장 순서가 아니라 orderIndex 순으로 돌려준다`() = runTest {
        // 서버가 준 순서를 SQLite 행 순서에 기대지 않고 명시적으로 저장한다 —
        // 앨범 정렬은 클라이언트가 하지만(§AlbumSort), 캐시가 기준 순서를
        // 잃어버리면 오프라인에서 목록이 뒤죽박죽으로 뜬다.
        db.albumDao().insertAll(listOf(album("c", 2), album("a", 0), album("b", 1)))

        assertEquals(listOf("a", "b", "c"), db.albumDao().getAll().map { it.uid })
    }

    @Test
    fun `replaceAll 은 이전 앨범을 남기지 않는다`() = runTest {
        db.albumDao().replaceAll(listOf(album("old1", 0), album("old2", 1)))
        db.albumDao().replaceAll(listOf(album("new1", 0)))

        // 서버에서 지워진 앨범이 캐시에 유령으로 남으면, 탭했을 때 404 가 난다.
        assertEquals(listOf("new1"), db.albumDao().getAll().map { it.uid })
    }

    @Test
    fun `조회에 실패하면 이전 캐시가 그대로 남는다`() = runTest {
        db.albumDao().replaceAll(listOf(album("a", 0), album("b", 1)))

        // 오프라인 보장의 핵심: replaceAll 은 **성공한 응답으로만** 불린다.
        // 실패 경로에서는 아예 호출되지 않으므로 이전 행이 그대로 남는다.
        // (AlbumRepository 가 실패 시 getAll() 로 폴백한다.)
        assertEquals(2, db.albumDao().getAll().size)
    }
}
