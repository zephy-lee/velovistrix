package com.regnius.photoprism.core.network

import com.regnius.photoprism.fake.FakeSessionSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun clientWith(source: FakeSessionSource) = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(source))
        .build()

    @Test
    fun `일반 API 요청에 Bearer 헤더를 붙인다`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val client = clientWith(FakeSessionSource(FakeSessionSource.session("tok-abc")))

        client.newCall(Request.Builder().url(server.url("/api/v1/albums")).build()).execute().close()

        assertEquals("Bearer tok-abc", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `세션이 없으면 헤더를 붙이지 않는다`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = clientWith(FakeSessionSource(initial = null))

        client.newCall(Request.Builder().url(server.url("/api/v1/config")).build()).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `썸네일 요청에는 헤더를 붙이지 않는다`() {
        // §4.2 — 썸네일 URL 은 경로에 previewToken 이 들어있어 인증이 필요 없다.
        // 헤더를 붙이지 않아야 이 요청이 무상태 GET 으로 남고, 캐시·프리페치가
        // 인증 상태에 의존하지 않는다.
        server.enqueue(MockResponse().setResponseCode(200).setBody("img"))
        val client = clientWith(FakeSessionSource(FakeSessionSource.session()))

        client.newCall(
            Request.Builder().url(server.url("/api/v1/t/deadbeef/preview-1/tile_224")).build()
        ).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `비디오 스트림 요청에도 헤더를 붙이지 않는다`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("video"))
        val client = clientWith(FakeSessionSource(FakeSessionSource.session()))

        client.newCall(
            Request.Builder().url(server.url("/api/v1/videos/vhash/preview-1/avc")).build()
        ).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `서브패스 서버에서도 썸네일 경로를 알아본다`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("img"))
        val client = clientWith(FakeSessionSource(FakeSessionSource.session()))

        client.newCall(
            Request.Builder().url(server.url("/photos/api/v1/t/hash/preview-1/tile_224")).build()
        ).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }
}
