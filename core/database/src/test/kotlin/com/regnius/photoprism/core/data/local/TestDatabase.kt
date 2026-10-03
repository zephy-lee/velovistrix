package com.regnius.photoprism.core.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.regnius.photoprism.core.data.local.entity.AlbumEntity
import com.regnius.photoprism.core.data.local.entity.FeedRemoteKeyEntity
import com.regnius.photoprism.core.data.local.entity.PhotoEntity
import com.regnius.photoprism.core.data.local.entity.PhotoFeedEntryEntity
import kotlinx.coroutines.Dispatchers

/**
 * 인메모리 DB — 안드로이드도 Robolectric 도 없이 순수 JVM 에서 연다.
 *
 * §17.10 이 막혀 있던 지점이 정확히 여기다. `:app`(단일 안드로이드 모듈) 안에서는
 * Room 이 android 타깃으로 컴파일돼 `RoomDatabase` 가 `Looper.getMainLooper()` 를
 * 부르고, 로컬 유닛테스트의 안드로이드 스텁은 거기서 null 을 돌려줘 NPE 가 났다.
 * 이 모듈은 `kotlin("jvm")` 이라 `room-runtime-jvm` 이 선택되고, 그 구현은
 * `Looper` 를 아예 모른다.
 */
internal fun createTestDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

internal fun photo(uid: String, hash: String = "h-$uid") = PhotoEntity(
    uid = uid,
    title = uid,
    hash = hash,
    type = "image",
    takenAtLocal = "2026-01-01T00:00:00Z",
    favorite = false,
    width = 100,
    height = 100,
    cameraMake = null,
    cameraModel = null,
    lens = null,
    iso = null,
    fNumber = null,
    exposure = null,
    latitude = null,
    longitude = null,
    filesJson = "[]",
)

internal fun album(uid: String, order: Int) = AlbumEntity(
    uid = uid,
    title = "album-$uid",
    type = "album",
    description = null,
    photoCount = 0,
    favorite = false,
    createdAt = null,
    updatedAt = null,
    coverHash = null,
    orderIndex = order,
)

internal fun feedKey(name: String, lastRefreshedAt: Long) = FeedRemoteKeyEntity(
    feedKey = name,
    nextOffset = 0,
    endOfList = false,
    lastRefreshedAt = lastRefreshedAt,
)

internal fun entries(feed: String, uids: List<String>) =
    uids.mapIndexed { i, uid -> PhotoFeedEntryEntity(feedKey = feed, photoUid = uid, sortIndex = i) }

/** 피드 하나를 사진 [count] 장으로 채운다. */
internal suspend fun AppDatabase.seedFeed(feed: String, count: Int, lastRefreshedAt: Long, uidPrefix: String = feed) {
    val uids = (0 until count).map { "$uidPrefix-$it" }
    photoDao().upsertAll(uids.map { photo(it) })
    photoFeedDao().insertEntries(entries(feed, uids))
    feedRemoteKeyDao().upsert(feedKey(feed, lastRefreshedAt))
}
