package com.regnius.photoprism.feature.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 지도 링크 (Phase 3.7).
 *
 * 좌표 문자열이 깨지는 사고는 조용하다 — 지도 앱은 열리는데 엉뚱한 지점을
 * 가리킨다. 그래서 문자열을 눈으로 보는 테스트로 고정해 둔다.
 */
class MapLinkTest {

    @Test
    fun `지점과 핀을 함께 넣는다`() {
        // `geo:lat,lng` 만 주면 그 지점으로 이동만 하고 핀이 안 찍히는 앱이 있다.
        assertEquals(
            "geo:37.570000,126.978000?q=37.570000,126.978000",
            MapLink.geoUri(37.57, 126.978),
        )
    }

    @Test
    fun `라벨은 괄호 안에 넣고 공백을 인코딩한다`() {
        val uri = MapLink.geoUri(37.57, 126.978, "Gyeongbok Palace")

        assertTrue(uri.endsWith("(Gyeongbok%20Palace)"))
        // `+` 가 남으면 지도 앱이 핀 이름에 그 글자를 그대로 보여준다.
        assertFalse(uri.contains("+"))
    }

    @Test
    fun `라벨의 괄호는 구분자와 부딪히므로 치운다`() {
        // 괄호가 이 문법의 구분자다 — 제목에 괄호가 있으면 라벨이 거기서 끊긴다.
        val uri = MapLink.geoUri(1.0, 2.0, "Trip (2026)")

        assertFalse(uri.contains("(2026)"))
        assertTrue(uri.endsWith("(Trip%20%202026%20)"))
    }

    @Test
    fun `빈 라벨은 아예 붙이지 않는다`() {
        assertEquals(MapLink.geoUri(1.0, 2.0), MapLink.geoUri(1.0, 2.0, "   "))
    }

    @Test
    fun `소수점이 쉼표인 로케일에서도 좌표가 안 깨진다`() {
        // 독일·프랑스 등에서는 기본 로케일로 찍으면 "37,57" 이 되어 쉼표로
        // 좌표를 가르는 이 문법이 통째로 깨진다. 실기에서 재현하기 어려운
        // 종류의 버그라 여기서 못 박아 둔다.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)

            assertEquals("geo:37.570000,126.978000?q=37.570000,126.978000", MapLink.geoUri(37.57, 126.978))
            assertEquals(
                "https://www.google.com/maps/search/?api=1&query=37.570000,126.978000",
                MapLink.webUrl(37.57, 126.978),
            )
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `남위 서경도 부호를 유지한다`() {
        // 남반구·서반구 좌표에서 부호가 빠지면 지구 반대편이 열린다.
        assertEquals(
            "geo:-33.868800,-151.209300?q=-33.868800,-151.209300",
            MapLink.geoUri(-33.8688, -151.2093),
        )
    }
}
