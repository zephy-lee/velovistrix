package com.regnius.photoprism.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.regnius.photoprism.core.data.local.entity.PhotoEntity

@Dao
interface PhotoDao {

    /**
     * 피드 하나의 한 창(window)을 오프셋 기준으로 읽는다.
     *
     * **Room 자동 생성 `PagingSource`(§17.14)를 다시 걷어냈다(§17.15)** —
     * 이유는 그때와 정반대다. Room 의 `InvalidationTracker` 는 테이블
     * 단위라, `photos`/`photo_feed_entries` 를 관찰하는 모든 피드의
     * `PagingSource` 가 **다른 피드의 쓰기에도** 같이 무효화된다. 앨범
     * 상세는 이미지·동영상 두 탭을 [androidx.compose.foundation.pager.HorizontalPager]
     * 로 동시에 살려 두므로(`beyondViewportPageCount = 1`), 이미지 탭을
     * 스크롤해 RemoteMediator 가 APPEND 를 쓸 때마다 화면에 보이지도 않는
     * 동영상 탭의 `PagingSource` 까지 매번 새 세대로 재생성됐다 — 실기
     * 증상: 이미지 개수가 120→500→다시 180 처럼 튀고, 동영상은 총
     * 155장인데 정확히 `initialLoadSize`(120)에서 고정돼 절대 안 늘다가
     * 그 탭을 직접 열어야만 마저 받아졌다(재생성될 때마다 창이 초기
     * 크기로 다시 잘려서, 화면 밖이라 아무도 더 읽어달라고 요청하지
     * 않으니 딱 그 크기에 멈춘 것).
     *
     * 그래서 [com.regnius.photoprism.core.data.PhotoFeedLocalPagingSource]
     * 를 다시 두되, 이번엔 §17.12 처럼 완전히 비반응형이 아니라 **자기
     * 피드 키로만 범위를 좁힌 수동 무효화**를 쓴다 —
     * `InvalidatingPagingSourceFactory` 를 통해 [com.regnius.photoprism.core.data.PhotoFeedRemoteMediator]
     * 가 **자기 자신의 피드에 실제로 새 행을 쓴 직후에만** 명시적으로
     * `invalidate()` 를 호출한다. 다른 피드의 쓰기는 이 피드의 소스를
     * 전혀 건드리지 않고, §17.14 가 증명한 "성공한 APPEND 뒤엔 로컬
     * 소스가 반드시 무효화돼야 한다"는 전제는 그대로 지킨다.
     */
    @Query(
        """
        SELECT photos.* FROM photos
        INNER JOIN photo_feed_entries ON photos.uid = photo_feed_entries.photoUid
        WHERE photo_feed_entries.feedKey = :feedKey
        ORDER BY photo_feed_entries.sortIndex ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun page(feedKey: String, limit: Int, offset: Int): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM photos")
    suspend fun countAll(): Int

    /** 어떤 피드도 더 이상 가리키지 않는 사진 행을 정리한다. */
    @Query("DELETE FROM photos WHERE uid NOT IN (SELECT DISTINCT photoUid FROM photo_feed_entries)")
    suspend fun deleteOrphans()
}
