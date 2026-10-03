package com.regnius.photoprism.feature.slideshow

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 슬라이드쇼가 동영상의 **어느 지점부터** 재생할지 (Phase 3.1).
 *
 * "처음부터가 아니라 도입부를 건너뛴다"는 게 이 기능의 요구사항 자체라, 규칙을
 * 여기서 고정한다.
 */
class SlideshowVideoStartTest {

    @Test
    fun `길이의 20퍼센트 지점에서 시작한다`() {
        // 60초 영상, 5초 재생 → 12초 지점.
        assertEquals(12_000L, slideshowVideoStartMs(durationMs = 60_000L, clipMs = 5_000L))
    }

    @Test
    fun `짧은 영상은 도입부만 잘라낸다`() {
        // 6초 영상, 5초 재생 → 20%(1.2초)는 "끝에서 5초 앞"(1초)을 넘으므로 1초.
        assertEquals(1_000L, slideshowVideoStartMs(durationMs = 6_000L, clipMs = 5_000L))
    }

    @Test
    fun `클립보다 짧은 영상은 처음부터 전체를 튼다`() {
        // 라이브 포토(1.5초)처럼 슬라이드 시간보다 짧으면 건너뛸 여유가 없다.
        assertEquals(0L, slideshowVideoStartMs(durationMs = 1_500L, clipMs = 5_000L))
        assertEquals(0L, slideshowVideoStartMs(durationMs = 5_000L, clipMs = 5_000L))
    }

    @Test
    fun `아주 긴 영상은 시작 지점을 30초로 제한한다`() {
        // 20%면 1분이 넘지만, 전사코딩 스트림을 그만큼 시킹하면 버퍼링이
        // 슬라이드 한 장을 통째로 잡아먹는다.
        assertEquals(30_000L, slideshowVideoStartMs(durationMs = 600_000L, clipMs = 5_000L))
        assertEquals(30_000L, slideshowVideoStartMs(durationMs = 150_000L, clipMs = 5_000L))
    }

    @Test
    fun `길이를 모르면 처음부터 튼다`() {
        // §17.8 — merged=true 가 페이지 경계에서 파일을 흘려 길이가 0 으로 올 수
        // 있다. 이때는 플레이어가 준비된 뒤 실제 길이로 다시 시킹한다.
        assertEquals(0L, slideshowVideoStartMs(durationMs = 0L, clipMs = 5_000L))
        assertEquals(0L, slideshowVideoStartMs(durationMs = -1L, clipMs = 5_000L))
    }

    @Test
    fun `재생 길이가 0이면 처음부터 튼다`() {
        assertEquals(0L, slideshowVideoStartMs(durationMs = 60_000L, clipMs = 0L))
    }

    @Test
    fun `시작 지점 뒤로는 항상 클립 길이만큼 남는다`() {
        // 어떤 조합에서도 "틀자마자 끝나는" 일이 없어야 한다.
        for (duration in listOf(500L, 1_000L, 3_333L, 9_000L, 45_000L, 400_000L)) {
            for (clip in listOf(3_000L, 5_000L, 8_000L, 15_000L, 30_000L)) {
                val start = slideshowVideoStartMs(duration, clip)
                val remaining = duration - start
                val expected = minOf(clip, duration)
                assert(remaining >= expected) {
                    "duration=$duration clip=$clip start=$start 남은길이=$remaining < $expected"
                }
            }
        }
    }
}
