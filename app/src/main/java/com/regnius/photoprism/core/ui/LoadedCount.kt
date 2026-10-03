package com.regnius.photoprism.core.ui

/**
 * 지금까지 불러온 개수.
 *
 * PhotoPrism 검색 API 는 전체 개수를 미리 알려주지 않는다 (§12.1) — 그래서
 * "총 N장" 을 사전에 표시할 방법이 없다. 대신 **지금까지 실제로 불러온 개수**를
 * 보여준다. [complete] 가 false 면 더 있을 수 있다는 뜻이라 "137+" 처럼 표시하고,
 * 서버가 마지막 페이지에서 요청보다 적게 반환해 끝에 도달하면 [complete] 가
 * true 로 바뀌어 정확한 총량이 된다.
 *
 * 부수 효과로, 스크롤해도 이 숫자가 늘지 않으면 사용자가 "여기서 막혔다" 는
 * 걸 직접 확인할 수 있는 진단 도구도 된다.
 */
data class LoadedCount(val count: Int, val complete: Boolean) {
    override fun toString(): String = if (complete) "$count" else "$count+"

    companion object {
        val ZERO = LoadedCount(0, complete = false)
    }
}
