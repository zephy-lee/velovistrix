package com.regnius.photoprism.feature.viewer

import java.net.URLEncoder
import java.util.Locale

/**
 * 사진의 좌표를 지도 앱에 넘길 링크로 바꾼다 (Phase 3.7).
 *
 * 순수 함수만 둔다 — 안드로이드 `Uri`/`Intent` 를 안 쓰므로 에뮬레이터 없이
 * `src/test` 에서 그대로 검증된다. 좌표 문자열이 깨지는 사고는 조용하다:
 * 지도 앱이 열리긴 하는데 엉뚱한 곳(또는 바다)을 가리킨다.
 */
internal object MapLink {

    /**
     * `geo:` URI — 기기에 깔린 **지도 앱 전체**가 받을 수 있는 표준 스킴이다.
     *
     * `geo:lat,lng` 만 주면 그 지점으로 이동만 하고 핀이 안 찍히는 앱이 있어
     * `?q=` 를 같이 넣는다. `q` 의 괄호 안 라벨은 핀에 붙는 이름이다 — 사진
     * 제목을 넣으면 "어느 사진의 위치인지"가 지도에서도 보인다.
     *
     * 좌표는 **[Locale.ROOT] 로 찍는다.** 기기 로케일로 찍으면 소수점이
     * 쉼표인 지역(독일·프랑스 등)에서 `37,57` 이 되어, 쉼표로 좌표를 가르는
     * 이 문법이 통째로 깨진다.
     */
    fun geoUri(latitude: Double, longitude: Double, label: String? = null): String {
        val point = "%.6f,%.6f".format(Locale.ROOT, latitude, longitude)
        val name = label?.trim()?.takeIf { it.isNotBlank() }
            // 괄호는 이 문법의 구분자다 — 제목에 괄호가 들어 있으면 라벨이
            // 거기서 끊긴다.
            ?.replace('(', ' ')?.replace(')', ' ')
            ?.let { "(${encode(it)})" }
            .orEmpty()
        return "geo:$point?q=$point$name"
    }

    /**
     * 지도 앱이 하나도 없을 때의 폴백 — 브라우저로 열 수 있는 주소.
     *
     * 구글 지도의 공식 "search" 링크 형식이다. 지도 앱이 깔려 있으면 그 앱이
     * 이 주소도 받으므로, 폴백이 억지로 웹으로만 가는 것도 아니다.
     */
    fun webUrl(latitude: Double, longitude: Double): String {
        val point = "%.6f,%.6f".format(Locale.ROOT, latitude, longitude)
        return "https://www.google.com/maps/search/?api=1&query=$point"
    }

    /** `URLEncoder` 는 공백을 `+` 로 바꾸는데, URI 경로/쿼리에서는 `%20` 이어야 한다. */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
