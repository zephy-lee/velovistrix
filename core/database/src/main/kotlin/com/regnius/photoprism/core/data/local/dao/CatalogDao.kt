package com.regnius.photoprism.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.regnius.photoprism.core.data.local.entity.CatalogItemEntity

/**
 * 라벨·인물 캐시 (Phase 3.5~3.6).
 *
 * [AlbumDao] 와 같은 규칙이다 — 조회·교체가 종류별로 **격리**되고,
 * [replaceAll] 은 성공한 응답으로만 불린다(실패 시 이전 캐시가 남아 오프라인
 * 에서도 목록이 보인다).
 */
@Dao
interface CatalogDao {

    @Query("SELECT * FROM catalog_items WHERE kind = :kind ORDER BY orderIndex ASC")
    suspend fun getAll(kind: String): List<CatalogItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CatalogItemEntity>)

    @Query("DELETE FROM catalog_items WHERE kind = :kind")
    suspend fun clear(kind: String)

    /** 로그아웃·서버 전환용. */
    @Query("DELETE FROM catalog_items")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(kind: String, items: List<CatalogItemEntity>) {
        clear(kind)
        insertAll(items)
    }
}
