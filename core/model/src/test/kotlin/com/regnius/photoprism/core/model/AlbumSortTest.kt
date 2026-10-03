package com.regnius.photoprism.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumSortTest {

    private fun album(
        title: String,
        favorite: Boolean = false,
        createdAt: String = "2024-01-01T00:00:00Z",
        updatedAt: String = "2024-01-01T00:00:00Z",
    ) = Album(
        uid = title, title = title, type = "album", description = null,
        photoCount = 0, favorite = favorite, createdAt = createdAt,
        updatedAt = updatedAt, coverHash = null,
    )

    private val albums = listOf(
        album("banana", createdAt = "2024-03-01T00:00:00Z", updatedAt = "2024-01-01T00:00:00Z"),
        album("Apple", createdAt = "2024-01-01T00:00:00Z", updatedAt = "2024-03-01T00:00:00Z"),
        album("cherry", createdAt = "2024-02-01T00:00:00Z", updatedAt = "2024-02-01T00:00:00Z"),
    )

    @Test
    fun `이름순은 대소문자를 구분하지 않는다`() {
        // "banana" 를 아스키 그대로 비교하면 대문자 "Apple" 보다 뒤로 밀린다 —
        // 사용자가 기대하는 사전식 순서와 다르다.
        val sorted = albums.sortedForDisplay(AlbumSort.NAME_ASC, favoritesFirst = false)
        assertEquals(listOf("Apple", "banana", "cherry"), sorted.map { it.title })
    }

    @Test
    fun `이름 역순`() {
        // PhotoPrism 서버 자체는 내림차순 이름 정렬을 지원하지 않아
        // (2026-08-28 실측) 클라이언트에서 뒤집는다.
        val sorted = albums.sortedForDisplay(AlbumSort.NAME_DESC, favoritesFirst = false)
        assertEquals(listOf("cherry", "banana", "Apple"), sorted.map { it.title })
    }

    @Test
    fun `최신 업데이트순은 UpdatedAt 내림차순이다`() {
        val sorted = albums.sortedForDisplay(AlbumSort.RECENTLY_UPDATED, favoritesFirst = false)
        assertEquals(listOf("Apple", "cherry", "banana"), sorted.map { it.title })
    }

    @Test
    fun `오래된순은 CreatedAt 오름차순이다`() {
        val sorted = albums.sortedForDisplay(AlbumSort.OLDEST, favoritesFirst = false)
        assertEquals(listOf("Apple", "cherry", "banana"), sorted.map { it.title })
    }

    @Test
    fun `즐겨찾기 먼저는 그룹 안에서는 기존 정렬을 유지한다`() {
        // 안정 정렬(stable sort) 이라 즐겨찾기 그룹 안에서 이름순이 그대로 유지돼야 한다.
        val withFavorites = listOf(
            album("Zebra", favorite = true),
            album("Apple", favorite = false),
            album("Mango", favorite = true),
        )
        val sorted = withFavorites.sortedForDisplay(AlbumSort.NAME_ASC, favoritesFirst = true)
        // 즐겨찾기(Mango, Zebra — 이름순 유지) 먼저, 그다음 비즐겨찾기(Apple).
        assertEquals(listOf("Mango", "Zebra", "Apple"), sorted.map { it.title })
    }

    @Test
    fun `즐겨찾기 우선을 껐으면 정렬 기준만 적용된다`() {
        val withFavorites = listOf(album("Zebra", favorite = true), album("Apple", favorite = false))
        val sorted = withFavorites.sortedForDisplay(AlbumSort.NAME_ASC, favoritesFirst = false)
        assertEquals(listOf("Apple", "Zebra"), sorted.map { it.title })
    }

    @Test
    fun `날짜가 없는 앨범은 빈 문자열로 취급해 정렬 끝쪽에 놓인다`() {
        val mixed = listOf(
            album("A", createdAt = "2024-06-01T00:00:00Z"),
            album("B", createdAt = ""),
        )
        val oldest = mixed.sortedForDisplay(AlbumSort.OLDEST, favoritesFirst = false)
        assertEquals("B", oldest.first().title)
    }
}
