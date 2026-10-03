package com.regnius.photoprism.core.network

import android.util.Log
import com.regnius.photoprism.core.auth.SessionSource
import com.regnius.photoprism.core.network.dto.SessionRequestDto
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 401 을 받으면 저장된 자격증명으로 **한 번만** 재로그인하고 원 요청을 재시도한다.
 *
 * PhotoPrism 의 세션 토큰은 만료된다. 이게 없으면 앱을 오래 켜둔 뒤 스크롤할 때
 * 썸네일이 우수수 깨지고 사용자는 이유를 모른다.
 *
 * 무한 루프 방지가 이 클래스의 핵심이다:
 *  - [responseCount] 로 재시도 횟수를 세어 1회로 제한한다.
 *  - 재로그인 자체가 401 이면 세션을 지우고 null 을 반환해 로그인 화면으로 보낸다.
 *
 * OkHttp 의 Authenticator 는 동기 API 라 [runBlocking] 이 불가피하다.
 * OkHttp 가 이 콜백을 자체 백그라운드 스레드에서 호출하므로 메인 스레드를
 * 막지는 않는다.
 *
 * [clientProvider] 를 [Provider] 로 받는 이유는 의존성 순환 때문이다:
 * OkHttpClient → SessionAuthenticator → PhotoPrismClientProvider → OkHttpClient.
 * Provider 는 주입 시점을 실제 사용 시점까지 미뤄 이 고리를 끊는다.
 */
@Singleton
class SessionAuthenticator @Inject constructor(
    private val sessionStore: SessionSource,
    private val clientProvider: Provider<PhotoPrismClientProvider>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // public 모드 서버에서 401 이 왔다면 자격증명이 없어서가 아니라 서버
        // 설정이 바뀐 것이다. 빈 자격증명으로 재로그인을 시도해봐야 실패만
        // 반복하므로 즉시 포기하고 로그인 화면으로 보낸다.
        if (sessionStore.session.value?.isPublic == true) {
            Log.w(TAG, "public-mode server returned 401 — its configuration appears to have changed")
            sessionStore.clear()
            return null
        }

        if (response.responseCount > 1) {
            Log.w(TAG, "still 401 after re-authenticating — clearing the session")
            sessionStore.clear()
            return null
        }

        val credentials = sessionStore.storedCredentials() ?: run {
            sessionStore.clear()
            return null
        }
        val api = clientProvider.get().currentApi ?: return null

        val newToken = runCatching {
            runBlocking {
                api.createSession(
                    SessionRequestDto(credentials.username, credentials.secret)
                ).bearerToken
            }
        }.getOrElse { error ->
            Log.w(TAG, "re-authentication failed", error)
            null
        }

        if (newToken.isNullOrBlank()) {
            sessionStore.clear()
            return null
        }

        sessionStore.updateAccessToken(newToken)
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private val Response.responseCount: Int
        get() {
            var count = 1
            var prior = priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }

    private companion object {
        const val TAG = "SessionAuthenticator"
    }
}
