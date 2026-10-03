package com.regnius.photoprism.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [com.regnius.photoprism.core.model.Album] 을 그대로 미러링한 캐시 행.
 *
 * 앨범은 페이징하지 않고 마지막 성공 조회를 통째로 저장/교체한다.
 * [orderIndex] 는 SQLite 행 순서에 기대지 않기 위해 마지막 성공 조회
 * 순서를 명시적으로 저장한다.
 */
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val uid: String,
    val title: String,
    val type: String,
    val description: String?,
    val photoCount: Int,
    val favorite: Boolean,
    val createdAt: String?,
    val updatedAt: String?,
    val coverHash: String?,
    val orderIndex: Int,
)
