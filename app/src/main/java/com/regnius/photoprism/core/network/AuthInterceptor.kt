package com.regnius.photoprism.core.network

import com.regnius.photoprism.core.auth.SessionSource
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 모든 API 요청에 `Authorization: Bearer <access_token>` 을 붙인다. §4.1
 *
 * 지원 하한을 260601 로 잡았기 때문에 구버전의 `X-Auth-Token` 폴백이나
 * 버전 감지 분기는 없다. 헤더가 하나뿐이라 이 클래스가 이만큼 짧다.
 *
 * 예외 두 곳:
 *  - `POST /session` : 아직 토큰이 없다.
 *  - `GET /config`   : 로그인 전 프로브에도 쓰인다 (인증 없이도 응답한다).
 * 두 경우 토큰이 있으면 붙이고, 없으면 그냥 통과시킨다.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionSource,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // 썸네일/비디오 URL 은 경로에 previewToken 이 들어있어 헤더가 필요 없다 (§4.2).
        // 헤더를 붙여도 무해하지만, 붙이지 않아야 이 요청이 무상태 GET 으로 남아
        // 캐시·프리페치 경로에서 인증 상태에 의존하지 않는다.
        if (request.isTokenizedMediaRequest()) return chain.proceed(request)

        val token = sessionStore.session.value?.accessToken
        if (token.isNullOrBlank()) return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )
    }

    private fun okhttp3.Request.isTokenizedMediaRequest(): Boolean {
        val segments = url.pathSegments
        val i = segments.indexOf("v1")
        if (i < 0 || i + 1 >= segments.size) return false
        return segments[i + 1] == "t" || segments[i + 1] == "videos"
    }
}
