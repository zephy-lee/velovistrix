package com.regnius.photoprism.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 라벨·인물이 만드는 검색 쿼리 (Phase 3.5~3.6).
 *
 * 이 문자열이 틀리면 목록은 멀쩡히 뜨는데 탭할 때마다 빈 그리드가 나온다 —
 * 서버는 모르는 필터를 오류가 아니라 **0건**으로 돌려주기 때문에
 * (PhotoQuery 주석 참고) 조용히 깨지는 부류다.
 */
class CatalogItemTest {

    private fun label(name: String, slug: String, uid: String = "lbl1") =
        CatalogItem(CatalogKind.LABEL, uid, name, slug, photoCount = 3, coverHash = null)

    private fun person(name: String, uid: String) =
        CatalogItem(CatalogKind.PERSON, uid, name, slug = "slug", photoCount = 3, coverHash = null)

    @Test
    fun `라벨은 슬러그로 검색한다`() {
        assertEquals("label:cat", label("Cat", "cat").query)
    }

    @Test
    fun `사람은 이름이 아니라 UID 로 검색한다`() {
        // 이름으로 걸면 공백 때문에 따옴표가 필요하고, 동명이인이 한 목록으로
        // 섞인다. UID 는 둘 다 없다.
        assertEquals("subject:srj2k3", person("Jane Doe", "srj2k3").query)
    }

    @Test
    fun `슬러그가 비면 이름으로라도 건다`() {
        // 서버가 슬러그를 안 준 라벨은 눌러도 아무것도 안 나오는 타일이 된다.
        // 이름이 슬러그와 같은 경우가 대부분이라 그걸로라도 시도하는 편이 낫다.
        assertEquals("label:Sunset", label("Sunset", "").query)
    }
}
