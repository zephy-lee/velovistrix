package com.regnius.photoprism.core.data.local

import com.regnius.photoprism.core.data.local.entity.CatalogItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 라벨·인물 캐시 (Phase 3.5~3.6). */
class CatalogDaoTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = createTestDatabase() }
    @After fun tearDown() { db.close() }

    private fun item(uid: String, kind: String, order: Int) = CatalogItemEntity(
        kind = kind,
        uid = uid,
        name = "name-$uid",
        slug = uid,
        photoCount = 1,
        coverHash = null,
        orderIndex = order,
    )

    @Test
    fun `종류별로 격리된다`() {
        runTest {
            db.catalogDao().replaceAll("label", listOf(item("cat", "label", 0)))

            // 사람 목록을 받아오는 것이 라벨 캐시를 지우면, 메뉴를 왕복할
            // 때마다 둘 중 하나는 항상 네트워크를 기다린다.
            db.catalogDao().replaceAll("person", listOf(item("jane", "person", 0)))

            assertEquals(listOf("cat"), db.catalogDao().getAll("label").map { it.uid })
            assertEquals(listOf("jane"), db.catalogDao().getAll("person").map { it.uid })
        }
    }

    @Test
    fun `같은 UID 라도 종류가 다르면 서로를 덮지 않는다`() = runTest {
        // 라벨 UID 와 인물 UID 가 겹칠 일은 없어 보이지만, 그걸 가정하면
        // 서버가 UID 체계를 바꾼 날 한쪽이 조용히 사라진다. 복합 PK 가 그걸 막는다.
        db.catalogDao().insertAll(listOf(item("same", "label", 0), item("same", "person", 0)))

        assertEquals(1, db.catalogDao().getAll("label").size)
        assertEquals(1, db.catalogDao().getAll("person").size)
    }

    @Test
    fun `orderIndex 순으로 돌려준다`() = runTest {
        db.catalogDao().insertAll(
            listOf(item("c", "label", 2), item("a", "label", 0), item("b", "label", 1)),
        )

        assertEquals(listOf("a", "b", "c"), db.catalogDao().getAll("label").map { it.uid })
    }

    @Test
    fun `로그아웃은 둘 다 비운다`() = runTest {
        db.catalogDao().replaceAll("label", listOf(item("cat", "label", 0)))
        db.catalogDao().replaceAll("person", listOf(item("jane", "person", 0)))

        db.catalogDao().clearAll()

        assertEquals(0, db.catalogDao().getAll("label").size)
        assertEquals(0, db.catalogDao().getAll("person").size)
    }
}
