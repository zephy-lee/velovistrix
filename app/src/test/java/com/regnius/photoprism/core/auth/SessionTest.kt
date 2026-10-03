package com.regnius.photoprism.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * public 모드는 accessToken 이 **없는 것이 정상**이다.
 * 이걸 토큰 유무로만 판단하면 public 서버 사용자가 로그인 화면에 갇힌다.
 */
class SessionTest {

    private fun session(
        token: String = "t",
        preview: String = "p",
        isPublic: Boolean = false,
    ) = Session(
        serverUrl = "https://example.com/",
        username = "demo",
        accessToken = token,
        previewToken = preview,
        downloadToken = "d",
        serverVersion = "260601-abc",
        isPublic = isPublic,
    )

    @Test
    fun `일반 세션은 토큰이 둘 다 있어야 사용 가능하다`() {
        assertTrue(session().isUsable)
        assertFalse(session(token = "").isUsable)
        assertFalse(session(preview = "").isUsable)
    }

    @Test
    fun `public 세션은 accessToken 이 없어도 사용 가능하다`() {
        assertTrue(session(token = "", isPublic = true).isUsable)
    }

    @Test
    fun `public 세션이라도 previewToken 이 없으면 사용 불가다`() {
        // previewToken 이 없으면 썸네일을 한 장도 못 띄운다. 목록만 나오고
        // 이미지가 전부 깨진 상태로 들여보내면 안 된다.
        assertFalse(session(token = "", preview = "", isPublic = true).isUsable)
    }
}
