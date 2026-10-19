package com.regnius.photoprism.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.regnius.photoprism.core.model.Photo

/**
 * 그리드의 선택 모드 상태 (Phase 4.3 — 여러 장 선택).
 *
 * 저장(4.5)과 공유(4.4)가 둘 다 "어느 사진들" 을 먼저 정해야 하므로, 선택은
 * 두 기능의 공통 전제다. 그래서 둘 중 하나에 붙이지 않고 따로 뒀다.
 *
 * **UID 만이 아니라 [Photo] 를 들고 있는다.** 저장·공유는 파일 해시와 파일
 * 이름까지 필요한데(어느 파일을 받을지 정해야 한다 — `TransferTargets`),
 * UID 집합만 들고 있으면 그걸 다시 페이징 목록에서 찾아와야 하고, 그 사이
 * 페이지가 밀려나면 찾을 수 없다.
 *
 * 선택 순서를 유지한다 — 사용자가 고른 순서대로 저장·공유되는 편이, 목록
 * 순서로 재배열되는 것보다 예측에 맞다. `LinkedHashMap` 의 성질을 그대로 쓴다.
 */
@Stable
class PhotoSelection {
    private var items by mutableStateOf<Map<String, Photo>>(emptyMap())

    val active: Boolean get() = items.isNotEmpty()
    val count: Int get() = items.size
    val photos: List<Photo> get() = items.values.toList()

    fun isSelected(uid: String): Boolean = items.containsKey(uid)

    /** 이미 고른 것이면 뺀다 — 선택 모드에서 탭은 토글이다. */
    fun toggle(photo: Photo) {
        items = if (items.containsKey(photo.uid)) {
            items - photo.uid
        } else {
            items + (photo.uid to photo)
        }
    }

    fun clear() {
        items = emptyMap()
    }
}

/**
 * 화면 회전에는 살아남지 않는다 — `rememberSaveable` 이 아니다.
 *
 * [Photo] 는 Bundle 에 넣을 수 있는 타입이 아니고(§17.47 의 `SearchFilters` 와
 * 같은 사정), UID 만 저장하면 복원 시점에 사진을 다시 찾아야 한다. 선택은
 * **지금 이 화면에서 바로 저장·공유하려고** 만드는 짧은 상태라, 회전하면
 * 풀리는 쪽이 복잡한 복원보다 낫다고 판단했다.
 */
@Composable
fun rememberPhotoSelection(): PhotoSelection = remember { PhotoSelection() }
