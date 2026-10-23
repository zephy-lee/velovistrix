package com.regnius.photoprism.feature.viewer

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

private const val LOG_TAG = "ViewerClipCache"

/**
 * §22 — 라이브 사진·GIF 클립을 **뷰어를 보는 동안만** 들고 있는 캐시.
 *
 * 둘 다 무음으로 끝없이 반복하는데, 캐시가 없으면 ExoPlayer 가 한 바퀴마다
 * 네트워크에서 처음부터 다시 읽는다. 라이브 클립 하나가 5MB 쯤이라 반복마다
 * 5MB 를 다시 받았고, 느린 서버에서는 그 사이 영상이 몇 초씩 멈췄다(§21.2).
 *
 * 오프라인 캐시 원칙("동영상 본문은 저장하지 않는다")과 부딪히지 않도록
 * 수명을 뷰어 하나에 묶는다 — 뷰어에 들어갈 때 [open], 나갈 때 [release] 가
 * 캐시 폴더를 통째로 지운다. 프로세스가 죽어 [release] 를 못 탄 폴더는 다음
 * [open] 이 치운다. 일반 동영상은 이 캐시를 쓰지 않는다 — 크기가 커서 "보는
 * 동안" 이라도 저장소를 크게 차지할 수 있다.
 */
@UnstableApi
class ViewerClipCache private constructor(
    private val dir: File,
    private val cache: SimpleCache,
) {
    /** [upstream] 에서 받은 바이트를 캐시에 쓰고, 다음 바퀴부터는 캐시에서 읽는다. */
    fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun release() {
        // release() 는 색인을 디스크에 쓰고 delete() 는 파일을 지운다 — 둘 다
        // 디스크 작업이라 메인 스레드 밖에서 한다.
        thread(name = LOG_TAG) {
            try {
                cache.release()
                SimpleCache.delete(dir, databaseProvider(null))
            } catch (e: Exception) {
                Log.w(LOG_TAG, "failed to delete $dir", e)
            } finally {
                synchronized(active) { active.remove(dir) }
            }
        }
    }

    companion object {
        private const val ROOT_DIR = "viewer-clips"

        /** 라이브 클립 수십 개가 들어가는 크기. 넘치면 오래 안 본 것부터 버린다. */
        private const val MAX_BYTES = 200L * 1024 * 1024

        /** 지금 열려 있는 캐시 폴더 — 잔여물 정리가 이것들은 건드리지 않는다. */
        private val active = mutableSetOf<File>()

        @Volatile
        private var provider: StandaloneDatabaseProvider? = null

        private fun databaseProvider(context: Context?): StandaloneDatabaseProvider =
            provider ?: synchronized(this) {
                provider ?: StandaloneDatabaseProvider(context!!.applicationContext).also { provider = it }
            }

        fun open(context: Context): ViewerClipCache {
            val db = databaseProvider(context)
            val root = File(context.cacheDir, ROOT_DIR)
            val dir = File(root, UUID.randomUUID().toString())
            synchronized(active) { active += dir }
            val cache = SimpleCache(dir, LeastRecentlyUsedCacheEvictor(MAX_BYTES), db)

            thread(name = LOG_TAG) {
                val leftovers = root.listFiles().orEmpty().filter { file ->
                    synchronized(active) { file !in active }
                }
                leftovers.forEach { stale ->
                    try {
                        SimpleCache.delete(stale, db)
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "failed to delete leftover $stale", e)
                    }
                }
            }
            return ViewerClipCache(dir, cache)
        }
    }
}
