package com.regnius.photoprism.core.data.local

/**
 * 오프라인 캐시 저장공간 관리.
 *
 * WorkManager 같은 예약 작업이 아니다 — REFRESH 성공 트랜잭션 끝에서만
 * 기회주의적으로 돈다("방금 화면을 열었다/당겨서 새로고침했다"는 실제
 * 사용자 행동에 얹힘). §4.6 가드레일: WorkManager 는 Phase 4 의 "명시적
 * 오프라인 보관" 전용으로 남겨둔다.
 *
 * 정리 단위는 항상 **피드 전체**다 — 피드 안 사진 몇 장만 지우면 순서에
 * 구멍이 남는다.
 */
object CachePolicy {
    /** 메모리 슬롯(10개, [com.regnius.photoprism.core.data.PhotoFeedController.MAX_CACHED_KEYS])
     *  보다 넉넉하게 — 한 세션에서 실제로 열어보는 서로 다른 FeedKey 개수가
     *  이보다 많기는 어렵다. */
    const val MAX_CACHED_FEEDS = 24

    /** 만 장 단위 앨범 두 벌 분량. photos 는 uid 로 전역 중복 제거되므로
     *  실제 저장량은 이보다 작다(행당 대략 1~1.5KB, 2만 행 ≈ 20~30MB). */
    const val MAX_CACHED_PHOTOS = 20_000

    suspend fun enforceCaps(db: AppDatabase) {
        val feedCount = db.feedRemoteKeyDao().feedCount()
        if (feedCount > MAX_CACHED_FEEDS) {
            val stale = db.feedRemoteKeyDao().leastRecentlyUsedFeeds(feedCount - MAX_CACHED_FEEDS)
            db.photoFeedDao().clearFeeds(stale)
            db.feedRemoteKeyDao().deleteKeys(stale)
        }
        db.photoDao().deleteOrphans()

        // 방금 갱신한 피드는 lastRefreshedAt 이 항상 최신이라 자기 자신을
        // 지우는 일은 구조적으로 불가능하다.
        while (db.photoDao().countAll() > MAX_CACHED_PHOTOS) {
            val victim = db.feedRemoteKeyDao().leastRecentlyUsedFeeds(1).firstOrNull() ?: break
            db.photoFeedDao().clearFeeds(listOf(victim))
            db.feedRemoteKeyDao().deleteKeys(listOf(victim))
            db.photoDao().deleteOrphans()
        }
    }
}
