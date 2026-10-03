package com.regnius.photoprism.core.model

/**
 * 앨범 목록 정렬 기준.
 *
 * PhotoPrism 의 `/api/v1/albums?order=` 는 서버가 이해하는 값이 몇 개
 * 안 되고(`name`, `added`, `edited`, `newest`, `oldest` 정도), **내림차순
 * 이름 정렬(Z→A)은 서버에 아예 없다** — `order=name-desc`, `-name` 등을
 * 실제로 찔러봐도 전부 조용히 기본 정렬로 떨어진다 (2026-08-28 실측,
 * demo.photoprism.app).
 *
 * 그래서 정렬은 **서버가 아니라 클라이언트에서** 한다. 앨범은 사진과 달리
 * 사용자당 많아야 수백 개 수준이라, 한 번에 다 받아와 메모리에서 정렬해도
 * 무리가 없다. 이렇게 하면 정렬 기준을 바꿀 때마다 재요청할 필요도 없고,
 * 서버가 지원하지 않는 역순도 자유롭게 구현할 수 있다.
 */
/**
 * 화면에 보일 이름은 여기 두지 않는다 — 이 모듈은 순수 Kotlin/JVM 이라
 * 안드로이드 리소스를 못 쓴다(§3.4). 번역된 라벨은 `:app` 의
 * `AlbumSort.labelRes()` 가 붙인다.
 */
enum class AlbumSort {
    NAME_ASC,
    NAME_DESC,
    RECENTLY_UPDATED,
    OLDEST,
    ;

    companion object {
        val DEFAULT = NAME_ASC
    }
}

/**
 * 목록에 정렬 기준과 "즐겨찾기 먼저" 를 적용한다.
 *
 * 즐겨찾기 우선은 별도 정렬 모드가 아니라 **얹는 옵션**으로 뒀다. 파일
 * 탐색기·드라이브류 앱에서 흔한 "고정 항목은 항상 위" 패턴과 같다 — 어떤
 * 기준으로 정렬하든 즐겨찾기 앨범이 눈에 먼저 띄면 되고, 정렬 기준 자체를
 * 4개에서 8개로 두 배 늘릴 필요가 없다.
 *
 * 두 단계 모두 안정 정렬(stable sort)이라 즐겨찾기 그룹 안에서의 순서가
 * 흐트러지지 않는다.
 */
fun List<Album>.sortedForDisplay(sort: AlbumSort, favoritesFirst: Boolean): List<Album> {
    val base = sortedWith(sort.comparator())
    return if (favoritesFirst) base.sortedByDescending { it.favorite } else base
}

private fun AlbumSort.comparator(): Comparator<Album> = when (this) {
    AlbumSort.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    AlbumSort.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }
    // ISO 8601 문자열은 사전식 비교가 곧 시간순 비교와 같다.
    AlbumSort.RECENTLY_UPDATED -> compareByDescending { it.updatedAt.orEmpty() }
    AlbumSort.OLDEST -> compareBy { it.createdAt.orEmpty() }
}
