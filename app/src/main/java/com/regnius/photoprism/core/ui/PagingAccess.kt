package com.regnius.photoprism.core.ui

import androidx.paging.compose.LazyPagingItems

/**
 * Paging 목록을 **범위 확인 후에** 읽는다.
 *
 * `LazyPagingItems.peek`/`get` 은 범위를 벗어나면 null 이 아니라
 * [IndexOutOfBoundsException] 을 던진다(`PageStore.checkIndex`). 그리드는
 * LazyLayout 이 항상 유효한 인덱스만 넘겨줘서 이걸 신경 쓸 일이 없지만,
 * **뷰어와 슬라이드쇼는 자기가 인덱스를 들고 있다** — 목록이 새 세대로 넘어가는
 * 순간 `itemCount` 가 잠깐 0 이 되는데, 들고 있던 페이지 번호는 그대로라
 * 그 프레임에서 그대로 앱이 죽는다(§17.30).
 *
 * 그래서 화면이 스스로 관리하는 인덱스로 Paging 을 읽을 때는 항상 이걸 쓴다.
 */
fun <T : Any> LazyPagingItems<T>.peekAt(index: Int): T? =
    if (index in 0 until itemCount) peek(index) else null

/**
 * [peekAt] 과 같되, Paging 에 "이 자리를 보고 있다"고 알려 로드까지 요청한다.
 *
 * 스크롤 이벤트가 없는 화면(슬라이드쇼)에서는 이 호출이 목록을 앞으로 밀고
 * 가는 유일한 동력이다.
 */
fun <T : Any> LazyPagingItems<T>.getAt(index: Int): T? =
    if (index in 0 until itemCount) this[index] else null
