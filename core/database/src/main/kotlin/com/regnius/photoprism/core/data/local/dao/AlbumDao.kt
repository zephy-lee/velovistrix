package com.regnius.photoprism.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.regnius.photoprism.core.data.local.entity.AlbumEntity

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY orderIndex ASC")
    suspend fun getAll(): List<AlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums")
    suspend fun clear()

    /** 성공한 조회 결과로만 통째로 교체한다 — 실패 시엔 호출되지 않아 이전 캐시가 남는다. */
    @Transaction
    suspend fun replaceAll(albums: List<AlbumEntity>) {
        clear()
        insertAll(albums)
    }
}
