package com.regnius.photoprism.core.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 그리드 ↔ 뷰어 전환에서 **공유 요소가 될 사진 하나**. §17.45
 *
 * §17.34 에서 `sharedBounds` 를 모든 칸에 걸던 것을 **누른 한 칸**으로 줄였다.
 * 그게 스크롤 성능의 결정적 수정이었지만(90 퍼센타일 프레임 113ms → 17ms) 빈
 * 구멍이 하나 남았다: 뷰어에서 **좌우로 넘긴 뒤 뒤로 가면** 돌아갈 칸이 누른
 * 칸이 아니다. 그 칸에는 `sharedBounds` 가 없으니 전환도 없고, 사진이 그냥
 * 툭 나타난다.
 *
 * 그리드와 뷰어는 서로 다른 NavBackStackEntry 라 `ViewModel` 이 각자 생긴다.
 * 그래서 이 한 값만 앱 스코프로 빼서 잇는다 — 뷰어가 페이지를 넘길 때마다
 * 지금 보는 사진을 적어 두면, 그리드는 **돌아올 그 칸**에만 `sharedBounds` 를
 * 건다. 칸 하나만 거는 성질은 그대로다.
 *
 * 값이 하나뿐이라 여러 그리드(사진 탭 · 즐겨찾기 · 앨범 상세 · 검색)가 같은
 * 값을 본다. 그래도 안전하다 — 한 번에 한 그리드만 전환에 참여하고, 화면 밖
 * 그리드에서 한 칸에 모디파이어가 더 붙는 것은 눈에 띄는 비용이 아니다.
 */
@Singleton
class SharedPhotoTransition @Inject constructor() {
    private val _photoUid = MutableStateFlow<String?>(null)

    /** 지금 전환의 주인공. 아직 아무것도 안 눌렀으면 null. */
    val photoUid: StateFlow<String?> = _photoUid.asStateFlow()

    fun show(uid: String) {
        _photoUid.value = uid
    }
}
