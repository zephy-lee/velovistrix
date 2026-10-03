package com.regnius.photoprism.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.regnius.photoprism.core.data.local.dao.AlbumDao
import com.regnius.photoprism.core.data.local.dao.FeedRemoteKeyDao
import com.regnius.photoprism.core.data.local.dao.PhotoDao
import com.regnius.photoprism.core.data.local.dao.PhotoFeedDao
import com.regnius.photoprism.core.data.local.entity.AlbumEntity
import com.regnius.photoprism.core.data.local.entity.FeedRemoteKeyEntity
import com.regnius.photoprism.core.data.local.entity.PhotoEntity
import com.regnius.photoprism.core.data.local.entity.PhotoFeedEntryEntity

/**
 * 목록/메타데이터 오프라인 캐시 전용 DB (Phase 2.9). 썸네일은 여기 없다 —
 * 이미 있는 Coil 디스크 캐시(LRU, best-effort)에 그대로 맡긴다.
 *
 * pre-1.0 앱이고 순수 캐시(서버가 진실 소스)라 마이그레이션을 쓰지 않는다
 * — 스키마가 바뀌면 통째로 지우고 다음 REFRESH 가 다시 채운다.
 *
 * `version = 2`(§17.16) — [FeedRemoteKeyEntity.forceRefresh] 컬럼 추가.
 * 겸사겸사, §17.11~17.15 를 고치는 동안 이 기기에 쌓여있던(당겨서
 * 새로고침이 `nextOffset` 을 되돌려 실제 로컬 데이터량과 어긋나 있던)
 * 오염된 캐시도 이 버전업이 통째로 지워서 정리해준다.
 */
@Database(
    entities = [
        PhotoEntity::class,
        PhotoFeedEntryEntity::class,
        FeedRemoteKeyEntity::class,
        AlbumEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(MediaFileListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun photoFeedDao(): PhotoFeedDao
    abstract fun feedRemoteKeyDao(): FeedRemoteKeyDao
    abstract fun albumDao(): AlbumDao

    companion object {
        /**
         * [com.regnius.photoprism.core.di.RoomModule] 이 DB 를 여는 파일명과
         * 설정 화면의 "오프라인 보관용 캐시" 용량 표시(§17.20, `Context.getDatabasePath`)
         * 가 같은 이름을 봐야 하므로 여기 한 곳에 둔다.
         */
        const val DB_NAME = "photoprism_cache.db"
    }
}
