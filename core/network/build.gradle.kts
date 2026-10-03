// 순수 Kotlin/JVM 모듈 — 서버와 주고받는 모양(API 인터페이스 · DTO · URL 조립)만 담는다.
//
// **여기 없는 것**: OkHttp 클라이언트 조립, 세션 주입 인터셉터, TLS 신뢰 정책,
// 네트워크 연결 확인. 그것들은 안드로이드 저장소/시스템 서비스에 묶여 있어
// `:app` 에 남았다. 이 모듈이 순수 JVM 이어야 하는 이유는 `:core:data` 가
// 여기 의존하면서 순수 JVM 이어야 하기 때문이다(§17.10, 아래 모듈 참고).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":core:model"))
    api(libs.retrofit)
    api(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.serialization)
}
