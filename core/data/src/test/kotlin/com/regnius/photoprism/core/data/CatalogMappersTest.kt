package com.regnius.photoprism.core.data

import com.regnius.photoprism.core.model.CatalogKind
import com.regnius.photoprism.core.network.dto.LabelDto
import com.regnius.photoprism.core.network.dto.SubjectDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 라벨·인물 DTO → 도메인 (Phase 3.5~3.6). */
class CatalogMappersTest {

    @Test
    fun `이름을 고친 라벨은 커스텀 슬러그로 검색한다`() {
        val item = LabelDto(uid = "l1", name = "Dog", slug = "canine", customSlug = "dog").toDomain()

        // 커스텀을 무시하고 원래 슬러그를 쓰면, 이름을 고친 라벨을 눌렀을 때
        // 빈 그리드가 나온다.
        assertEquals("dog", item.slug)
        assertEquals("label:dog", item.query)
    }

    @Test
    fun `커스텀 슬러그가 없으면 기본 슬러그를 쓴다`() {
        val item = LabelDto(uid = "l1", name = "Dog", slug = "dog", customSlug = "").toDomain()
        assertEquals("dog", item.slug)
    }

    @Test
    fun `인물은 사진 수가 없으면 파일 수로 대신한다`() {
        val item = SubjectDto(uid = "s1", name = "Jane", photoCount = 0, fileCount = 7).toDomain()

        assertEquals(CatalogKind.PERSON, item.kind)
        assertEquals(7, item.photoCount)
    }

    @Test
    fun `대표 사진 해시가 빈 문자열이면 없는 것으로 본다`() {
        // 빈 문자열을 그대로 두면 썸네일 URL 이 `/t//tile_500` 으로 만들어져
        // 404 를 부르고, 목록이 깨진 이미지로 가득 찬다.
        assertNull(LabelDto(uid = "l1", name = "Dog", thumb = "").toDomain().coverHash)
        assertNull(SubjectDto(uid = "s1", name = "Jane", thumb = "").toDomain().coverHash)
    }
}
