package com.regnius.photoprism.core.network

import com.regnius.photoprism.core.model.ThumbSize
import com.regnius.photoprism.core.network.url.ThumbUrlFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 썸네일 URL 을 되읽는 부분(§17.36).
 *
 * `ThumbSizeFallbackInterceptor` 가 "같은 사진의 다른 크기" 를 만들 때 쓰는
 * 유일한 실마리가 URL 이다. 여기가 틀리면 대체가 조용히 아무 일도 안 하거나,
 * 더 나쁘게는 엉뚱한 주소를 만든다.
 */
class ThumbUrlParseTest {

    private val factory = ThumbUrlFactory(
        apiBase = "http://192.168.0.10:2342/photo/api/v1/".toHttpUrl(),
        previewToken = "kfen5gfr",
    )

    @Test
    fun `직접 만든 URL 을 그대로 되읽는다`() {
        val url = factory.thumb("abc123", ThumbSize.GRID_COMPACT)
        assertEquals("abc123" to ThumbSize.GRID_COMPACT, ThumbUrlFactory.parseThumb(url))
    }

    @Test
    fun `서브패스가 있어도 읽는다`() {
        // 리버스 프록시 뒤 서브패스(/photo)가 붙은 실제 사용자 서버 형태.
        val url = "http://192.168.0.10:2342/photo/api/v1/t/deadbeef/kfen5gfr/tile_500"
        assertEquals("deadbeef" to ThumbSize.GRID_DENSE, ThumbUrlFactory.parseThumb(url))
    }

    @Test
    fun `크기만 바꾼 URL 을 만든다`() {
        val original = factory.thumb("abc123", ThumbSize.GRID_TINY)
        val swapped = ThumbUrlFactory.withSize(original, ThumbSize.GRID_DENSE)
        assertEquals(factory.thumb("abc123", ThumbSize.GRID_DENSE), swapped)
        // 되읽으면 바꾼 크기가 나와야 한다 — 왕복이 맞아야 대체가 성립한다.
        assertEquals("abc123" to ThumbSize.GRID_DENSE, ThumbUrlFactory.parseThumb(swapped))
    }

    @Test
    fun `썸네일이 아닌 주소는 건드리지 않는다`() {
        assertNull(ThumbUrlFactory.parseThumb("http://x/photo/api/v1/photos?count=60"))
        assertNull(ThumbUrlFactory.parseThumb("http://x/photo/api/v1/albums/a1/t/kfen5gfr/tile_500"))
    }

    @Test
    fun `모르는 크기 이름은 거절한다`() {
        assertNull(ThumbUrlFactory.parseThumb("http://x/api/v1/t/abc/tok/tile_9999"))
    }
}
