package com.regnius.photoprism.core.data.local

import com.regnius.photoprism.core.data.local.entity.AlbumEntity
import com.regnius.photoprism.core.data.local.entity.PhotoEntity
import com.regnius.photoprism.core.model.Album
import com.regnius.photoprism.core.model.MediaFile
import com.regnius.photoprism.core.model.MediaType
import com.regnius.photoprism.core.model.Photo
import kotlinx.serialization.json.Json

/**
 * 도메인 모델 ↔ Room 엔티티 매핑.
 *
 * [com.regnius.photoprism.core.data.Mappers] (DTO → 도메인)와 대칭되는
 * 별도 경계다 — 캐시 스키마 변경이 네트워크 DTO 나 UI 까지 번지지 않게 한다.
 */

fun Photo.toEntity() = PhotoEntity(
    uid = uid,
    title = title,
    hash = hash,
    type = type.apiValue,
    takenAtLocal = takenAtLocal,
    favorite = favorite,
    width = width,
    height = height,
    cameraMake = cameraMake,
    cameraModel = cameraModel,
    lens = lens,
    iso = iso,
    fNumber = fNumber,
    exposure = exposure,
    latitude = latitude,
    longitude = longitude,
    filesJson = Json.encodeToString(files),
)

fun PhotoEntity.toDomain() = Photo(
    uid = uid,
    title = title,
    hash = hash,
    type = MediaType.from(type),
    takenAtLocal = takenAtLocal,
    favorite = favorite,
    width = width,
    height = height,
    cameraMake = cameraMake,
    cameraModel = cameraModel,
    lens = lens,
    iso = iso,
    fNumber = fNumber,
    exposure = exposure,
    latitude = latitude,
    longitude = longitude,
    files = Json.decodeFromString<List<MediaFile>>(filesJson),
)

fun Album.toEntity(orderIndex: Int) = AlbumEntity(
    uid = uid,
    title = title,
    type = type,
    description = description,
    photoCount = photoCount,
    favorite = favorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    coverHash = coverHash,
    orderIndex = orderIndex,
)

fun AlbumEntity.toDomain() = Album(
    uid = uid,
    title = title,
    type = type,
    description = description,
    photoCount = photoCount,
    favorite = favorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    coverHash = coverHash,
)
