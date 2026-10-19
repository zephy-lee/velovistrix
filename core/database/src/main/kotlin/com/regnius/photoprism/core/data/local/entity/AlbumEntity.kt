package com.regnius.photoprism.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [com.regnius.photoprism.core.model.Album] 을 그대로 미러링한 캐시 행.
 *
 * 앨범은 페이징하지 않고 마지막 성공 조회를 통째로 저장/교체한다.
 * [orderIndex] 는 SQLite 행 순서에 기대지 않기 위해 마지막 성공 조회
 * 순서를 명시적으로 저장한다.
 *
 * [listType] 은 **어느 목록에서 받은 행인지**다(Phase 3.1~3.4). [type] 과
 * 보통 같지만 따로 둔다 — 캐시를 타입별로 지우고 다시 채울 때 기준이 되는
 * 값이라, 서버가 `Type` 을 비워 보내거나 요청한 것과 다른 값을 돌려주면
 * **그 타입의 캐시를 영영 못 찾는다.** [type] 은 서버가 말한 것,
 * [listType] 은 우리가 물어본 것이다.
 */
@Entity(
    tableName = "albums",
    indices = [Index("listType")],
)
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
    val listType: String,
)
