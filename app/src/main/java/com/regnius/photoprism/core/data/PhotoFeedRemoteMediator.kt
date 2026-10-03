package com.regnius.photoprism.core.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.regnius.photoprism.core.data.local.AppDatabase
import com.regnius.photoprism.core.data.local.CachePolicy
import com.regnius.photoprism.core.data.local.entity.FeedRemoteKeyEntity
import com.regnius.photoprism.core.data.local.entity.PhotoFeedEntryEntity
import com.regnius.photoprism.core.data.local.entity.PhotoEntity
import com.regnius.photoprism.core.data.local.toEntity
import com.regnius.photoprism.core.network.PhotoPrismApi
import java.io.IOException
import retrofit2.HttpException

/**
 * PhotoPrism 사진 검색을 Room 뒤로 옮긴 RemoteMediator.
 *
 * `PhotoPagingSource`(구, 삭제됨)가 갖고 있던 세 가지 네트워크 페칭 규칙을
 * 그대로 옮겼다 — `merged=true` 때문에 서버 응답을 그대로 믿을 수 없는
 * 지점들이다.
 *
 * **1. 끝 판정을 개수로 하면 안 된다.** 서버는 요청보다 항상 적게 돌려준다
 * (실측: count=120 → 107건). `x-count`(실제 스캔 행 수) < `x-limit`(요청
 * 개수) 로 판정한다.
 *
 * **2. offset 은 병합 전 행 기준으로 전진시킨다.** `offset + 받은개수` 로
 * 전진하면 병합으로 줄어든 만큼 뒤로 밀려 이미 본 사진을 다시 불러온다.
 * `offset + 요청개수` 가 맞다.
 *
 * **3. 경계 중복은 Room 의 PK + IGNORE 로 자연스럽게 걸러진다** — 예전의
 * 수동 `emittedUids` 셋이 하던 일을 [PhotoFeedEntryEntity] 의 복합 PK가
 * 대신한다.
 *
 * **오프라인 보장의 핵심**: 네트워크 호출이 성공한 뒤에만 [AppDatabase.withTransaction]
 * 이 실행된다. `IOException`/HTTP 오류는 그 전에 `catch`/`return`되므로
 * 트랜잭션 자체가 안 돈다 — REFRESH 가 오프라인으로 실패해도 이전 세션이
 * 써둔 [PhotoFeedEntryEntity] 행은 그대로 남고, Paging 3 가 관찰하는 로컬
 * [androidx.paging.PagingSource]([com.regnius.photoprism.core.data.local.dao.PhotoDao.pagingSource],
 * Room 자동 생성)는 마지막으로 캐싱된 목록을 그대로 보여준다.
 *
 * **로컬 PagingSource 는 반드시 반응형이어야 한다 — 하지만 자기 피드
 * 범위로만 좁힌 반응형이어야 한다(§17.12→§17.14→§17.15).** Paging 3
 * 소스(`PageFetcherSnapshot`/`RemoteMediatorAccessor`, `paging-common`
 * 3.5.1)를 직접 확인한 결과, `RemoteMediator.load()` 가 성공해도 로컬
 * PagingSource 를 다시 읽으라고 알려주는 경로가 **PagingSource 자신의
 * 무효화 말고는 없다** — 그래서 §17.12 의 완전 비반응형 버전은 REFRESH 로
 * 처음 받은 페이지가 요청보다 적게 오면(`merged=true` 때문에 항상 그렇다)
 * 그 지점에서 다음 페이지를 영원히 안 가져왔다(§17.14).
 *
 * §17.14 에서 Room 자동 생성 반응형 `PagingSource` 로 되돌렸지만, 그
 * 반응형은 **테이블 단위**였다(Room `InvalidationTracker`). 앨범 상세는
 * 이미지·동영상 두 탭을 `HorizontalPager(beyondViewportPageCount = 1)`
 * 로 동시에 살려 두므로, 두 탭 모두 `photos`/`photo_feed_entries` 를
 * 관찰한다 — 이미지 탭을 스크롤해 이 클래스의 APPEND 가 쓸 때마다 화면에
 * 보이지도 않는 동영상 탭의 PagingSource 까지 매번 새 세대로
 * 재생성됐다(§17.15 실기 증상: 이미지 개수가 120→500→다시 180 처럼
 * 튀고, 동영상은 총 155장인데 정확히 `initialLoadSize`(120)에서 고정돼
 * 절대 안 늘다가 그 탭을 직접 열어야만 마저 받아졌다 — 재생성될 때마다
 * 창이 초기 크기로 잘리고, 화면 밖이라 아무도 더 읽어달라고 요청하지
 * 않으니 딱 그 크기에 멈춘 것이었다).
 *
 * 그래서 로컬 소스를 다시 [PhotoFeedLocalPagingSource] 로 바꾸되(§17.12
 * 때와 달리 이번엔 반응형이다), Room 의 자동 무효화 대신
 * `InvalidatingPagingSourceFactory` 를 통해 **자기 자신의 피드에 실제로
 * 새 행을 쓴 직후에만** [invalidate] 를 호출한다([PhotoDao.page] 문서
 * 참고). 다른 피드의 쓰기는 이 소스를 전혀 건드리지 않는다.
 *
 * REFRESH 도착 시 이미 이 피드의 [FeedRemoteKeyEntity] 가 있으면 —
 * "새로 여는 피드"가 아니라 "이미 로드된 피드가 다시 REFRESH 된 것"으로
 * 보고 DB 를 건드리지 않고 그대로 성공 처리한다(무의미한 네트워크 재요청을
 * 막는 가드).
 *
 * **REFRESH 는 절대 기존 데이터를 지우지 않는다** — 예전엔 REFRESH 가
 * `clearFeed()` 로 `sortIndex` 를 0부터 다시 매겼는데, 자기무효화로 인해
 * 이게 수시로 일어나다 보니 APPEND 로 쌓은 걸 REFRESH 가 도로 지워버리는
 * 무한 루프가 됐다(§17.11). 그래서 REFRESH 도 APPEND 와 똑같이
 * "이어붙이기"만 한다(`OnConflictStrategy.IGNORE` 라 겹치는 사진은
 * 조용히 무시되고 기존 `sortIndex` 가 유지된다) — 유일한 손해는, 당겨서
 * 새로고침(2.10) 직후에도 아주 오래된 캐시 항목이 새로 받은 항목보다
 * 먼저 나올 수 있다는 것 뿐이고, 그 정도는 "전체를 못 불러오거나 앱이
 * 죽는" 것보다 훨씬 낫다.
 *
 * **당겨서 새로고침도 nextOffset/endOfList 진행률을 지우지 않는다(§17.16)**
 * — 예전엔 [PhotoFeedController.invalidateFeed] 가 이 피드의
 * [FeedRemoteKeyEntity] 행을 통째로 지워서 "진짜 새로고침"을 신호했는데,
 * 그러면 `photo_feed_entries` 는 그대로 수백 장 남아있는데 네트워크
 * `nextOffset` 만 처음(0)으로 리셋됐다. 이후 APPEND 가 이미 로컬에 다
 * 있는 구간을 offset 0 부터 다시 훑으며, `merged=true` 의 병합 경계가
 * 매번 살짝 달라 가끔 겹치지 않는 몇 장을 발견할 때마다
 * [invalidate] 가 불려 화면에 보이는 개수가 확 줄었다 다시 느는 게
 * 반복됐다(실기 로그로 확인: `nextOffset` 이 480 근처에서 60씩 계속
 * 전진하는데 로컬엔 이미 850장 넘게 있었다). 이제
 * [PhotoFeedController.invalidateFeed] 는 행을 지우지 않고
 * [FeedRemoteKeyEntity.forceRefresh] 만 세운다 — REFRESH 는 이 플래그를
 * 보고 맨 앞부터 다시 훑되, DB 에 쓰는 `nextOffset`/`endOfList` 는 이미
 * APPEND 로 가 있던 값보다 뒤로 가지 않도록 [maxOf] 로 방어한다.
 */
