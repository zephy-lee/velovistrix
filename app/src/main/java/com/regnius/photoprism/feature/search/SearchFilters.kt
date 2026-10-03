package com.regnius.photoprism.feature.search

import com.regnius.photoprism.core.data.FeedKey
import com.regnius.photoprism.core.model.MediaType
import com.regnius.photoprism.core.network.PhotoQuery
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 검색 필터 UI (Phase 2.2) — 기간 / 타입 / 카메라 / 위치 유무.
 *
 * [type]/[dateFrom]/[dateTo]/[camera] 는 PhotoPrism 의 `q=` 문법으로 서버에
 * 그대로 보낸다. `before:`/`after:`/`camera:` 는 PhotoPrism 문서 기준 문법을
 * 썼지만, `type:`/`favorite:` 와 달리 이 앱에서 실서버로 검증한 적은 아직
 * 없다 — `ApiProbeRunner` 의 "검색 필터 문법 확인" 단계로 실서버 검증할 것.
 *
 * [hasLocation] 은 서버 쿼리가 아니다. PhotoPrism 에 "GPS 있는 것만" 같은
 * 문서화된 직접 필터가 없어서, 잘못 짚은 문법으로 조용히 빈 결과를 돌려주는
 * 위험을 피하려고 [com.regnius.photoprism.core.data.FeedKey.requireLocation]
 * 을 통해 클라이언트에서 페이지 결과를 걸러낸다.
 */
data class SearchFilters(
    val type: MediaType? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val camera: String? = null,
    val hasLocation: Boolean? = null,
) {
    val isActive: Boolean
        get() = type != null || dateFrom != null || dateTo != null || !camera.isNullOrBlank() || hasLocation != null

    /** 필터 버튼의 뱃지 숫자. 기간은 시작/종료를 합쳐 하나로 센다. */
    val activeCount: Int
        get() = listOfNotNull(
            type,
            (dateFrom ?: dateTo)?.let { Unit },
            camera?.takeIf { it.isNotBlank() },
            hasLocation,
        ).size

    /** 검색창의 자유 텍스트와 합쳐 PhotoPrism `q=` 파라미터를 만든다. */
    fun combinedQuery(freeText: String): String = buildList {
        if (freeText.isNotBlank()) add(freeText.trim())
        type?.let { add(it.searchClause()) }
        dateFrom?.let { add("after:${it.format(ISO_DATE)}") }
        dateTo?.let { add("before:${it.format(ISO_DATE)}") }
        camera?.takeIf { it.isNotBlank() }?.let { add("camera:\"${it.trim()}\"") }
    }.joinToString(" ")

    /** [MainShell] 와 [SearchBody] 가 같은 키를 만들도록 여기 한 곳에 모은다. */
    fun toFeedKey(freeText: String): FeedKey = FeedKey.search(combinedQuery(freeText), hasLocation)

    private fun MediaType.searchClause(): String = when (this) {
        MediaType.IMAGE -> PhotoQuery.IMAGES
        MediaType.VIDEO -> PhotoQuery.VIDEOS
        else -> "type:$apiValue"
    }

    companion object {
        val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        /** 필터 시트에서 고를 수 있는 타입 — 나머지(RAW/VECTOR/DOCUMENT)는 "사진"에 포함된다. */
        val TYPE_OPTIONS = listOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.LIVE, MediaType.ANIMATED)
    }
}
