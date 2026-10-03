package com.regnius.photoprism.core.network

import com.regnius.photoprism.core.network.url.ServerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 셀프호스팅 사용자가 주소를 입력하는 방식은 제각각이다.
 * 여기서 한 칸이라도 어긋나면 이후 모든 요청이 실패하므로 규칙을 고정한다.
 */
class ServerUrlTest {

    private fun urls(input: String) = ServerUrl.candidates(input).map { it.toString() }

    // ── 스킴 선택 ────────────────────────────────────────────────────────

    @Test
    fun `사설망 IP 와 기본 포트는 평문을 먼저 시도한다`() {
        // 실제 사용자 서버. 예전에는 https 를 강제로 붙여 TLS 오류로 막혔다.
        val candidates = urls("192.168.0.10:2342")
        assertEquals("http://192.168.0.10:2342/", candidates.first())
        assertTrue("https 후보도 남아 있어야 한다", candidates.any { it.startsWith("https://") })
    }

    @Test
    fun `사설망 대역들을 알아본다`() {
        listOf(
            "10.0.0.5:2342", "172.16.3.4:2342", "192.168.1.1:2342",
            "127.0.0.1:2342", "localhost:2342", "nas.local:2342",
        ).forEach { input ->
            assertTrue("$input 은 평문 우선이어야 한다", urls(input).first().startsWith("http://"))
        }
    }

    @Test
    fun `PhotoPrism 기본 포트면 공인 주소라도 평문을 먼저 본다`() {
        // 2342 를 그대로 노출한 서버가 TLS 를 붙였을 확률은 낮다.
        assertTrue(urls("photos.example.com:2342").first().startsWith("http://"))
    }

    @Test
    fun `일반 도메인은 https 를 먼저 시도한다`() {
        val candidates = urls("photos.example.com")
        assertEquals("https://photos.example.com/", candidates.first())
        assertTrue(candidates.any { it.startsWith("http://") })
    }

    @Test
    fun `스킴을 직접 적으면 그것만 쓴다`() {
        // 사용자가 https 를 명시했는데 몰래 http 로 떨어뜨리면 안 된다.
        assertTrue(urls("https://photos.example.com").all { it.startsWith("https://") })
        assertTrue(urls("http://192.168.0.10:2342").all { it.startsWith("http://") })
    }

    // ── 경로 처리 ────────────────────────────────────────────────────────

    @Test
    fun `브라우저에서 복사한 웹 UI 경로도 결국 루트에 닿는다`() {
        // 사용자가 주소창을 그대로 붙여넣는 흔한 경우. 이걸 base 로 쓰면
        // /photo/library/api/v1/config 를 때려서 404 가 난다.
        //
        // 입력한 경로를 먼저 시도하는 것은 진짜 서브패스 설치를 위해 양보할 수
        // 없다. 대신 웹 UI 라우트를 알아보고 **바로 다음 후보**로 루트를 둬서,
        // 잘못 붙여넣은 사용자는 왕복 한 번만 더 하면 접속된다.
        val candidates = urls("192.168.0.10:2342/photo/library")
        assertEquals("http://192.168.0.10:2342/photo/library/", candidates.first())
        assertEquals("http://192.168.0.10:2342/", candidates[1])
    }

    @Test
    fun `여러 웹 UI 라우트를 알아본다`() {
        listOf("/library/browse", "/albums", "/settings/general", "/places").forEach { route ->
            val candidates = urls("192.168.0.10:2342$route")
            assertEquals(
                "route=$route 는 두 번째 후보가 루트여야 한다",
                "http://192.168.0.10:2342/",
                candidates[1],
            )
        }
    }

    @Test
    fun `진짜 서브패스는 첫 후보로 보존한다`() {
        // 리버스 프록시 뒤 서브패스 설치는 실제로 있는 구성이다.
        assertEquals("https://example.com/photos/", urls("https://example.com/photos").first())
    }

    @Test
    fun `서브패스가 실패하면 루트까지 내려가는 후보를 준다`() {
        val candidates = urls("https://example.com/a/b")
        assertEquals("https://example.com/a/b/", candidates.first())
        assertTrue(candidates.contains("https://example.com/"))
    }

    @Test
    fun `사용자가 붙여넣은 api 접미사를 제거한다`() {
        assertEquals("https://example.com/", urls("https://example.com/api/v1").first())
        assertEquals("https://example.com/", urls("https://example.com/api").first())
        assertEquals("https://example.com/photos/", urls("https://example.com/photos/api/v1").first())
    }

    // ── 잡다한 입력 ──────────────────────────────────────────────────────

    @Test
    fun `쿼리와 프래그먼트를 버린다`() {
        assertEquals("https://example.com/", urls("https://example.com/?tab=1#x").first())
    }

    @Test
    fun `공백을 무시한다`() {
        assertEquals("https://example.com/", urls("  https://example.com  ").first())
    }

    @Test
    fun `해석 불가능한 입력은 후보가 없다`() {
        assertTrue(ServerUrl.candidates("").isEmpty())
        assertTrue(ServerUrl.candidates("   ").isEmpty())
    }

    @Test
    fun `자격증명이 들어간 URL 에서 사용자정보를 제거한다`() {
        assertFalse(urls("https://user:pw@example.com").first().contains("user"))
    }

