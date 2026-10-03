package com.regnius.photoprism.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerBuildTest {

    @Test
    fun `릴리스 버전 문자열에서 빌드 날짜를 뽑는다`() {
        assertEquals(260_601, ServerBuild.parseOrNull("260601-a7d098548")?.yymmdd)
        assertEquals(260_728, ServerBuild.parseOrNull("260728-bbde8f452")?.yymmdd)
        assertEquals(240_915, ServerBuild.parseOrNull("240915")?.yymmdd)
    }

    @Test
    fun `Plus 빌드의 긴 버전 문자열도 읽는다`() {
        // 실측값 — demo.photoprism.app 이 보고한 형식.
        assertEquals(
            260_827,
            ServerBuild.parseOrNull("260827-dfd52c8e4-Linux-AMD64-Plus")?.yymmdd,
        )
    }

    @Test
    fun `날짜를 읽을 수 없는 개발 빌드는 null 이다`() {
        assertNull(ServerBuild.parseOrNull("develop"))
        assertNull(ServerBuild.parseOrNull("latest"))
        assertNull(ServerBuild.parseOrNull(""))
        assertNull(ServerBuild.parseOrNull(null))
    }

    @Test
    fun `YYMMDD 형태가 아닌 6자리 숫자를 날짜로 오해하지 않는다`() {
        // 월 99 는 존재하지 않는다 — 빌드번호를 버전으로 착각하면
        // 멀쩡한 서버를 "너무 낮은 버전" 으로 차단하게 된다.
        assertNull(ServerBuild.parseOrNull("259901-abc"))
        assertNull(ServerBuild.parseOrNull("260040-abc"))
    }

    @Test
    fun `지원 하한 미만은 TooOld 로 판정한다`() {
        val result = ServerSupport.of("240915-e1a2b3c4")
        assertTrue(result is ServerSupport.TooOld)
    }

    @Test
    fun `하한 당일과 그 이후는 지원한다`() {
        assertTrue(ServerSupport.of("260601-a7d098548") is ServerSupport.Supported)
        assertTrue(ServerSupport.of("260728-bbde8f452") is ServerSupport.Supported)
    }

    @Test
    fun `버전을 못 읽으면 차단이 아니라 Unknown 이다`() {
        // 최신 develop 빌드를 쓰는 사용자를 막아버리는 오탐 비용이
        // 구버전을 통과시키는 비용보다 크다.
        assertTrue(ServerSupport.of("develop") is ServerSupport.Unknown)
    }

    @Test
    fun `ISO 날짜로 표시한다`() {
        assertEquals("2026-06-01", ServerBuild(260_601).toIsoDate())
    }
}
