package com.regnius.photoprism.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.regnius.photoprism.core.data.local.entity.PhotoFeedEntryEntity

@Dao
interface PhotoFeedDao {

    @Query("DELETE FROM photo_feed_entries WHERE feedKey IN (:feedKeys)")
    suspend fun clearFeeds(feedKeys: List<String>)

    /**
     * 경계 중복 UID 는 여기서 조용히 무시된다 — PK(`feedKey`,`photoUid`) +
     * IGNORE 라 두 번째 삽입이 버려지고 첫 삽입의 `sortIndex` 가 유지된다.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntries(entries: List<PhotoFeedEntryEntity>)

    @Query("SELECT MAX(sortIndex) FROM photo_feed_entries WHERE feedKey = :feedKey")
    suspend fun maxSortIndex(feedKey: String): Int?

    @Query("SELECT photoUid FROM photo_feed_entries WHERE feedKey = :feedKey ORDER BY sortIndex ASC")
    suspend fun uidsForFeed(feedKey: String): List<String>

    /**
     * §17.18 — [com.regnius.photoprism.core.data.PhotoFeedLocalPagingSource]
     * 가 placeholder(`itemsBefore`/`itemsAfter`) 계산에 쓴다. 로컬에 이미
     * 얼마나 캐싱돼 있는지 정확히 아는 값이라, 앱을 재시작해 새 세대가
     * 처음부터 다시 시작해도 `LazyPagingItems.itemCount` 가 곧바로 이
     * 값을 반영한다 — `initialLoadSize`(120) 만큼만 읽혔다고 해서 그
     * 숫자로 보이는 게 아니라, 이미 캐싱된 진짜 개수가 즉시 보인다.
     */
    @Query("SELECT COUNT(*) FROM photo_feed_entries WHERE feedKey = :feedKey")
    suspend fun countForFeed(feedKey: String): Int

    @Query("DELETE FROM photo_feed_entries")
    suspend fun deleteAllEntries()
}
