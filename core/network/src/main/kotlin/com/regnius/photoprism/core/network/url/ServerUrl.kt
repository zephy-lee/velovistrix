package com.regnius.photoprism.core.network.url

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 사용자가 입력한 서버 주소를 실제로 접속 가능한 후보들로 바꾼다. §4.5
 *
 * 셀프호스팅 사용자는 주소를 온갖 형태로 입력한다:
 *   `192.168.0.10:2342`, `photos.example.com`, `https://example.com/photos/`,
 *   `http://nas.local:2342/api/v1/`, 그리고 **브라우저 주소창에서 그대로 복사한**
 *   `192.168.0.10:2342/library/browse`
 *
 * 여기서 한 칸이라도 어긋나면 이후 모든 요청이 실패하므로 규칙을 한 곳에 모으고
 * 테스트로 고정한다.
 */
object ServerUrl {

    /** 사용자가 API 경로까지 붙여 넣은 경우 잘라낸다. */
    private val API_SUFFIX = Regex("""/api(/v\d+)?/?$""", RegexOption.IGNORE_CASE)

    /**
     * PhotoPrism 웹 UI 의 SPA 경로들.
     *
     * 사용자가 브라우저 주소창을 그대로 복사하면 `/library/browse`, `/albums`
     * 같은 화면 경로가 딸려 온다. 이건 서버의 서브패스가 아니라 프런트엔드
     * 라우트라, 그대로 base 로 쓰면 `/library/browse/api/v1/config` 를 때려서
     * 404 가 난다.
     */
    private val WEB_UI_ROUTES = setOf(
        "library", "browse", "albums", "photos", "videos", "favorites",
        "places", "labels", "people", "folders", "originals", "moments",
        "calendar", "states", "archive", "review", "private", "hidden",
        "settings", "login", "index", "search", "photo", "video",
    )

    /** PhotoPrism 기본 포트. 이 포트를 쓰면 평문 HTTP 일 확률이 매우 높다. */
    private const val DEFAULT_PHOTOPRISM_PORT = 2342

    /**
     * 리버스 프록시 뒤에 흔히 쓰이는 서브패스.
     *
     * 사용자가 경로를 입력하지 않았을 때만 시도한다. 프록시로 `/photo/` 같은
     * 경로에 붙여 둔 구성이 흔한데, 사용자 입장에서는 브라우저에서 열리는
     * 주소가 곧 서버 주소이므로 "IP 만 넣었는데 왜 안 되지" 가 된다.
     */
    private val COMMON_SUBPATHS = listOf("photoprism", "photo", "photos")

    /**
     * 접속을 시도할 base URL 후보를 **시도 순서대로** 돌려준다.
     *
     * 스킴을 하나로 정해 버릴 수 없다. 예전에는 없으면 무조건 `https://` 를
     * 붙였는데, PhotoPrism 의 가장 흔한 배포 형태인 **LAN + 평문 HTTP**
     * (`192.168.0.10:2342`) 에서 TLS 핸드셰이크 오류로 그냥 막혔다.
     * 반대로 무조건 `http://` 로 하면 공개 도메인 사용자가 평문으로 로그인하게
     * 된다. 그래서 **둘 다 후보로 두고 상황에 맞는 순서로** 시도한다.
     *
     * 사용자가 스킴을 직접 적었으면 그 의사를 존중해 후보는 하나뿐이다.
     */
    fun candidates(input: String): List<HttpUrl> {
        val raw = input.trim()
        if (raw.isEmpty()) return emptyList()

        val hasScheme = raw.contains("://")
        val bare = if (hasScheme) raw.substringAfter("://") else raw
        val probe = "http://$bare".toHttpUrlOrNull() ?: return emptyList()
        if (probe.host.isBlank()) return emptyList()

        // 사용자가 포트를 적었는지 판단한다. HttpUrl 은 없으면 스킴 기본값을
        // 채워 넣으므로, 원본 문자열의 host 부분에 콜론이 있는지로 본다.
        val authority = bare.substringBefore('/')
        val hasExplicitPort = authority.substringAfterLast(']').contains(':')

        val schemes = when {
            hasScheme -> listOf(raw.substringBefore("://"))
            probe.prefersCleartext(hasExplicitPort) -> listOf("http", "https")
            else -> listOf("https", "http")
        }

        // 포트를 안 적었을 때의 순서는 **주소가 사설망인지**에 달렸다.
        //  - LAN 주소(192.168.x.x)는 PhotoPrism 을 직접 노출한 구성이 대부분이라
        //    2342 를 먼저 본다. IP 만 입력해도 접속되게 하려는 것이 이 기능의 목적이다.
        //  - 공개 도메인(photos.example.com)은 리버스 프록시 뒤 443/80 이 거의
        //    전부다. 여기까지 2342 를 먼저 찌르면 흔한 경우가 느려진다.
        val ports: List<Int?> = when {
            hasExplicitPort -> listOf(probe.port)
            probe.host.isPrivateAddress() -> listOf(DEFAULT_PHOTOPRISM_PORT, null)
            else -> listOf(null, DEFAULT_PHOTOPRISM_PORT)
        }

        val userPath = probe.encodedPath
        val hasUserPath = userPath.trim('/').isNotEmpty()

        // 후보를 "가능성 높은 순" 으로 만든다. 병렬로 시도하더라도 승자는
        // 이 순서에서 가장 앞선 것을 고르므로 순서가 여전히 의미를 갖는다.
        val result = LinkedHashSet<HttpUrl>()
        for (port in ports) {
            for (scheme in schemes) {
                val root = HttpUrl.Builder()
                    .scheme(scheme)
                    .host(probe.host)
                    .apply { port?.let { port(it) } }
                    .build()

                val paths: List<List<String>> = if (hasUserPath) {
                    root.derivedPaths(userPath)
                } else {
                    listOf(emptyList<String>()) + COMMON_SUBPATHS.map { listOf(it) }
                }

                paths.forEach { segs -> result += root.withPath(segs) }
            }
        }
        return result.take(MAX_CANDIDATES)
    }

