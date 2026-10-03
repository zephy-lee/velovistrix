package com.regnius.photoprism.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 피드 하나(=하나의 [com.regnius.photoprism.core.data.FeedKey]) 당 한 행.
 *
 * 이 API 의 페이징 키는 항목별이 아니라 피드 전체에 걸친 단일 `offset` 이라,
 * 구글 페이징 코드랩 표준의 "항목별 remote keys 테이블" 은 과하다 — 피드당
 * 1행이면 [RemoteMediator][androidx.paging.RemoteMediator] 의 APPEND 가
 * 이어받을 지점을 알기에 충분하다.
 *
 * [lastRefreshedAt] 은 [com.regnius.photoprism.core.data.local.CachePolicy]
 * 의 LRU 정리 시계도 겸한다.
 *
 * [forceRefresh] — §17.16. 당겨서 새로고침이 이 행을 통째로 지우던 때
 * (§ 예전 [com.regnius.photoprism.core.data.PhotoFeedController.invalidateFeed])
 * 는, 이미 훨씬 뒤까지 진행돼 있던 [nextOffset] 이 다음 REFRESH 에서
 * `0+requested` 로 통째로 되돌아가 버렸다 — `photo_feed_entries` 는 그대로
 * 수백 장이 남아있는데 네트워크 진행률만 처음으로 리셋되면서, 이후 APPEND 가
 * 이미 로컬에 있는 구간을 다시 훑어 가끔 겹치지 않는 몇 장을 발견할 때마다
 * 무효화 → 화면 숫자가 훅 줄었다 다시 느는 게 반복됐다. 이제는 행을 지우지
 * 않고 이 플래그만 세운다 — REFRESH 가 "맨 앞부터 새로 확인은 하되
 * [nextOffset]/[endOfList] 는 절대 뒤로 되돌리지 않는다"는 신호로 쓴다.
 */
@Entity(tableName = "feed_remote_keys")
data class FeedRemoteKeyEntity(
    @PrimaryKey val feedKey: String,
    val nextOffset: Int?,
    val endOfList: Boolean,
    val lastRefreshedAt: Long,
    val forceRefresh: Boolean = false,
)
