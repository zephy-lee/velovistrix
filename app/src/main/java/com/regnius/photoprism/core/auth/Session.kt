package com.regnius.photoprism.core.auth

/**
 * 서버 접속에 필요한 상태 전부.
 *
 * [accessToken] 과 [previewToken] / [downloadToken] 은 **다른 물건이다** (§4.2).
 *  - accessToken  : API 요청의 `Authorization: Bearer` 헤더에 쓴다.
 *  - previewToken : 썸네일/비디오 URL 경로에 박아 넣는다. 헤더가 아니다.
 *  - downloadToken: 원본 다운로드 쿼리 파라미터.
 *
 * 셋을 한 타입에 모아두는 이유는, 실제로 이걸 헷갈려서 "썸네일만 401" 이 나는
 * 사고가 흔하기 때문이다.
 */
data class Session(
    val serverUrl: String,
    val username: String,
    val accessToken: String,
    val previewToken: String,
    val downloadToken: String,
    val serverVersion: String?,
    /**
     * public 모드 서버인가.
     *
     * PhotoPrism 은 인증 없이 열람 가능한 public 모드로 운영할 수 있고, 그때
     * `/config` 가 `public: true` 와 `previewToken: "public"` 을 돌려준다.
     * 이 경우 [accessToken] 이 비어 있는 것이 **정상**이므로, 토큰이 없다고
     * 로그인 화면으로 되돌리면 안 된다. [isUsable] 이 이 둘을 구분한다.
     */
    val isPublic: Boolean = false,
) {
    /**
     * 이 세션으로 서버를 쓸 수 있는가.
     *
     * previewToken 은 어느 모드든 반드시 있어야 한다 — 없으면 썸네일을 한 장도
     * 못 띄운다. accessToken 은 public 모드에서만 없어도 된다.
     */
    val isUsable: Boolean
        get() = previewToken.isNotBlank() && (isPublic || accessToken.isNotBlank())
}
