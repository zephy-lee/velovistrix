package com.regnius.photoprism.core.model

/**
 * 라이브러리를 훑는 축 — PhotoPrism `/api/v1/albums?type=` 의 값 그대로다.
 *
 * 달력·폴더·지역·나의 순간은 **별도 화면이 아니라 같은 앨범 목록**이다.
 * 서버가 돌려주는 것이 넷 다 앨범 배열이고, 우리는 그 화면을 이미 갖고 있다
 * (§6 Phase 3.1~3.4). 그래서 새 기능 넷이 enum 항목 넷으로 끝난다.
 *
 * @param apiValue 서버에 보내는 `type` 값. **Room 캐시의 격리 키도 이것**이다 —
 *   타입별로 나누지 않으면 달력을 열 때 앨범 목록이 지워진다.
 * @param serverOrder 서버에 보내는 `order` 값.
 * @param userSortable 정렬 바를 보여줄지. [ALBUM] 만 켠다 — 사용자가 직접
 *   만든 앨범은 이름이 뜻을 가지지만, 나머지 넷은 **서버가 주는 순서 자체가
 *   정보**다. 달력을 이름순으로 정렬하면 "4월 2024, 8월 2023" 처럼 연도를
 *   무시하고 섞이고, 폴더는 경로 순서가 곧 디렉터리 구조다.
 */
enum class AlbumKind(
    val apiValue: String,
    val serverOrder: String,
    val userSortable: Boolean,
) {
    /** 사용자가 직접 만든 앨범. 하단 "앨범" 탭. */
    ALBUM("album", "name", userSortable = true),

    /** 달력 — 월 단위로 서버가 자동 생성하는 앨범. 최신 월이 위로 온다. */
    CALENDAR("month", "newest", userSortable = false),

    /** 나의 순간 — 장소·시기·라벨로 서버가 자동으로 묶어 주는 모음. */
    MOMENT("moment", "newest", userSortable = false),

    /**
     * 지역 — 시·도 단위 자동 앨범(예: "경기도, 대한민국").
     *
     * 지도가 아니다. 지도(Places)는 MapLibre 가 필요해 백로그로 빠졌고
     * (2026-09-09), 이건 앨범 목록이라 지금 화면을 그대로 쓴다.
     */
    STATE("state", "name", userSortable = false),

    /** 폴더 — 원본 디렉터리 구조 그대로. */
    FOLDER("folder", "name", userSortable = false),
    ;

    companion object {
        /** 네비게이션 인자(문자열)에서 되돌린다. 모르는 값은 [ALBUM] 으로 떨어진다. */
        fun from(apiValue: String?): AlbumKind =
            entries.firstOrNull { it.apiValue == apiValue } ?: ALBUM
    }
}
