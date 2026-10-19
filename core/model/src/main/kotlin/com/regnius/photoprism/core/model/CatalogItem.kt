package com.regnius.photoprism.core.model

import androidx.compose.runtime.Immutable

/**
 * 라벨 또는 사람 — **사진 목록으로 들어가는 이름표** (Phase 3.5~3.6).
 *
 * 앨범이 아니라서 `albums?type=` 으로는 안 오고 자기 전용 목록 API 가 있다
 * (`/labels`, `/subjects`). 하지만 탭한 뒤는 앨범과 달리 **검색 쿼리**로
 * 바뀌므로([query]), 사진 그리드·뷰어·슬라이드쇼가 그대로 받는다.
 *
 * 라벨과 사람을 한 타입으로 둔 이유: 두 응답에서 우리가 쓰는 필드가 완전히
 * 같다(이름·장수·대표 사진). 구분이 필요한 곳은 [kind] 하나이고, 그 차이는
 * 화면에서만 쓴다 — 사람은 얼굴이라 동그랗게, 라벨은 사물이라 네모로 그린다.
 */
@Immutable
data class CatalogItem(
    val kind: CatalogKind,
    val uid: String,
    val name: String,
    /** PhotoPrism 의 슬러그. 라벨 검색에 쓰는 값이다 (`label:cat`). */
    val slug: String,
    val photoCount: Int,
    /** 대표 사진의 **파일 해시**. 앨범 커버와 같은 썸네일 경로로 받는다. */
    val coverHash: String?,
) {
    /**
     * 이 이름표에 속한 사진을 찾는 PhotoPrism 검색 쿼리.
     *
     * 사람은 이름이 아니라 **UID** 로 건다. `subject:` 는 둘 다 받지만, 이름을
     * 쓰면 두 가지가 문제가 된다 — 공백이 든 이름은 따옴표로 감싸야 하고
     * (`subject:"Jane Doe"`), 동명이인이 있으면 둘이 한 목록으로 섞인다.
     * UID 는 그 둘이 다 없다.
     *
     * 라벨은 반대로 **슬러그**로 건다. 라벨 UID 로는 검색이 안 되고
     * (`label:` 은 슬러그를 기대한다), 슬러그는 이미 공백 없는 소문자라
     * 따옴표가 필요 없다.
     */
    val query: String
        get() = when (kind) {
            CatalogKind.LABEL -> "label:${slug.ifBlank { name }}"
            CatalogKind.PERSON -> "subject:$uid"
        }
}

enum class CatalogKind(val storageKey: String) {
    LABEL("label"),
    PERSON("person"),
    ;

    companion object {
        fun from(storageKey: String?): CatalogKind =
            entries.firstOrNull { it.storageKey == storageKey } ?: LABEL
    }
}
