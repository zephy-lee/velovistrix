package com.regnius.photoprism.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.regnius.photoprism.core.data.local.entity.AlbumEntity

/**
 * 앨범 캐시.
 *
 * 모든 조회·교체가 `listType` 로 **격리**된다(Phase 3.1~3.4). 달력·폴더·
 * 지역·나의 순간이 같은 테이블을 쓰면서도 서로를 지우지 않게 하는 지점이다 —
 * 격리가 없으면 달력을 한 번 열었을 때 `replaceAll` 이 앨범 목록까지 날린다.
 * 사진 쪽에서 `FeedKey.cacheKey()` 로 푼 것과 같은 문제, 같은 해법이다.
 */
@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums WHERE listType = :listType ORDER BY orderIndex ASC")
    suspend fun getAll(listType: String): List<AlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE listType = :listType")
    suspend fun clear(listType: String)

    /** 로그아웃·서버 전환용. 타입을 가리지 않고 전부 비운다. */
    @Query("DELETE FROM albums")
    suspend fun clearAll()

    /**
     * 성공한 조회 결과로만 그 타입을 통째로 교체한다 — 실패 시엔 호출되지
     * 않아 이전 캐시가 남는다(오프라인 폴백의 근거).
     */
    @Transaction
    suspend fun replaceAll(listType: String, albums: List<AlbumEntity>) {
        clear(listType)
        insertAll(albums)
    }
}
