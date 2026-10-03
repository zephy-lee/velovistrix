package com.regnius.photoprism.core.auth

/**
 * 로그인 자격증명. §4.1
 *
 * 앱 패스워드를 **1급 시민으로 지원한다.** 서드파티 뷰어가 사용자의 진짜 계정
 * 비밀번호를 기기에 보관하는 것보다, 서버에서 언제든 폐기 가능한 앱 전용
 * 비밀번호를 쓰는 편이 훨씬 안전하다. 두 방식 모두 `POST /session` 을 타지만,
 * 사용자에게 어느 쪽을 쓰는지 분명히 보여주기 위해 타입으로 구분한다.
 */
sealed interface Credentials {
    val username: String
    val secret: String

    data class Password(
        override val username: String,
        override val secret: String,
    ) : Credentials

    data class AppPassword(
        override val username: String,
        override val secret: String,
    ) : Credentials

    val kind: String get() = when (this) {
        is Password -> "password"
        is AppPassword -> "app_password"
    }
}
