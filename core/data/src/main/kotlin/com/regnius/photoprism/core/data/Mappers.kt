package com.regnius.photoprism.core.data

import com.regnius.photoprism.core.model.Album
import com.regnius.photoprism.core.model.MediaFile
import com.regnius.photoprism.core.model.MediaType
import com.regnius.photoprism.core.model.Photo
import com.regnius.photoprism.core.network.dto.AlbumDto
import com.regnius.photoprism.core.network.dto.FileDto
import com.regnius.photoprism.core.network.dto.PhotoDto

/**
 * DTO → 도메인 모델.
 *
 * 이 경계를 두는 이유는 PhotoPrism 의 필드 변화가 UI 까지 번지지 않게 하기
 * 위해서다. 서버가 `Iso` 를 `ISO` 로 바꾸면 [PhotoDto] 한 곳만 고치면 된다.
 */

fun FileDto.toDomain() = MediaFile(
    uid = uid,
    hash = hash,
    name = name,
    mimeType = mime,
    width = width,
    height = height,
    sizeBytes = size,
    isPrimary = primary,
    isVideo = video,
    durationNanos = duration,
    codec = codec?.takeIf { it.isNotBlank() },
    videoProfile = profile?.takeIf { it.isNotBlank() },
    audioCodec = audioCodec?.takeIf { it.isNotBlank() },
    fps = fps?.takeIf { it > 0.0 },
    root = root,
)

fun PhotoDto.toDomain(): Photo {
    val mapped = files.map { it.toDomain() }
    // 그리드에 쓸 해시는 primary 파일 기준이다. RAW 만 있는 항목에서 primary 가
    // 비어 있는 경우가 있어 photo 의 Hash 로 폴백한다.
    val displayHash = mapped.firstOrNull { it.isPrimary && !it.isVideo }?.hash
        ?: mapped.firstOrNull { !it.isVideo }?.hash
        ?: hash
    return Photo(
        uid = uid,
        title = title,
        hash = displayHash,
        type = MediaType.from(type),
        takenAtLocal = takenAtLocal ?: takenAt,
        favorite = favorite,
        width = width,
        height = height,
        // 목록 조회는 평평한 필드로, 단건 조회는 중첩 객체로 준다 — 둘 다 확인한다
        // (PhotoDto/CameraDto/LensDto 문서 참고, 실서버 확인됨).
        cameraMake = (cameraMake ?: camera?.make)?.takeIf { it.isNotBlank() },
        cameraModel = (cameraModel ?: camera?.model)?.takeIf { it.isNotBlank() },
        lens = (lensModel ?: lens?.model)?.takeIf { it.isNotBlank() },
        iso = iso?.takeIf { it > 0 },
        fNumber = fNumber?.takeIf { it > 0f },
        exposure = exposure?.takeIf { it.isNotBlank() },
        // PhotoPrism 은 위치가 없을 때 0,0 을 준다. 그대로 두면 기니 만 앞바다에
        // 찍힌 사진이 되므로 없는 것으로 취급한다.
        latitude = lat?.takeIf { it != 0.0 },
        longitude = lng?.takeIf { it != 0.0 },
        files = mapped,
    )
}

fun AlbumDto.toDomain() = Album(
    uid = uid,
    // 제목이 비어 있으면 그대로 둔다 — 사람이 읽을 대체 문구는 번역이
    // 필요해서 화면 쪽에서 붙인다(이 모듈은 순수 JVM 이라 리소스가 없다).
    title = title,
    type = type,
    description = description?.takeIf { it.isNotBlank() },
    photoCount = photoCount,
    favorite = favorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    coverHash = thumb?.takeIf { it.isNotBlank() },
)
