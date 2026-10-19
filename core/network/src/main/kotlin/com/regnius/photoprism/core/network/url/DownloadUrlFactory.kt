package com.regnius.photoprism.core.network.url

import okhttp3.HttpUrl

/**
 * **원본 파일** 다운로드 URL (Phase 4.4~4.5).
 *
 * 썸네일([ThumbUrlFactory])과 다른 토큰을 쓴다 — 미리보기는 previewToken,
 * 다운로드는 downloadToken 이다. 둘은 서버에서 따로 회전되고, 다운로드 토큰은
 * 원본 전체를 내주는 값이라 더 민감하다.
 *
 * 토큰이 쿼리에 들어가므로 이 요청도 무상태 GET 이다. `AuthInterceptor` 가
 * `Authorization` 헤더를 덧붙이지만(경로가 `t`/`videos` 가 아니라서) 무해하다 —
 * 서버는 토큰과 세션 둘 중 하나만 맞아도 내준다.
 *
 * **`/zip` 은 쓰지 않는다.** 여러 장을 한 번에 받는 서버 기능이 있지만 그건
 * `POST` 다. 이 앱은 로그인 말고는 서버에 POST 를 보내지 않는다는 것이 읽기
 * 전용 방어선의 실제 내용이므로(§1 설계 원칙 1), 여러 장은 **한 장씩 GET** 해서
 * 받는다. 느린 대신 그 선을 지킨다.
 */
class DownloadUrlFactory(
    private val apiBase: HttpUrl,
    private val downloadToken: String,
) {
    fun original(fileHash: String): String =
        apiBase.newBuilder()
            .addPathSegment("dl")
            .addPathSegment(fileHash)
            .addQueryParameter("t", downloadToken)
            .build()
            .toString()
}
