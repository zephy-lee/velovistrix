package com.regnius.photoprism.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * DTO 계약 테스트.
 *
 * PhotoPrism 은 릴리스마다 응답 필드를 늘린다. 여기서 확인하려는 것은
 * "우리가 아는 필드를 잘 읽는가" 뿐 아니라 **"모르는 필드가 와도 죽지 않는가"** 다.
 * 후자가 깨지면 서버를 업데이트한 사용자의 앱이 통째로 못 쓰게 된다.
 */
class PhotoPrismApiContractTest {

    private lateinit var server: MockWebServer
    private lateinit var api: PhotoPrismApi

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PhotoPrismApi::class.java)
    }

    @After fun tearDown() { server.shutdown() }

    @Test
    fun `세션 응답에서 access_token 을 읽는다`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "access_token": "at-123",
                  "token_type": "Bearer",
                  "expires_in": 3600,
                  "id": "sess-1",
                  "config": { "version": "260601-abc", "previewToken": "pt-1", "downloadToken": "dt-1" }
                }
                """.trimIndent()
            )
        )

        val response = api.createSession(
            com.regnius.photoprism.core.network.dto.SessionRequestDto("demo", "pw")
        )

        assertEquals("at-123", response.bearerToken)
        assertEquals("pt-1", response.config?.previewToken)
        assertEquals("dt-1", response.config?.downloadToken)
    }

    @Test
    fun `모르는 필드가 섞여 있어도 파싱이 깨지지 않는다`() = runTest {
        // 서버 업데이트로 새 필드가 추가된 상황을 재현한다.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "access_token": "at-9",
                  "brandNewFieldFromFutureRelease": { "nested": [1, 2, 3] },
                  "config": { "version": "260728-x", "previewToken": "p", "somethingElse": true }
                }
                """.trimIndent()
            )
        )

        val response = api.createSession(
            com.regnius.photoprism.core.network.dto.SessionRequestDto("demo", "pw")
        )
        assertEquals("at-9", response.bearerToken)
    }

    @Test
    fun `사진 검색 응답의 Files 배열을 읽는다`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{
                  "UID": "pq1", "Title": "Beach", "Hash": "h1", "Type": "image",
                  "TakenAtLocal": "2026-06-01T10:00:00Z", "Favorite": true,
                  "Width": 4000, "Height": 3000,
                  "CameraMake": "Apple", "CameraModel": "iPhone 15",
                  "Iso": 100, "FNumber": 1.8, "Exposure": "1/240",
                  "Files": [
                    {"UID":"f1","Hash":"h1","Name":"a.jpg","Primary":true,"Video":false,"Mime":"image/jpeg","Width":4000,"Height":3000,"Size":123456},
                    {"UID":"f2","Hash":"h2","Name":"a.dng","Primary":false,"Video":false,"Mime":"image/dng","Size":900000}
                  ]
                }]
                """.trimIndent()
            )
        )

        val photos = api.getPhotos(count = 10)

        assertEquals(1, photos.size)
        // merged=true 의 효과 — RAW+JPEG 가 한 항목의 Files 배열로 온다 (§4.3).
        assertEquals(2, photos[0].files.size)
        assertEquals("pq1", photos[0].uid)
        assertTrue(photos[0].favorite)
        assertEquals(1.8f, photos[0].fNumber)
    }

    @Test
    fun `필드가 통째로 빠져도 기본값으로 넘어간다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"UID":"pq2"}]"""))

        val photos = api.getPhotos(count = 1)

        assertEquals("pq2", photos[0].uid)
        assertEquals(0, photos[0].width)
        assertTrue(photos[0].files.isEmpty())
    }

    @Test
    fun `앨범 목록을 읽는다`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"UID":"aq1","Title":"2026 여름","Type":"album","PhotoCount":42,"Favorite":false}]"""
            )
        )

        val albums = api.getAlbums(count = 10)

        assertEquals("aq1", albums[0].uid)
        assertEquals(42, albums[0].photoCount)
    }

    @Test
    fun `사진 검색이 merged 와 필터를 쿼리로 보낸다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        api.getPhotos(count = 60, offset = 60, scope = "aq1", query = PhotoQuery.VIDEOS)

        val request = server.takeRequest()
        val url = request.requestUrl!!
        assertEquals("60", url.queryParameter("count"))
        assertEquals("60", url.queryParameter("offset"))
        assertEquals("aq1", url.queryParameter("s"))
        assertEquals("type:video", url.queryParameter("q"))
        assertEquals("true", url.queryParameter("merged"))
    }

    @Test
    fun `config 응답에서 두 종류 토큰을 구분해 읽는다`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"version":"260601-a7d","name":"PhotoPrism","mode":"user","previewToken":"pv","downloadToken":"dl"}"""
            )
        )

        val config = api.getConfig()

        // §4.2 — 이 둘을 헷갈리면 "썸네일만 401" 같은 증상이 난다.
        assertEquals("pv", config.previewToken)
        assertEquals("dl", config.downloadToken)
        assertEquals("260601-a7d", config.version)
    }
}
