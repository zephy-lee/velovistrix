package com.regnius.photoprism.core.network

import com.regnius.photoprism.core.model.ThumbSize
import com.regnius.photoprism.core.network.url.ServerUrl
import com.regnius.photoprism.core.network.url.ThumbUrlFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThumbUrlFactoryTest {

    private val factory = ThumbUrlFactory(
        apiBase = ServerUrl.apiBase(ServerUrl.normalize("https://example.com")!!),
        previewToken = "abc123",
    )

    @Test
    fun `썸네일 URL 에 previewToken 이 경로로 들어간다`() {
        assertEquals(
            "https://example.com/api/v1/t/deadbeef/abc123/tile_224",
            factory.thumb("deadbeef", ThumbSize.GRID_COMPACT),
        )
    }

    @Test
    fun `서브패스 서버에서도 경로가 어긋나지 않는다`() {
        val sub = ThumbUrlFactory(
            ServerUrl.apiBase(ServerUrl.normalize("https://example.com/photos")!!),
            "tok",
        )
        assertEquals(
            "https://example.com/photos/api/v1/t/hash/tok/fit_1920",
            sub.thumb("hash", ThumbSize.VIEWER_FULL),
        )
    }

    @Test
    fun `앨범 커버 URL`() {
        assertEquals(
            "https://example.com/api/v1/albums/aq1/t/abc123/tile_500",
            factory.albumCover("aq1"),
        )
    }

    @Test
    fun `비디오는 avc 로 트랜스코딩을 요청한다`() {
        assertEquals(
            "https://example.com/api/v1/videos/vhash/abc123/avc",
            factory.video("vhash"),
        )
    }

    @Test
    fun `디스크 캐시 키에는 토큰이 들어가지 않는다`() {
        // §4.2 — 서버가 previewToken 을 회전하면 URL 이 전부 바뀐다.
        // 캐시 키가 URL 이면 그 순간 디스크 캐시가 통째로 날아간다.
        val key = ThumbUrlFactory.diskCacheKey("deadbeef", ThumbSize.GRID_COMPACT)
        assertEquals("deadbeef_tile_224", key)
        assert(!key.contains("abc123"))
    }

    @Test
    fun `사이즈가 다르면 캐시 키가 다르다`() {
        assertNotEquals(
            ThumbUrlFactory.diskCacheKey("h", ThumbSize.GRID_COMPACT),
            ThumbUrlFactory.diskCacheKey("h", ThumbSize.VIEWER_FULL),
        )
    }
}
