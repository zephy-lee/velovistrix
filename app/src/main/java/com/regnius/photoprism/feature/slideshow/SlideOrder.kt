package com.regnius.photoprism.feature.slideshow

import kotlin.random.Random

/**
 * 슬라이드가 지나갈 순서 (Phase 3.1).
 *
 * 순차 재생과 셔플, 그리고 앞뒤 이동을 한 곳에서 결정한다. 화면이 직접
 * `index + 1` 을 계산하지 않는 이유는 셔플 때문이다 — 셔플에서 "이전"은
 * `index - 1` 이 아니라 **실제로 방금 전에 봤던 사진**이어야 하고, 그러려면
 * 지나온 순서를 기억하고 있어야 한다.
 *
 * 셔플은 무작위로 하나씩 뽑되 **이미 나온 것은 한 바퀴가 끝날 때까지 다시
 * 뽑지 않는다.** 매번 순수 난수로 뽑으면 같은 사진이 금방 두세 번 나와서
 * "셔플이 고장 났다"는 인상을 준다.
 *
 * 목록은 스크롤과 무관하게 계속 자라므로([count] 가 호출마다 달라진다) 전체
 * 순열을 미리 만들어 두지 않고, 뽑을 때마다 그 시점의 [count] 안에서 고른다.
 */
internal class SlideOrder(
    private val shuffle: Boolean,
    private val random: Random = Random.Default,
) {
    /** 지나온 순서. 뒤로 갔다가 다시 앞으로 가면 이걸 그대로 되짚는다. */
    private val history = mutableListOf<Int>()
    private var cursor = -1

    /** 이번 바퀴에 이미 나온 자리들. */
    private val shown = mutableSetOf<Int>()

    /**
     * 다음에 나올 자리를 미리 정해 둔 값.
     *
     * 슬라이드쇼는 **앞 슬라이드가 보이는 동안 다음 동영상을 미리 준비**하는데,
     * 셔플에서는 그때 다음이 무엇인지 알아야 준비할 수 있다. 그래서 한 걸음
     * 앞서 뽑아 두고, 실제로 넘어갈 때 그 값을 그대로 쓴다 — 미리 준비한 것과
     * 실제로 재생하는 것이 어긋나면 준비가 통째로 헛돈다.
     */
    private var lookahead: Int? = null

    val current: Int get() = history.getOrElse(cursor) { 0 }

    fun start(index: Int) {
        history.clear()
        shown.clear()
        lookahead = null
        history.add(index)
        shown.add(index)
        cursor = 0
    }

    /** 다음에 나올 자리. 아직 넘어가지는 않는다. */
    fun peekNext(count: Int): Int {
        if (count <= 0) return 0
        if (cursor < history.lastIndex) return history[cursor + 1]
        lookahead?.let { if (it < count) return it }
        return pick(count).also { lookahead = it }
    }

    fun next(count: Int): Int {
        if (count <= 0) return 0
        if (cursor < history.lastIndex) {
            cursor++
            lookahead = null
            return history[cursor]
        }
        val picked = peekNext(count)
        lookahead = null
        history.add(picked)
        shown.add(picked)
        cursor = history.lastIndex
        trimHistory()
        return picked
    }

    fun previous(count: Int): Int {
        if (count <= 0) return 0
        lookahead = null
        if (cursor > 0) {
            cursor--
            return history[cursor]
        }
        // 되짚을 기록이 없다 — 순차면 한 칸 뒤로, 셔플이면 새로 하나 뽑는다.
        val picked = if (shuffle) pick(count) else (current - 1).mod(count)
        history.add(0, picked)
        shown.add(picked)
        cursor = 0
        trimHistory()
        return picked
    }

    private fun pick(count: Int): Int {
        if (!shuffle) return (current + 1).mod(count)
        if (count <= 1) return 0
        // 한 바퀴 다 돌았으면 새 바퀴를 시작한다. 방금 본 것만 남겨 두어
        // 바퀴가 바뀌는 경계에서 같은 사진이 연달아 나오지 않게 한다.
        if (shown.size >= count) {
            shown.clear()
            shown.add(current)
        }
        repeat(REJECTION_TRIES) {
            val candidate = random.nextInt(count)
            if (candidate !in shown) return candidate
        }
        // 거의 다 본 상태라 난수로는 잘 안 걸린다 — 남은 것 중 첫 번째.
        return (0 until count).firstOrNull { it !in shown } ?: (current + 1).mod(count)
    }

    /** 만 장짜리 목록을 몇 시간 돌려도 기록이 무한정 늘지 않게 앞에서 잘라낸다. */
    private fun trimHistory() {
        if (history.size <= MAX_HISTORY) return
        val drop = history.size - MAX_HISTORY
        repeat(drop) { history.removeAt(0) }
        cursor -= drop
    }

    private companion object {
        const val REJECTION_TRIES = 64
        const val MAX_HISTORY = 500
    }
}
