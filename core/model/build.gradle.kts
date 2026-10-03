// 순수 Kotlin/JVM 모듈 — 안드로이드 의존성이 없다.
//
// 도메인 모델은 화면도 DB 도 네트워크도 몰라야 하는 계층이라 여기가 가장
// 아래다. 안드로이드 플러그인을 붙이지 않는 것 자체가 그 규칙을 컴파일러로
// 강제하는 장치다 — 여기서 `android.*` 를 import 하면 빌드가 깨진다.
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
    api(libs.kotlinx.serialization.json)
    // @Immutable / @Stable 만 쓴다. 이 애노테이션은 컴포즈 컴파일러 플러그인
    // 없이도 붙일 수 있고(붙이는 쪽은 그냥 애노테이션이다), 그걸 읽어 안정성을
    // 추론하는 건 이걸 쓰는 UI 모듈의 컴파일러다.
    api(libs.compose.runtime)

    testImplementation(libs.junit)
}
