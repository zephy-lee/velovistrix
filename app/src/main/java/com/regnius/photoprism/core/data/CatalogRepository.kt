package com.regnius.photoprism.core.data

import com.regnius.photoprism.core.data.local.dao.CatalogDao
import com.regnius.photoprism.core.data.local.toDomain
import com.regnius.photoprism.core.data.local.toEntity
import com.regnius.photoprism.core.model.CatalogItem
import com.regnius.photoprism.core.model.CatalogKind
import com.regnius.photoprism.core.network.PhotoPrismClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 라벨·인물 목록 (Phase 3.5~3.6).
 *
 * [AlbumRepository] 와 **같은 모양**이다 — 페이징하지 않고 한 번에 받아,
 * 성공한 조회만 Room 에 종류별로 교체 저장하고, 실패하면 Room 에 남은 마지막
 * 목록으로 폴백한다. 라벨·인물도 많아야 수백 개 수준이라 앨범과 같은 판단이
 * 그대로 성립한다.
 *
 * 목록 자체는 여기서 끝이고, 탭한 뒤의 사진들은 [PhotoFeedController] 가
 * [CatalogItem.query] 를 받아 처리한다 — 라벨·인물 전용 사진 경로는 없다.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val clientProvider: PhotoPrismClientProvider,
    private val catalogDao: CatalogDao,
) {
    private val cached = ConcurrentHashMap<CatalogKind, List<CatalogItem>>()

    suspend fun items(kind: CatalogKind, forceRefresh: Boolean = false): Result<List<CatalogItem>> {
        cached[kind]?.let { if (!forceRefresh) return Result.success(it) }
        val api = clientProvider.currentApi ?: return offlineFallback(kind)
        return runCatching {
            when (kind) {
                CatalogKind.LABEL ->
                    api.getLabels(count = FETCH_LIMIT).map { it.toDomain() }

                CatalogKind.PERSON ->
                    api.getSubjects(count = FETCH_LIMIT).map { it.toDomain() }
            }
                // 이름이 빈 항목은 걸러낸다. 인물 쪽에 실제로 들어온다 — 서버가
                // 얼굴 묶음을 먼저 만들고 사람이 이름을 붙이기를 기다리는
                // 상태가 있어서, 그대로 두면 이름 없는 타일이 줄줄이 생긴다.
                .filter { it.name.isNotBlank() }
        }.onSuccess { items ->
            cached[kind] = items
            withContext(Dispatchers.IO) {
                catalogDao.replaceAll(
                    kind = kind.storageKey,
                    items = items.mapIndexed { i, item -> item.toEntity(orderIndex = i) },
                )
            }
        }.recoverCatching { e ->
            offlineFallback(kind).getOrElse { throw e }
        }
    }

    /**
     * 네트워크를 건드리지 않고 캐시에 있는 것만 — 화면이 먼저 뭔가를 그릴 수
     * 있게 하는 길(§17.33 과 같은 이유). **메모리 캐시는 채우지 않는다.**
     */
    suspend fun cachedItems(kind: CatalogKind): List<CatalogItem> =
        withContext(Dispatchers.IO) { catalogDao.getAll(kind.storageKey).map { it.toDomain() } }

    private suspend fun offlineFallback(kind: CatalogKind): Result<List<CatalogItem>> {
        val local = withContext(Dispatchers.IO) {
            catalogDao.getAll(kind.storageKey).map { it.toDomain() }
        }
        return if (local.isNotEmpty()) {
            cached[kind] = local
            Result.success(local)
        } else {
            Result.failure(IllegalStateException("no session"))
        }
    }

    suspend fun clear() {
        cached.clear()
        withContext(Dispatchers.IO) { catalogDao.clearAll() }
    }

    private companion object {
        /** 앨범과 같은 상한. 이보다 많은 라벨을 가진 라이브러리는 드물다. */
        const val FETCH_LIMIT = 1000
    }
}
