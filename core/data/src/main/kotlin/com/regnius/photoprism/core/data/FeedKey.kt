package com.regnius.photoprism.core.data

import com.regnius.photoprism.core.network.PhotoQuery
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 하나의 사진 목록을 식별하는 키.
 *
 * 전체 타임라인 · 앨범의 이미지 탭 · 앨범의 동영상 탭 · 즐겨찾기가 전부
 * 같은 `/photos` 엔드포인트에 파라미터만 다르게 던지는 것이므로, 화면마다
 * 별도 Repository 를 두는 대신 이 키 하나로 구분한다.
 *
 * 네비게이션 인자로 그대로 넘어가기 때문에 [Serializable] 이다.
 */
@Serializable
data class FeedKey(
    val albumUid: String? = null,
    val query: String? = null,
    val order: String = "newest",
    /**
     * "위치 유무" 필터(Phase 2.2)만 여기 별도로 둔다 — PhotoPrism 에 문서화된
     * "GPS 있는 것만" 같은 직접적인 `q=` 문법이 없어서, [query] 로 서버에
     * 보내는 대신 `PhotoFeedController.feed` 에서 받은 페이지를 클라이언트
     * 쪽에서 걸러낸다. null = 필터 없음, true = 위치 있는 것만, false = 없는 것만.
     */
    val requireLocation: Boolean? = null,
) {
    companion object {
        fun timeline() = FeedKey()
        fun albumImages(uid: String) = FeedKey(albumUid = uid, query = PhotoQuery.IMAGES, order = "oldest")
        fun albumVideos(uid: String) = FeedKey(albumUid = uid, query = PhotoQuery.VIDEOS, order = "oldest")
        fun favorites() = FeedKey(query = PhotoQuery.FAVORITES)

        /**
         * 자유 텍스트 검색. PhotoPrism 의 `q=` 는 자유 텍스트와 필터 문법
         * (`type:image`, `favorite:true` 등)을 같은 파라미터에서 함께 받는다 —
         * 사용자가 검색창에 "berlin type:video" 처럼 입력해도 그대로 전달된다.
         */
        fun search(text: String, requireLocation: Boolean? = null) =
            FeedKey(query = text, requireLocation = requireLocation)
    }
}

/**
 * [FeedKey] 를 Room 캐시 신원(문자열)으로 바꾼다.
 *
 * [FeedKey.requireLocation] 은 뺀다 — 서버에 보내지 않는 클라이언트 후처리
 * 필터라, 포함시키면 true/false/null 세 벌이 서버 응답이 완전히 같은데도
 * Room 에 세 번 저장되고 [PhotoFeedRemoteMediator] 도 세 번 따로 REFRESH 를 친다.
 */
fun FeedKey.cacheKey(): String =
    Json.encodeToString(copy(requireLocation = null))
