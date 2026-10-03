package com.regnius.photoprism.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.regnius.photoprism.core.data.local.entity.FeedRemoteKeyEntity

@Dao
interface FeedRemoteKeyDao {

    @Query("SELECT * FROM feed_remote_keys WHERE feedKey = :feedKey")
    suspend fun get(feedKey: String): FeedRemoteKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(key: FeedRemoteKeyEntity)

    @Query("DELETE FROM feed_remote_keys")
    suspend fun deleteAll()

    @Query("DELETE FROM feed_remote_keys WHERE feedKey IN (:feedKeys)")
    suspend fun deleteKeys(feedKeys: List<String>)

    /**
     * 당겨서 새로고침 신호. 행 자체는 지우지 않는다 — [FeedRemoteKeyEntity.forceRefresh]
     * 문서 참고. 이 피드를 아직 한 번도 안 열어 행이 없으면 조용히 no-op —
     * 그런 피드는 [com.regnius.photoprism.core.data.PhotoFeedRemoteMediator.initialize]
     * 가 어차피 처음부터 LAUNCH_INITIAL_REFRESH 한다.
     */
    @Query("UPDATE feed_remote_keys SET forceRefresh = 1 WHERE feedKey = :feedKey")
    suspend fun markForceRefresh(feedKey: String)

    @Query("SELECT COUNT(*) FROM feed_remote_keys")
    suspend fun feedCount(): Int

    /** [com.regnius.photoprism.core.data.local.CachePolicy] 의 LRU 정리용. */
    @Query("SELECT feedKey FROM feed_remote_keys ORDER BY lastRefreshedAt ASC LIMIT :n")
    suspend fun leastRecentlyUsedFeeds(n: Int): List<String>
}
