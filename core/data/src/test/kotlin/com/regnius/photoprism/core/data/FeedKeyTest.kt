package com.regnius.photoprism.core.data

import com.regnius.photoprism.core.network.PhotoQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedKeyTest {

    @Test
    fun `타임라인은 범위도 필터도 없다`() {
        val key = FeedKey.timeline()
        assertNull(key.albumUid)
        assertNull(key.query)
        assertEquals("newest", key.order)
    }

    @Test
    fun `앨범 이미지 탭은 vector 와 document 까지 포함한다`() {
        // Phase 0.3 실측에서 드러난 타입들. 빠지면 앨범 장수와 탭에 보이는
        // 장수가 어긋나 사용자가 사진이 사라졌다고 느낀다.
        val key = FeedKey.albumImages("a1")
        assertEquals(PhotoQuery.IMAGES, key.query)
        assertTrue(PhotoQuery.IMAGES.contains("vector"))
        assertTrue(PhotoQuery.IMAGES.contains("document"))
    }

    @Test
    fun `앨범 안에서는 오래된 순으로 본다`() {
        // 앨범은 보통 하나의 사건이라 시간 순서대로 보는 게 자연스럽다.
        // 반면 전체 타임라인은 최신이 위에 와야 한다.
        assertEquals("oldest", FeedKey.albumImages("a1").order)
        assertEquals("newest", FeedKey.timeline().order)
    }

    @Test
    fun `이미지 탭과 동영상 탭은 서로 다른 키다`() {
        // 같은 키면 캐시가 공유돼 탭을 바꿔도 같은 목록이 나온다.
        assertNotEquals(FeedKey.albumImages("a1"), FeedKey.albumVideos("a1"))
    }

    @Test
    fun `앨범이 다르면 키도 다르다`() {
        assertNotEquals(FeedKey.albumImages("a1"), FeedKey.albumImages("a2"))
    }

    @Test
    fun `즐겨찾기 필터`() {
        assertEquals(PhotoQuery.FAVORITES, FeedKey.favorites().query)
    }

    @Test
    fun `cacheKey 는 requireLocation 차이를 무시한다`() {
        // requireLocation 은 서버에 안 보내는 클라이언트 후처리 필터라, 셋 다
        // 서버 응답이 같은데 Room 에 세 번 저장/세 번 REFRESH 되면 안 된다.
        val base = FeedKey.search("berlin")
        val withLocation = base.copy(requireLocation = true)
        val withoutLocation = base.copy(requireLocation = false)

        assertEquals(base.cacheKey(), withLocation.cacheKey())
        assertEquals(base.cacheKey(), withoutLocation.cacheKey())
    }

    @Test
    fun `cacheKey 는 albumUid, query, order 가 다르면 다르다`() {
        assertNotEquals(FeedKey.timeline().cacheKey(), FeedKey.favorites().cacheKey())
        assertNotEquals(FeedKey.albumImages("a1").cacheKey(), FeedKey.albumVideos("a1").cacheKey())
        assertNotEquals(FeedKey.albumImages("a1").cacheKey(), FeedKey.albumImages("a2").cacheKey())
    }
}
