package com.regnius.photoprism.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [com.regnius.photoprism.core.model.Photo] 를 그대로 미러링한 캐시 행 —
 * `uid` 기준 전역 하나뿐이라, 타임라인·앨범·즐겨찾기·검색이 같은 사진을
 * 가리키면 행 하나를 공유한다. 어떤 피드에 어떤 순서로 속하는지는
 * [PhotoFeedEntryEntity] 가 따로 기록한다.
 *
 * `files` 는 [filesJson] 으로 JSON 직렬화해 저장한다 — 사진당 1~3개뿐이고
 * 항상 사진과 1:1로 통째로 읽고 쓰며, 파일 필드만 단독으로 쿼리하는 화면이
 * 없어서 별도 엔티티+관계로 나눌 이득이 없다.
 */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val uid: String,
    val title: String,
    val hash: String,
    val type: String,
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
    val filesJson: String,
)
