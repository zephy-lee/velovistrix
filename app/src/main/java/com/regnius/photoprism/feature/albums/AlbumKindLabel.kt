package com.regnius.photoprism.feature.albums

import androidx.annotation.StringRes
import com.regnius.photoprism.R
import com.regnius.photoprism.core.model.AlbumKind

/**
 * 탐색 축의 번역된 이름.
 *
 * [AlbumKind] 자체는 `:core:model`(순수 Kotlin/JVM)에 있어 안드로이드 리소스를
 * 못 쓴다(§3.4). 그래서 enum 에는 서버가 아는 값만 두고, 보여줄 이름은 여기서
 * 붙인다 — `AlbumSort.labelRes()` 와 같은 방식이다.
 *
 * 하단 메뉴의 타일과 **같은 문자열을 쓴다.** 메뉴에서 "달력" 을 눌렀는데 열린
 * 화면 제목이 다른 단어면 같은 기능인지 의심하게 된다.
 */
@StringRes
fun AlbumKind.titleRes(): Int = when (this) {
    AlbumKind.ALBUM -> R.string.tab_albums
    AlbumKind.CALENDAR -> R.string.menu_calendar
    AlbumKind.MOMENT -> R.string.menu_moments
    AlbumKind.STATE -> R.string.menu_states
    AlbumKind.FOLDER -> R.string.menu_folders
}
