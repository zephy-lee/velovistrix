package com.regnius.photoprism.core.ui

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 월별 헤더의 **파싱**만 고정한다([monthKey]).
 *
 * 예전에는 이 함수가 "2015년 7월" 같은 완성된 문자열을 돌려줬고 테스트도 그
 * 문자열을 검증했다. 다국어를 넣으면서 파싱과 표기를 갈랐다 — 표기는 로케일이
 * 정하는 값이라 고정할 대상이 아니고, 정작 깨지기 쉬운 건 서버가 주는
 * 타임스탬프 문자열을 읽어내는 쪽이다.
 */
class PhotoGridMonthLabelTest {

    @Test
    fun `ISO 타임스탬프에서 연월을 읽는다`() {
        assertEquals(YearMonth.of(2015, 7), monthKey("2015-07-26T10:26:08Z"))
        assertEquals(YearMonth.of(2026, 1), monthKey("2026-01-05T00:00:00Z"))
    }

    @Test
    fun `해가 바뀌는 경계`() {
        assertEquals(YearMonth.of(2025, 12), monthKey("2025-12-31T23:59:59Z"))
        assertEquals(YearMonth.of(2026, 1), monthKey("2026-01-01T00:00:00Z"))
    }

    @Test
    fun `날짜가 없으면 null`() {
        assertNull(monthKey(null))
        assertNull(monthKey(""))
        assertNull(monthKey("   "))
    }

    @Test
    fun `너무 짧으면 null`() {
        assertNull(monthKey("2015"))
        assertNull(monthKey("2015-0"))
    }

    @Test
    fun `월이 범위를 벗어나면 null`() {
        assertNull(monthKey("2015-13-01T00:00:00Z"))
        assertNull(monthKey("2015-00-01T00:00:00Z"))
    }

    @Test
    fun `구분자가 없으면 null`() {
        // "20150726" 의 5~6 번째 글자는 "07" 이 아니라 "72" 라 월로 읽히지 않는다.
        assertNull(monthKey("20150726"))
    }
}
