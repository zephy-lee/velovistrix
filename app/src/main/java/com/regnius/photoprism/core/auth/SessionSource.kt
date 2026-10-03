package com.regnius.photoprism.core.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * 네트워크 계층이 세션에 대해 알아야 하는 최소한.
 *
 * [SessionStore] 는 Android Keystore 와 Context 에 묶여 있어 JVM 단위 테스트에서
 * 쓸 수 없다. 인터셉터와 Authenticator 는 저장 방식을 알 필요가 없으므로,
 * 그들이 실제로 쓰는 네 가지만 인터페이스로 떼어낸다. 덕분에 인증 헤더 부착과
 * 401 재인증 같은 **틀리면 조용히 아픈 로직**을 테스트로 고정할 수 있다.
 */
interface SessionSource {
    val session: StateFlow<Session?>
    fun storedCredentials(): Credentials?
    fun updateAccessToken(token: String)
    fun clear()
}
