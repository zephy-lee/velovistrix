package com.regnius.photoprism.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * "어떤 사진이 어떤 피드에 어떤 순서로 속하는가" 를 기록하는 조인 테이블.
 *
 * [PhotoEntity] 는 사진당 하나뿐이라, 서로 다른 [feedKey](타임라인 · 특정
 * 앨범의 이미지/동영상 탭 · 즐겨찾기 · 검색어)가 같은 사진을 가리켜도
 * 충돌하지 않고 각자의 순서(`sortIndex`)만 별도로 갖는다.
 */
@Entity(
    tableName = "photo_feed_entries",
    primaryKeys = ["feedKey", "photoUid"],
    indices = [Index("feedKey"), Index("photoUid")],
)
data class PhotoFeedEntryEntity(
    val feedKey: String,
    val photoUid: String,
    val sortIndex: Int,
)
