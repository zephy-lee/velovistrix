package com.regnius.photoprism.feature.catalog

import androidx.annotation.StringRes
import com.regnius.photoprism.R
import com.regnius.photoprism.core.model.CatalogKind

/**
 * 라벨/인물의 번역된 문구.
 *
 * [CatalogKind] 는 `:core:model`(순수 Kotlin/JVM)에 있어 안드로이드 리소스를
 * 못 쓴다(§3.4) — `AlbumSort.labelRes()`, `AlbumKind.titleRes()` 와 같은 방식이다.
 *
 * 제목은 하단 메뉴의 타일과 **같은 문자열**을 쓴다. 메뉴에서 "사람" 을 눌렀는데
 * 열린 화면 제목이 다른 단어면 같은 기능인지 의심하게 된다.
 */
@StringRes
fun CatalogKind.titleRes(): Int = when (this) {
    CatalogKind.LABEL -> R.string.menu_labels
    CatalogKind.PERSON -> R.string.menu_people
}

/**
 * 비어 있을 때의 문장.
 *
 * 둘 다 **사용자가 만드는 게 아니라 서버가 찾아 주는 것**이라, "없습니다" 로
 * 끝내지 않고 어떻게 채워지는지를 같이 알려준다 — 라벨은 사진에서 무엇이
 * 찍혔는지, 사람은 얼굴을 서버가 인식하면서 생긴다.
 */
@StringRes
fun CatalogKind.emptyRes(): Int = when (this) {
    CatalogKind.LABEL -> R.string.labels_empty
    CatalogKind.PERSON -> R.string.people_empty
}

@StringRes
fun CatalogKind.loadFailedRes(): Int = R.string.catalog_load_failed
