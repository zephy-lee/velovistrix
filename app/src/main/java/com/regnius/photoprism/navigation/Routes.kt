package com.regnius.photoprism.navigation

import androidx.annotation.StringRes
import com.regnius.photoprism.R
import com.regnius.photoprism.core.data.FeedKey
import kotlinx.serialization.Serializable

/**
 * 타입 안전 네비게이션 라우트.
 *
 * 문자열 경로 대신 [Serializable] 데이터 클래스를 쓰면 인자 이름 오타나
 * 타입 불일치가 컴파일 타임에 잡힌다.
 */
@Serializable data object LoginRoute

@Serializable data object MainRoute

/**
 * @param photoCount 앨범의 전체 장수. 앨범 목록 응답에 이미 들어 있는 값이라
 *   그대로 넘긴다 — 상세 화면이 이걸 다시 조회할 필요가 없고, PhotoPrism 검색
 *   API 는 전체 개수를 주지 않으므로 **상세 화면에서 알 수 있는 유일한 총량**이다.
 */
@Serializable data class AlbumDetailRoute(
    val albumUid: String,
    val title: String,
    val photoCount: Int = 0,
)

/**
 * 전체화면 뷰어.
 *
 * 사진 목록 자체를 인자로 넘기지 않는다 — 만 장짜리 목록을 직렬화해 Bundle 에
 * 담을 수는 없다. 대신 어떤 목록인지를 가리키는 [albumUid]/[query]/[order] 와
 * 시작 위치만 넘기고, 뷰어는 PhotoFeedController 가 이미 캐시해둔 **같은**
 * 페이징 스트림을 받아 쓴다. 그래서 뷰어가 열릴 때 네트워크 요청이 없다.
 *
 * [requireLocation] 도 **반드시 같이 실어야 한다.** 이건 서버 쿼리가 아니라
 * `PhotoFeedController.feed` 가 받은 페이지를 걸러내는 클라이언트 필터라
 * ([FeedKey.requireLocation]), 빼먹으면 뷰어의 [FeedKey] 가 그리드의 것과
 * 달라진다. 그러면 캐시가 안 맞아 다른 스트림을 새로 만들고, 그 스트림은
 * **걸러지지 않은 목록**이라 [startIndex] 가 전혀 다른 사진을 가리킨다 —
 * 위치 필터를 건 검색에서 사진을 누르면 엉뚱한 사진이 열렸다 (§17.46).
 */
@Serializable
data class ViewerRoute(
    val albumUid: String? = null,
    val query: String? = null,
    val order: String = "newest",
    val startIndex: Int = 0,
    val requireLocation: Boolean? = null,
)

/**
 * 슬라이드쇼 (Phase 3.1).
 *
 * [ViewerRoute] 와 같은 이유로 목록 자체가 아니라 목록을 가리키는 키만 넘긴다 —
 * 슬라이드쇼도 그리드가 이미 채워 둔 페이징 스트림을 그대로 물려받는다.
 */
@Serializable
data class SlideshowRoute(
    val albumUid: String? = null,
    val query: String? = null,
    val order: String = "newest",
    val startIndex: Int = 0,
    val requireLocation: Boolean? = null,
)

/*
 * ---------------------------------------------------------------------------
 * [FeedKey] ↔ 라우트 변환 — **양방향을 여기 한 곳에만 둔다.**
 *
 * §17.46 이 이 네 함수가 생긴 이유다. 원래는 네비게이션 호출부와 화면 진입부
 * 양쪽에 손으로 필드를 나열했는데, [FeedKey] 에 [FeedKey.requireLocation] 이
 * 늘었을 때 **한쪽만 고쳤다.** 컴파일은 그대로 통과한다 — 뺀 인자에 기본값이
 * 있으니 타입이 맞는다. 그래서 조용히 어긋난 채로 배포 직전까지 갔다.
 *
 * 앞으로 [FeedKey] 에 필드가 늘면 여기 네 곳만 보면 된다. 라우트에 필드를
 * 추가하지 않으면 `copy` 가 아니라 생성자 호출이라 여기서 컴파일이 깨진다.
 * ---------------------------------------------------------------------------
 */

fun FeedKey.toViewerRoute(startIndex: Int): ViewerRoute = ViewerRoute(
    albumUid = albumUid,
    query = query,
    order = order,
    startIndex = startIndex,
    requireLocation = requireLocation,
)

fun ViewerRoute.toFeedKey(): FeedKey = FeedKey(
    albumUid = albumUid,
    query = query,
    order = order,
    requireLocation = requireLocation,
)

fun FeedKey.toSlideshowRoute(startIndex: Int): SlideshowRoute = SlideshowRoute(
    albumUid = albumUid,
    query = query,
    order = order,
    startIndex = startIndex,
    requireLocation = requireLocation,
)

fun SlideshowRoute.toFeedKey(): FeedKey = FeedKey(
    albumUid = albumUid,
    query = query,
    order = order,
    requireLocation = requireLocation,
)

/**
 * 설정 (Phase 2.11).
 *
 * 원래 하단 "메뉴" 탭의 본문이었는데 상단 앱바의 오버플로(⋮)에서 여는 별도
 * 화면이 됐다 — 어느 탭에서든 열 수 있어야 해서 탭이 아니라 라우트다.
 */
@Serializable data object SettingsRoute

/** 하단 네비게이션 탭. */
enum class MainTab(@StringRes val labelRes: Int) {
    PHOTOS(R.string.tab_photos),
    ALBUMS(R.string.tab_albums),
    FAVORITES(R.string.tab_favorites),
    MENU(R.string.tab_menu),
    ;

    companion object {
        /**
         * 기본 시작 탭은 **앨범**이다 (§5.1) — Phase 2.11 설정에서 바꾸지
         * 않았을 때의 값. 안드로이드 관례상 첫 탭이 시작 화면이지만, 앨범
         * 우선 진입이 이 앱의 의도된 동선이라 의도적으로 벗어난다. 탭 순서
         * 자체는 바꾸지 않는다 — 순서까지 바꾸면 다른 갤러리 앱에서 옮겨온
         * 사용자의 근육 기억이 깨진다.
         */
        val START = ALBUMS

        fun from(startTab: com.regnius.photoprism.core.settings.StartTab): MainTab = when (startTab) {
            com.regnius.photoprism.core.settings.StartTab.ALBUMS -> ALBUMS
            com.regnius.photoprism.core.settings.StartTab.PHOTOS -> PHOTOS
            com.regnius.photoprism.core.settings.StartTab.FAVORITES -> FAVORITES
        }
    }
}
