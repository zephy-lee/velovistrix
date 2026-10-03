package com.regnius.photoprism.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * PhotoPrism 의 미디어 타입.
 *
 * §5.3 결정 — `LIVE` 와 `ANIMATED` 는 **이미지 탭**에 속한다. 사용자 인식상
 * 사진에 가깝고, 탭을 둘로 유지할 수 있기 때문이다.
 *
 * `VECTOR`(SVG)와 `DOCUMENT`(PDF)는 계획서 초안에 없던 타입인데, Phase 0.3
 * 실서버 실측에서 드러났다. 둘 다 PhotoPrism 이 래스터 미리보기 파일을 함께
 * 생성해두기 때문에 일반 썸네일 경로로 정상 표시된다. 따라서 이미지 탭에 넣는다.
 * 빼두면 앨범 개수와 탭에 보이는 장수가 어긋나서 사용자가 "사진이 사라졌다"고
 * 느끼게 된다.
 */
enum class MediaType(val apiValue: String) {
    IMAGE("image"),
    RAW("raw"),
    LIVE("live"),
    ANIMATED("animated"),
    VECTOR("vector"),
    DOCUMENT("document"),
    VIDEO("video"),
    UNKNOWN("");

    /** 앨범 상세의 어느 탭에 들어가는가. */
    val belongsToVideoTab: Boolean get() = this == VIDEO

    companion object {
        fun from(raw: String?): MediaType =
            entries.firstOrNull { it.apiValue.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * 썸네일 사이즈. §4.4 의 매핑 전략을 타입으로 고정한다.
 *
 * 문자열을 코드 곳곳에 흩뿌리면 "그리드에서 실수로 fit_1920 을 요청" 같은
 * 성능 사고가 조용히 생긴다. 사용처를 이름에 박아 그걸 막는다.
 */
enum class ThumbSize(val apiValue: String, val pixels: Int) {
    /**
     * 가장 촘촘한 그리드(가로 10칸 이상)용. 셀이 100px 안팎이라 `tile_224` 는
     * 픽셀이 4배 넘게 남는다 — 받는 바이트도 3배다(§17.34).
     */
    GRID_TINY("tile_100", 100),
    GRID_COMPACT("tile_224", 224),
    GRID_DENSE("tile_500", 500),
    ALBUM_COVER("tile_500", 500),
    VIEWER_PREVIEW("fit_1280", 1280),
    VIEWER_FULL("fit_1920", 1920),
    VIEWER_ZOOMED("fit_2560", 2560);

    override fun toString(): String = apiValue
}

@Immutable
@Serializable
data class MediaFile(
    val uid: String,
    val hash: String,
    val name: String,
    val mimeType: String?,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val isPrimary: Boolean,
    val isVideo: Boolean,
    val durationNanos: Long,
    /** 비디오 코덱(예: "avc1"). */
    val codec: String?,
    /** 비디오 프로파일(예: "High"). */
    val videoProfile: String?,
    /** 오디오 코덱(예: "aac"). */
    val audioCodec: String?,
    val fps: Double?,
    /**
     * 저장 루트. PhotoPrism 원본 파일은 보통 `"/"` — 이때 [name] 이 곧
     * `originals/` 밑의 상대 경로다. WebDAV 폴백(§17.1) URL을 만들 때만 쓴다.
     */
    val root: String?,
)

@Immutable
data class Photo(
    val uid: String,
    val title: String,
    val hash: String,
    val type: MediaType,
    val takenAtLocal: String?,
    val favorite: Boolean,
    val width: Int,
    val height: Int,
    val cameraMake: String?,
    val cameraModel: String?,
    val lens: String?,
    val iso: Int?,
    val fNumber: Float?,
    val exposure: String?,
    val latitude: Double?,
    val longitude: Double?,
    val files: List<MediaFile>,
) {
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height else 1f

    /**
     * 비디오 스트리밍/Live Photo 재생/WebDAV 폴백(§17.1)에 쓸 파일.
     *
     * 기본은 `Files` 배열에서 `Video: true` 인 파일이지만, 실측으로 확인된
     * 경우가 있다 — PhotoPrism 이 사진 레벨 `Type` 은 `"video"` 로 주면서도
     * 그 안의 어느 파일에도 `Video: true` 를 안 주는 항목이 존재한다
     * (서버의 `/api/v1/photos` 응답을 직접 덤프해 확인했다). 원래 로직대로면 이럴 때
     * 재생을 시도조차 안 하고 조용히 정지 사진으로 떨어진다 — 웹
     * 포토프리즘은 파일 레벨 플래그가 아니라 사진 레벨 `Type` 을 믿기
     * 때문에 같은 파일이 거기서는 잘 재생된다.
     *
     * 그래서 `type == VIDEO` 인데 파일 레벨 플래그로 못 찾으면, primary
     * 파일(없으면 첫 파일)로 재생을 **시도는 해본다.** 진짜 재생 불가능한
     * 파일이면 기존 에러 UI/WebDAV 폴백이 그대로 이어받아 처리하므로 무해한
     * 폴백이다 — 지금처럼 아무 시도도 안 하는 것보다는 낫다. [videoHash] 와
     * 뷰어의 WebDAV 폴백용 파일 조회가 둘 다 이 프로퍼티 하나를 쓰도록
     * 모아뒀다 — 따로 두면 한쪽만 폴백을 타는 불일치가 생긴다.
     */
    val videoFile: MediaFile?
        get() = files.firstOrNull { it.isVideo }
            ?: (files.firstOrNull { it.isPrimary } ?: files.firstOrNull())
                ?.takeIf { type == MediaType.VIDEO }

    val videoHash: String? get() = videoFile?.hash?.takeIf { it.isNotBlank() }

    /** 검색의 "위치 유무" 필터(Phase 2.2)가 쓴다. */
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

@Immutable
data class Album(
    val uid: String,
    val title: String,
    val type: String,
    val description: String?,
    val photoCount: Int,
    val favorite: Boolean,
    val createdAt: String?,
    /**
     * 마지막 수정 시각(ISO 8601). "최신 업데이트순" 정렬의 기준.
     * 문자열째로 사전식 비교해도 시간순이 맞는다 (ISO 8601 의 특성).
     */
    val updatedAt: String?,
    /**
     * 커버 사진의 **파일 해시**.
     *
     * PhotoPrism 에는 `/albums/{uid}/t/{token}/{size}` 라는 전용 커버
     * 엔드포인트가 있지만, 실측해 보니 이 서버는 거기서 실제 이미지가 아니라
     * 252 바이트짜리 **플레이스홀더 SVG** 를 HTTP 200 으로 돌려준다.
     * 상태 코드가 200 이라 실패로도 안 잡히고, Coil 은 SVG 디코더 없이는
     * 아무것도 못 그려서 커버가 조용히 빈 칸이 된다.
     *
     * 반면 앨범 응답의 `Thumb` 필드에는 커버 사진의 파일 해시가 그대로 들어
     * 있고, 이걸 일반 썸네일 경로에 넣으면 정상 JPEG 가 온다. 그래서 커버도
     * 사진과 **같은 경로**로 받는다 — 캐시 정책도 하나로 통일된다.
     */
    val coverHash: String?,
)
