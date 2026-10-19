package com.regnius.photoprism.core.ui

import com.regnius.photoprism.core.model.MediaType
import com.regnius.photoprism.core.model.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 그리드 선택 모드 (Phase 4.3). */
class PhotoSelectionTest {

    private fun photo(uid: String) = Photo(
        uid = uid, title = uid, hash = "h-$uid", type = MediaType.IMAGE,
        takenAtLocal = null, favorite = false, width = 1, height = 1,
        cameraMake = null, cameraModel = null, lens = null, iso = null,
        fNumber = null, exposure = null, latitude = null, longitude = null,
        files = emptyList(),
    )

    @Test
    fun `아무것도 안 고르면 선택 모드가 아니다`() {
        // active 가 곧 "선택 바를 띄울지" 다 — 비어 있는데 켜져 있으면
        // 아무것도 할 수 없는 바가 화면을 가린다.
        assertFalse(PhotoSelection().active)
    }

    @Test
    fun `같은 사진을 다시 누르면 빠진다`() {
        val selection = PhotoSelection()
        selection.toggle(photo("a"))
        selection.toggle(photo("b"))
        selection.toggle(photo("a"))

        assertEquals(listOf("b"), selection.photos.map { it.uid })
        assertFalse(selection.isSelected("a"))
        assertTrue(selection.isSelected("b"))
    }

    @Test
    fun `고른 순서를 유지한다`() {
        // 저장·공유가 고른 순서대로 처리되는 편이 목록 순서로 재배열되는
        // 것보다 예측에 맞다.
        val selection = PhotoSelection()
        listOf("c", "a", "b").forEach { selection.toggle(photo(it)) }

        assertEquals(listOf("c", "a", "b"), selection.photos.map { it.uid })
        assertEquals(3, selection.count)
    }

    @Test
    fun `마지막 하나를 빼면 선택 모드가 끝난다`() {
        val selection = PhotoSelection()
        selection.toggle(photo("a"))
        selection.toggle(photo("a"))

        assertFalse(selection.active)
    }

    @Test
    fun `clear 는 전부 비운다`() {
        val selection = PhotoSelection()
        listOf("a", "b", "c").forEach { selection.toggle(photo(it)) }
        selection.clear()

        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }
}