    @Test
    fun `공개 도메인은 표준 포트를 먼저 시도한다`() {
        // photos.example.com 은 리버스 프록시 뒤 443 이 거의 전부다.
        // 여기까지 2342 를 먼저 찌르면 가장 흔한 경우가 느려진다.
        val c = urls("photos.example.com")
        assertEquals("https://photos.example.com/", c.first())
        assertTrue("2342 후보도 남아 있어야 한다", c.any { it.contains(":2342") })
    }

    // ── 그 외 ───────────────────────────────────────────────────────────

    @Test
    fun `평문 여부를 판별한다`() {
        assertTrue(ServerUrl.isCleartext(ServerUrl.normalize("http://192.168.0.10:2342")!!))
        assertFalse(ServerUrl.isCleartext(ServerUrl.normalize("https://example.com")!!))
    }

    @Test
    fun `apiBase 는 base 뒤에 api v1 을 붙인다`() {
        val base = ServerUrl.normalize("https://example.com/photos")!!
        assertEquals("https://example.com/photos/api/v1/", ServerUrl.apiBase(base).toString())
    }

    @Test
    fun `사설망 IP 의 apiBase`() {
        val base = ServerUrl.normalize("192.168.0.10:2342")!!
        assertEquals("http://192.168.0.10:2342/api/v1/", ServerUrl.apiBase(base).toString())
    }

    // ── 실제 사용자 서버 주소로 최종 요청 URL 을 못박는다 ────────────────

    @Test
    fun `사용자 서버의 최종 config 요청 URL`() {
        // 이 값이 어긋나면 무조건 404 가 난다. 눈으로 확인 가능하도록 고정한다.
        val base = ServerUrl.candidates("192.168.0.10:2342").first()
        assertEquals("http://192.168.0.10:2342/", base.toString())
        assertEquals(
            "http://192.168.0.10:2342/api/v1/config",
            ServerUrl.apiBase(base).newBuilder().addPathSegment("config").build().toString(),
        )
    }

    @Test
    fun `서브패스 서버의 최종 config 요청 URL`() {
        val base = ServerUrl.candidates("https://example.com/photos").first()
        assertEquals(
            "https://example.com/photos/api/v1/config",
            ServerUrl.apiBase(base).newBuilder().addPathSegment("config").build().toString(),
        )
    }

    @Test
    fun `경로에 슬래시가 겹치지 않는다`() {
        listOf("192.168.0.10:2342", "192.168.0.10:2342/", "https://h.com", "https://h.com/")
            .forEach { input ->
                val url = ServerUrl.apiBase(ServerUrl.candidates(input).first()).toString()
                assertFalse("$input -> $url 에 // 가 있다", url.substringAfter("://").contains("//"))
            }
    }

    // ── IP 만 입력했을 때의 자동 탐색 ────────────────────────────────────

    @Test
    fun `IP 만 넣으면 기본 포트 2342 를 가장 먼저 시도한다`() {
        val c = urls("192.168.0.10")
        assertEquals("http://192.168.0.10:2342/", c.first())
    }

    @Test
    fun `IP 만 넣어도 표준 포트 후보를 함께 만든다`() {
        // 리버스 프록시가 80/443 에서 서비스하는 구성.
        val c = urls("192.168.0.10")
        assertTrue("표준 포트 후보 없음: $c", c.contains("http://192.168.0.10/"))
    }

    @Test
    fun `IP 만 넣으면 흔한 서브패스도 후보에 넣는다`() {
        val c = urls("192.168.0.10")
        listOf("photoprism", "photo", "photos").forEach { sub ->
            assertTrue("$sub 후보 없음", c.any { it.endsWith("/$sub/") })
        }
    }

    @Test
    fun `포트를 적었으면 그 포트만 쓴다`() {
        // 사용자가 8080 이라고 했는데 앱이 몰래 2342 도 찔러보면 안 된다.
        val c = urls("192.168.0.10:8080")
        assertTrue("다른 포트를 시도함: $c", c.all { it.contains(":8080/") })
    }

    @Test
    fun `경로를 적었으면 서브패스를 추측하지 않는다`() {
        // 사용자가 경로를 줬으면 그 의사를 따르고, 파생 후보만 만든다.
        val c = urls("192.168.0.10:2342/library/browse")
        assertTrue("추측 서브패스가 섞임: $c", c.none { it.endsWith("/photoprism/") })
    }

    @Test
    fun `library browse 는 PhotoPrism 표준 경로라 루트로 이어진다`() {
        // 실제 사용자 케이스. 웹 UI 주소를 그대로 붙여넣어도 접속돼야 한다.
        val c = urls("192.168.0.10:2342/library/browse")
        assertEquals("http://192.168.0.10:2342/library/browse/", c.first())
        assertEquals("http://192.168.0.10:2342/", c[1])
    }

    @Test
    fun `후보 순서가 가능성 높은 순이다`() {
        val c = urls("192.168.0.10")
        // 루트가 서브패스 추측보다 앞서야 한다.
        val rootIdx = c.indexOf("http://192.168.0.10:2342/")
        val subIdx = c.indexOfFirst { it.endsWith("/photo/") }
        assertTrue("루트($rootIdx)가 서브패스($subIdx)보다 앞서야 한다", rootIdx < subIdx)
    }

    @Test
    fun `후보가 상한을 넘지 않는다`() {
        // 병렬로 던지므로 소켓이 폭증하면 안 된다.
        assertTrue(ServerUrl.candidates("192.168.0.10").size <= 16)
        assertTrue(ServerUrl.candidates("host.example.com/a/b/c/d/e").size <= 16)
    }
}