    /**
     * 단일 정규화. 스킴이 없으면 후보 중 첫 번째를 쓴다.
     * 이미 스킴과 경로가 확정된 URL 을 다룰 때만 쓸 것.
     */
    fun normalize(input: String): HttpUrl? = candidates(input).firstOrNull()

    /** 평문 HTTP 인가 — 로그인 화면에서 경고를 띄우는 데 쓴다. */
    fun isCleartext(url: HttpUrl): Boolean = !url.isHttps

    /** `<base>api/v1/` — Retrofit baseUrl 로 넘길 값. */
    fun apiBase(base: HttpUrl): HttpUrl =
        base.newBuilder().addPathSegments("api/v1/").build()

    // ── 내부 ────────────────────────────────────────────────────────────

    /**
     * 경로 후보. 입력한 경로로 먼저 시도하고, 실패하면 뒤에서부터 한 칸씩
     * 잘라내며 루트까지 내려간다.
     *
     * 브라우저에서 복사한 `…:2342/library/browse` 같은 입력을 구제하기 위한
     * 것이다. 반대로 진짜 서브패스(`example.com/photos/`)에 설치한 사용자도
     * 첫 후보에서 바로 성공하므로 손해가 없다.
     */
    /**
     * 사용자가 경로를 적었을 때의 후보 경로들.
     *
     * 입력한 경로를 **먼저** 시도한다 — `example.com/photos/` 처럼 진짜
     * 서브패스에 설치한 경우가 있고, 하필 그 이름이 웹 UI 라우트와 겹칠 수
     * 있기 때문이다. 실패했을 때만 잘라낸 후보로 넘어간다.
     */
    private fun HttpUrl.derivedPaths(encodedPath: String): List<List<String>> {
        val cleaned = API_SUFFIX.replace(encodedPath, "")
        val segments = cleaned.split('/').filter { it.isNotBlank() }
        val uiRouteIndex = segments.indexOfFirst { it.lowercase() in WEB_UI_ROUTES }
        return buildList {
            add(segments)
            if (uiRouteIndex >= 0) add(segments.take(uiRouteIndex))
            for (i in segments.size - 1 downTo 0) add(segments.take(i))
        }.distinct().take(MAX_PATH_CANDIDATES)
    }

    private fun HttpUrl.withPath(segments: List<String>): HttpUrl =
        newBuilder()
            .encodedPath("/" + segments.joinToString("/").let { if (it.isEmpty()) "" else "$it/" })
            .query(null)
            .fragment(null)
            .username("")
            .password("")
            .build()

    /** 사설망·루프백이거나 PhotoPrism 기본 포트면 평문을 먼저 시도한다. */
    private fun HttpUrl.prefersCleartext(hasExplicitPort: Boolean): Boolean =
        (hasExplicitPort && port == DEFAULT_PHOTOPRISM_PORT) || host.isPrivateAddress()

    private fun String.isPrivateAddress(): Boolean {
        if (equals("localhost", ignoreCase = true)) return true
        if (endsWith(".local", ignoreCase = true) || endsWith(".lan", ignoreCase = true)) return true
        val octets = split('.')
        if (octets.size != 4) return false
        val n = octets.map { it.toIntOrNull() ?: return false }
        if (n.any { it !in 0..255 }) return false
        return when {
            n[0] == 10 -> true                       // 10.0.0.0/8
            n[0] == 127 -> true                      // 루프백
            n[0] == 192 && n[1] == 168 -> true       // 192.168.0.0/16
            n[0] == 172 && n[1] in 16..31 -> true    // 172.16.0.0/12
            n[0] == 169 && n[1] == 254 -> true       // 링크 로컬
            else -> false
        }
    }

    private const val MAX_PATH_CANDIDATES = 4

    /** 병렬로 시도하므로 넉넉하되, 소켓이 폭증하지 않을 만큼만. */
    private const val MAX_CANDIDATES = 16
}
