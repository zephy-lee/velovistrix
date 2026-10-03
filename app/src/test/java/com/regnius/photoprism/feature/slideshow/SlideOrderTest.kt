package com.regnius.photoprism.feature.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 슬라이드 순서 — 순차/셔플과 앞뒤 이동 (Phase 3.1). */
class SlideOrderTest {

    @Test
    fun `순차는 다음 자리로 하나씩 간다`() {
        val order = SlideOrder(shuffle = false).apply { start(0) }
        assertEquals(1, order.next(5))
        assertEquals(2, order.next(5))
        assertEquals(3, order.next(5))
    }

    @Test
    fun `순차는 끝에서 처음으로 돌아온다`() {
        val order = SlideOrder(shuffle = false).apply { start(4) }
        assertEquals(0, order.next(5))
    }

    @Test
    fun `순차의 이전은 한 칸 뒤이고 처음에서는 끝으로 돈다`() {
        val order = SlideOrder(shuffle = false).apply { start(0) }
        assertEquals(4, order.previous(5))
    }

    @Test
    fun `중간부터 시작해도 그 자리에서 이어간다`() {
        // 그리드에서 스크롤해 둔 자리부터 시작하는 경우(Phase 3.1).
        val order = SlideOrder(shuffle = false).apply { start(37) }
        assertEquals(38, order.next(100))
    }

    @Test
    fun `사진이 한 장뿐이면 계속 그 자리다`() {
        val order = SlideOrder(shuffle = false).apply { start(0) }
        assertEquals(0, order.next(1))
        assertEquals(0, order.next(1))
    }

    @Test
    fun `셔플은 한 바퀴 안에 모든 자리를 한 번씩 보여준다`() {
        // "셔플이 고장 났다"는 인상의 원인이 바로 같은 사진이 금방 다시 나오는
        // 것이라, 매번 순수 난수로 뽑지 않는다는 걸 여기서 고정한다.
        val count = 20
        val order = SlideOrder(shuffle = true, random = Random(1234)).apply { start(0) }
        val seen = mutableSetOf(0)
        repeat(count - 1) { seen.add(order.next(count)) }
        assertEquals((0 until count).toSet(), seen)
    }

    @Test
    fun `셔플은 순서를 실제로 섞는다`() {
        val count = 30
        val order = SlideOrder(shuffle = true, random = Random(7)).apply { start(0) }
        val sequence = List(count - 1) { order.next(count) }
        assertNotEquals((1 until count).toList(), sequence)
    }

    @Test
    fun `셔플에서 이전은 방금 봤던 사진으로 돌아간다`() {
        val order = SlideOrder(shuffle = true, random = Random(42)).apply { start(0) }
        val first = order.next(50)
        val second = order.next(50)
        assertEquals(first, order.previous(50))
        assertEquals(0, order.previous(50))
        // 다시 앞으로 가면 지나온 순서를 그대로 되짚는다.
        assertEquals(first, order.next(50))
        assertEquals(second, order.next(50))
    }

    @Test
    fun `미리 본 다음 자리가 실제로 나올 자리와 같다`() {
        // 다음 동영상을 미리 준비하려면 이 둘이 반드시 일치해야 한다 —
        // 어긋나면 준비가 통째로 헛돈다.
        val order = SlideOrder(shuffle = true, random = Random(99)).apply { start(0) }
        repeat(10) {
            val peeked = order.peekNext(40)
            assertEquals(peeked, order.next(40))
        }
    }

    @Test
    fun `목록이 자라도 새로 늘어난 자리가 후보에 들어온다`() {
        // 슬라이드쇼가 도는 동안에도 Paging 이 계속 뒤를 채운다.
        val order = SlideOrder(shuffle = true, random = Random(5)).apply { start(0) }
        val seen = mutableSetOf<Int>()
        repeat(60) { seen.add(order.next(80)) }
        assertTrue("늘어난 뒤쪽 자리도 나와야 한다", seen.any { it >= 40 })
    }

    @Test
    fun `빈 목록에서는 0을 준다`() {
        val order = SlideOrder(shuffle = true).apply { start(0) }
        assertEquals(0, order.next(0))
        assertEquals(0, order.previous(0))
        assertEquals(0, order.peekNext(0))
    }
}
