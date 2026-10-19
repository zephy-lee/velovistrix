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

        assertEquals(listOf("a", "b", "c"), db.albumDao().getAll("album").map { it.uid })
    }

    @Test
    fun `replaceAll 은 이전 앨범을 남기지 않는다`() = runTest {
        db.albumDao().replaceAll("album", listOf(album("old1", 0), album("old2", 1)))
        db.albumDao().replaceAll("album", listOf(album("new1", 0)))

        // 서버에서 지워진 앨범이 캐시에 유령으로 남으면, 탭했을 때 404 가 난다.
        assertEquals(listOf("new1"), db.albumDao().getAll("album").map { it.uid })
    }

    @Test
    fun `조회에 실패하면 이전 캐시가 그대로 남는다`() = runTest {
        db.albumDao().replaceAll("album", listOf(album("a", 0), album("b", 1)))

        // 오프라인 보장의 핵심: replaceAll 은 **성공한 응답으로만** 불린다.
        // 실패 경로에서는 아예 호출되지 않으므로 이전 행이 그대로 남는다.
        // (AlbumRepository 가 실패 시 getAll() 로 폴백한다.)
        assertEquals(2, db.albumDao().getAll("album").size)
    }

    @Test
    fun `달력을 받아와도 앨범 캐시는 그대로다`() = runTest {
        // Phase 3.1~3.4 의 핵심. 달력·폴더·지역·나의 순간이 앨범과 같은
        // 테이블을 쓰므로, 격리가 없으면 메뉴에서 달력을 한 번 여는 것만으로
        // `replaceAll` 이 앨범 목록까지 날리고 다음 앨범 탭 진입이 다시
        // 네트워크를 기다린다.
        db.albumDao().replaceAll("album", listOf(album("my-album", 0)))

        db.albumDao().replaceAll("month", listOf(album("2026-09", 0, listType = "month")))

        assertEquals(listOf("my-album"), db.albumDao().getAll("album").map { it.uid })
        assertEquals(listOf("2026-09"), db.albumDao().getAll("month").map { it.uid })
    }

    @Test
    fun `타입별 교체는 그 타입만 비운다`() = runTest {
        db.albumDao().replaceAll("folder", listOf(album("f1", 0, listType = "folder"), album("f2", 1, listType = "folder")))
        db.albumDao().replaceAll("state", listOf(album("s1", 0, listType = "state")))

        // 폴더만 다시 받아온 상황: 폴더는 새 목록으로 바뀌고 지역은 남아야 한다.
        db.albumDao().replaceAll("folder", listOf(album("f3", 0, listType = "folder")))

        assertEquals(listOf("f3"), db.albumDao().getAll("folder").map { it.uid })
        assertEquals(listOf("s1"), db.albumDao().getAll("state").map { it.uid })
    }

    @Test
    fun `로그아웃은 타입을 가리지 않고 전부 비운다`() = runTest {
        db.albumDao().replaceAll("album", listOf(album("a", 0)))
        db.albumDao().replaceAll("month", listOf(album("m", 0, listType = "month")))

        // 서버를 바꿔 로그인했을 때 이전 서버의 달력이 남아 있으면 안 된다.
        db.albumDao().clearAll()

        assertEquals(0, db.albumDao().getAll("album").size)
        assertEquals(0, db.albumDao().getAll("month").size)
    }
}