@OptIn(ExperimentalPagingApi::class)
class PhotoFeedRemoteMediator(
    private val feedKey: FeedKey,
    private val feedKeyString: String,
    private val api: PhotoPrismApi,
    private val db: AppDatabase,
    /** 이 피드에 실제로 새 행이 쓰인 직후에만 호출된다 — 클래스 문서 참고. */
    private val invalidate: () -> Unit,
) : RemoteMediator<Int, PhotoEntity>() {

    /**
     * 이미 이 피드를 캐싱해둔 적이 있으면(=remote key 존재) 네트워크를
     * 아예 안 거치고 로컬 Room 데이터를 곧바로 보여준다 — "캐싱돼 있었으면
     * 바로 다 보여줘야 하는거 아니냐"는 요구사항 그대로다. 최초 진입(remote
     * key 없음)일 때만 네트워크에서 채운다.
     */
    override suspend fun initialize(): InitializeAction =
        if (db.feedRemoteKeyDao().get(feedKeyString) != null) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PhotoEntity>,
    ): MediatorResult {
        return try {
            // §12.1 — 첫 화면은 initialLoadSize(=pageSize 의 2배)로 한 번에
            // 채운다. RemoteMediator 는 PagingSource 와 달리 LoadParams 를
            // 안 받고 PagingState 만 받아서, REFRESH 도 명시적으로
            // initialLoadSize 를 챙기지 않으면 항상 pageSize 만큼만
            // 받아 첫 화면이 두 번에 나눠 채워진다.
            val requested = if (loadType == LoadType.REFRESH) {
                state.config.initialLoadSize
            } else {
                state.config.pageSize
            }
            val existingKey = db.feedRemoteKeyDao().get(feedKeyString)
            val offset = when (loadType) {
                LoadType.REFRESH -> {
                    if (existingKey != null && !existingKey.forceRefresh) {
                        // 자기무효화 — 위 클래스 문서 참고. 이미 쌓아둔 데이터를
                        // 지우지 않고 있는 그대로 성공 처리한다.
                        return MediatorResult.Success(endOfPaginationReached = existingKey.endOfList)
                    }
                    // existingKey == null(첫 로드) 이거나 forceRefresh(당겨서
                    // 새로고침)면 맨 앞부터 다시 훑는다 — nextOffset 을 어떻게
                    // 저장할지는 아래 트랜잭션에서 별도로 처리(§17.16).
                    0
                }
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val key = existingKey ?: return MediatorResult.Success(endOfPaginationReached = true)
                    if (key.endOfList) return MediatorResult.Success(endOfPaginationReached = true)
                    key.nextOffset ?: return MediatorResult.Success(endOfPaginationReached = true)
                }
            }

            val response = api.searchPhotosPaged(
                count = requested,
                offset = offset,
                scope = feedKey.albumUid,
                query = feedKey.query,
                order = feedKey.order,
                merged = true,
            )
            if (!response.isSuccessful) {
                // Response 로 받으면 4xx/5xx 가 예외를 던지지 않는다. 명시적으로
                // 확인하지 않으면 오류 응답이 "빈 페이지 = 끝" 으로 둔갑한다.
                return MediatorResult.Error(
                    IllegalStateException("HTTP ${response.code()} ${response.message()}")
                )
            }
            val dtos = response.body().orEmpty()
            val scanned = response.headers()["x-count"]?.toIntOrNull()
            val limit = response.headers()["x-limit"]?.toIntOrNull() ?: requested
            val endReached = when {
                scanned != null -> scanned < limit
                else -> dtos.isEmpty()
            }
            val photos = dtos.map { it.toDomain() }

            val freshNextOffset = if (endReached) null else offset + requested
            // §17.16 — 당겨서 새로고침(forceRefresh)의 REFRESH 는 맨 앞부터
            // 다시 훑어보되, 이미 APPEND 로 더 멀리 가 있던 nextOffset/endOfList
            // 진행률을 절대 되돌리지 않는다. 이 값을 DB 에 쓰는 것과 이
            // 호출의 반환값(endOfPaginationReached) 둘 다 여기서 계산한
            // 값을 그대로 써야 한다 — 안 그러면 "저장은 안 끝났다고 했는데
            // 반환은 끝났다고 하는" 모순이 생긴다. 클래스 문서 ·
            // FeedRemoteKeyEntity.forceRefresh 문서 참고.
            val (nextOffsetToStore, endOfListToStore) =
                if (loadType == LoadType.REFRESH && existingKey?.forceRefresh == true) {
                    when {
                        existingKey.endOfList -> null to true
                        freshNextOffset == null -> existingKey.nextOffset to false
                        else -> maxOf(freshNextOffset, existingKey.nextOffset ?: 0) to false
                    }
                } else {
                    freshNextOffset to endReached
                }

            db.withTransaction {
                // REFRESH 도 APPEND 와 동일하게 이어붙이기만 한다 — 위 클래스
                // 문서의 "REFRESH 는 절대 기존 데이터를 지우지 않는다" 참고.
                val baseIndex = (db.photoFeedDao().maxSortIndex(feedKeyString) ?: -1) + 1
                db.photoDao().upsertAll(photos.map { it.toEntity() })
                db.photoFeedDao().insertEntries(
                    photos.mapIndexed { i, p -> PhotoFeedEntryEntity(feedKeyString, p.uid, baseIndex + i) }
                )
                db.feedRemoteKeyDao().upsert(
                    FeedRemoteKeyEntity(
                        feedKey = feedKeyString,
                        nextOffset = nextOffsetToStore,
                        endOfList = endOfListToStore,
                        lastRefreshedAt = System.currentTimeMillis(),
                        forceRefresh = false,
                    )
                )
                if (loadType == LoadType.REFRESH) CachePolicy.enforceCaps(db)
            }
            // 이 피드에 실제로 새 행을 쓴 경우에만 무효화한다 — 빈 응답이면
            // 로컬 소스가 다시 읽어도 달라질 게 없다(클래스 문서 참고).
            if (photos.isNotEmpty()) invalidate()
            MediatorResult.Success(endOfPaginationReached = endOfListToStore)
        } catch (e: IOException) {
            MediatorResult.Error(e)
        } catch (e: HttpException) {
            MediatorResult.Error(e)
        }
    }
}
