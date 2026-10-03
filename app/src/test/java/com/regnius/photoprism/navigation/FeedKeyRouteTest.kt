package com.regnius.photoprism.navigation

import com.regnius.photoprism.core.data.FeedKey
import com.regnius.photoprism.core.network.PhotoQuery
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 그리드에서 넘긴 [FeedKey] 가 뷰어·슬라이드쇼에 **그대로** 도착해야 한다. §17.46
 *
 * 원래 `requireLocation` 이 라우트에서 빠져 있었다. 위치 필터를 건 검색에서
 * 사진을 누르면 뷰어가 *걸러지지 않은* 목록을 보게 되어, 탭한 인덱스가 다른
 * 사진을 가리켰다. 왕복이 항등이어야 그리드와 뷰어가 같은 목록을 본다.
 */
class FeedKeyRouteTest {

    @Test
    fun `viewer route keeps the location filter`() {
        val key = FeedKey.search("berlin", requireLocation = true)
        assertEquals(key, key.toViewerRoute(startIndex = 0).toFeedKey())
    }

    @Test
    fun `slideshow route keeps the location filter`() {
        val key = FeedKey.search("berlin", requireLocation = false)
        assertEquals(key, key.toSlideshowRoute(startIndex = 0).toFeedKey())
    }

    /**
     * 필드가 하나라도 새로 늘면 여기서 걸리라고 **모든 조합**을 돈다.
     * 화면에서 실제로 쓰이는 키들이다.
     */
    @Test
    fun `every feed key survives the round trip`() {
        val keys = listOf(
            FeedKey.timeline(),
            FeedKey.favorites(),
            FeedKey.albumImages("abc123"),
            FeedKey.albumVideos("abc123"),
            FeedKey.search("beach ${PhotoQuery.IMAGES}"),
            FeedKey.search("beach", requireLocation = true),
            FeedKey.search("beach", requireLocation = false),
            FeedKey.search("beach", requireLocation = null),
        )
        keys.forEach { key ->
            assertEquals(key, key.toViewerRoute(7).toFeedKey())
            assertEquals(key, key.toSlideshowRoute(7).toFeedKey())
        }
    }

    @Test
    fun `start index is carried separately from the key`() {
        val route = FeedKey.timeline().toViewerRoute(startIndex = 42)
        assertEquals(42, route.startIndex)
    }
}
