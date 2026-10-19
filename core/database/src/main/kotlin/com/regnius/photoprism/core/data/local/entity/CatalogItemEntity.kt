package com.regnius.photoprism.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 라벨·인물 목록의 캐시 행 (Phase 3.5~3.6).
 *
 * **테이블 하나에 둘을 같이 담는다.** 두 응답에서 우리가 쓰는 필드가
 * 완전히 같아서(이름·슬러그·장수·대표 사진), 테이블을 둘로 가르면 같은 모양의
 * 엔티티·DAO·매퍼가 두 벌 생기고 그 둘이 따로 썩는다.
 * [com.regnius.photoprism.core.model.CatalogItem] 과 같은 판단이다.
 *
 * 대신 [kind] 를 PK 의 일부로 둔다. 라벨 UID 와 인물 UID 가 충돌할 일은
 * 없어 보이지만, 그걸 **가정**하면 서버가 UID 체계를 바꾼 날 조용히
 * 한쪽이 다른 쪽을 덮어쓴다. 복합 PK 면 그 사고가 구조적으로 불가능하다.
 *
 * 앞으로 한쪽에만 필요한 필드(라벨의 우선순위, 인물의 제외 여부 등)가
 * 늘어나 빈 칸이 반씩 생기기 시작하면, 그때 테이블을 가르는 게 맞다.
 */
@Entity(
    tableName = "catalog_items",
    primaryKeys = ["kind", "uid"],
    indices = [Index("kind")],
)
data class CatalogItemEntity(
    /** [com.regnius.photoprism.core.model.CatalogKind.storageKey] */
    val kind: String,
    val uid: String,
    val name: String,
    val slug: String,
    val photoCount: Int,
    val coverHash: String?,
    /** 마지막 성공 조회의 순서. SQLite 행 순서에 기대지 않는다. */
    val orderIndex: Int,
)
