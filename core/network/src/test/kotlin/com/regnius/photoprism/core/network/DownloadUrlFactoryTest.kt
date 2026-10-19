package com.regnius.photoprism.core.network

import com.regnius.photoprism.core.network.url.DownloadUrlFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 원본 다운로드 URL (Phase 4.4~4.5). */
class DownloadUrlFactoryTest {

    private val factory = DownloadUrlFactory(
        "https://photos.example.com/api/v1/".toHttpUrl(),
        downloadToken = "dt-123",
    )

    @Test
    fun `해시와 다운로드 토큰으로 만든다`() {
        // 썸네일은 토큰이 **경로**에 들어가지만(ThumbUrlFactory) 다운로드는
        // 쿼리다 — 둘을 섞으면 404 가 온다.
        assertEquals(
            "https://photos.example.com/api/v1/dl/abc123?t=dt-123",
            factory.original("abc123"),
        )
    }

    @Test
    fun `서브패스 서버에서도 api 경로를 유지한다`() {
        val sub = DownloadUrlFactory(
            "https://example.com/photoprism/api/v1/".toHttpUrl(),
            downloadToken = "dt-9",
        )

        assertTrue(sub.original("h").startsWith("https://example.com/photoprism/api/v1/dl/h"))
    }
}
