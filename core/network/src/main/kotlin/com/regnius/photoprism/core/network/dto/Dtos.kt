@file:Suppress("PropertyName")

package com.regnius.photoprism.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * PhotoPrism 응답 DTO.
 *
 * 설계 규칙 — **모든 필드에 기본값을 준다.**
 * PhotoPrism 은 릴리스마다 필드를 추가/제거하고, 같은 엔드포인트라도 설정에 따라
 * 일부 필드를 생략한다. 필수 필드로 선언해두면 서버가 한 칸 바꿀 때마다 앱이
 * 파싱 예외로 죽는다. `ignoreUnknownKeys = true` 와 함께, 모르는 건 무시하고
 * 없는 건 기본값으로 넘어가도록 만든다.
 *
 * 실제 필드 구성은 Phase 0.3 진단 화면(실서버 실측)으로 확인한 뒤 조정한다.
 */

@Serializable
data class SessionRequestDto(
    val username: String,
    val password: String,
)

@Serializable
data class SessionResponseDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val id: String? = null,
    val status: String? = null,
    val config: ClientConfigDto? = null,
    val user: UserDto? = null,
) {
    /**
     * 실제로 Bearer 헤더에 실을 토큰.
     *
     * 260601+ 서버는 `access_token` 을 준다. `id` 는 구버전의 세션 ID 인데,
     * 응답 형식이 과도기인 빌드를 대비해 fallback 으로만 둔다. 이 값이 쓰이는
     * 상황은 지원 범위 밖이므로, 실제로 쓰이면 진단 화면이 경고한다.
     */
    val bearerToken: String? get() = accessToken ?: id
}

@Serializable
data class StatusDto(
    val status: String? = null,
)

@Serializable
data class UserDto(
    @SerialName("UID") val uid: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("DisplayName") val displayName: String = "",
)

@Serializable
data class ClientConfigDto(
    val version: String? = null,
    val name: String? = null,
    val mode: String? = null,
    val previewToken: String? = null,
    val downloadToken: String? = null,
    val public: Boolean = false,
    val readonly: Boolean = false,
    val experimental: Boolean = false,
)

@Serializable
data class FileDto(
    @SerialName("UID") val uid: String = "",
    @SerialName("PhotoUID") val photoUid: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Hash") val hash: String = "",
    @SerialName("Mime") val mime: String? = null,
    @SerialName("Width") val width: Int = 0,
    @SerialName("Height") val height: Int = 0,
    @SerialName("Size") val size: Long = 0,
    @SerialName("Primary") val primary: Boolean = false,
    @SerialName("Video") val video: Boolean = false,
    @SerialName("Duration") val duration: Long = 0,
    @SerialName("Root") val root: String? = null,
    // 정보 시트의 "비디오 코덱" 표시용 (§17.4).
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Profile") val profile: String? = null,
    @SerialName("AudioCodec") val audioCodec: String? = null,
    @SerialName("FPS") val fps: Double? = null,
)

/**
 * 카메라/렌즈 이름 — 목록 조회(`GET /photos`)와 단건 조회(`GET /photos/{uid}`)가
 * **다른 모양**으로 준다(실서버 데모, 260904 확인). 목록은 [PhotoDto.cameraMake]/
 * [PhotoDto.cameraModel] 처럼 평평한 필드로 주고, 단건 조회는 그 평평한
 * 필드가 아예 없는 대신 `"Camera": {"Make": ..., "Model": ...}` 처럼 중첩
 * 객체로 준다 — 렌즈도 마찬가지로 `"Lens": {"Model": ...}`. [PhotoDto.toDomain]
 * 에서 두 모양을 전부 확인해야, 뷰어 정보 시트가 상세 조회 후에 카메라/렌즈
 * 정보가 사라지지 않는다.
 */
@Serializable
data class CameraDto(
    @SerialName("Make") val make: String? = null,
    @SerialName("Model") val model: String? = null,
)

@Serializable
data class LensDto(
    @SerialName("Model") val model: String? = null,
)

@Serializable
data class PhotoDto(
    @SerialName("UID") val uid: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("Hash") val hash: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("TakenAt") val takenAt: String? = null,
    @SerialName("TakenAtLocal") val takenAtLocal: String? = null,
    @SerialName("Favorite") val favorite: Boolean = false,
    @SerialName("Private") val private: Boolean = false,
    @SerialName("Width") val width: Int = 0,
    @SerialName("Height") val height: Int = 0,
    // 목록 조회 모양(평평한 필드) — 위 클래스 문서 참고.
    @SerialName("CameraMake") val cameraMake: String? = null,
    @SerialName("CameraModel") val cameraModel: String? = null,
    @SerialName("LensModel") val lensModel: String? = null,
    // 단건 조회 모양(중첩 객체) — 위 클래스 문서 참고.
    @SerialName("Camera") val camera: CameraDto? = null,
    @SerialName("Lens") val lens: LensDto? = null,
    @SerialName("Iso") val iso: Int? = null,
    @SerialName("FNumber") val fNumber: Float? = null,
    @SerialName("Exposure") val exposure: String? = null,
    @SerialName("FocalLength") val focalLength: Int? = null,
    @SerialName("Lat") val lat: Double? = null,
    @SerialName("Lng") val lng: Double? = null,
    @SerialName("Files") val files: List<FileDto> = emptyList(),
)

@Serializable
data class AlbumDto(
    @SerialName("UID") val uid: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("Type") val type: String = "album",
    @SerialName("Slug") val slug: String = "",
    @SerialName("Description") val description: String? = null,
    @SerialName("PhotoCount") val photoCount: Int = 0,
    @SerialName("Favorite") val favorite: Boolean = false,
    @SerialName("CreatedAt") val createdAt: String? = null,
    @SerialName("UpdatedAt") val updatedAt: String? = null,
    @SerialName("Thumb") val thumb: String? = null,
)
