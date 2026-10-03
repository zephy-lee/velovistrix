// 순수 Kotlin/JVM 모듈 — 피드를 식별하는 키, DTO→도메인 매핑, Room 을 읽는
// 로컬 PagingSource.
//
// **`PhotoFeedRemoteMediator` 는 여기 없다.** 그 클래스는 `db.withTransaction { }`
// 을 쓰는데, Room 2.8 에서 이 확장 함수는 **안드로이드 전용**이다 —
// `room-ktx` AAR 은 비어 있고 내용이 `room-runtime` 으로 옮겨졌지만,
// `room-runtime-jvm` 의 공개 API 에는 `useWriterConnection`/`Transactor` 만
// 있고 `withTransaction` 이 없다(2026-09-06 jar 확인). 그래서 mediator 는
// `:app` 에 남았고, 그것을 옮기려면 쓰기 경로를 `@Transaction` DAO 메서드로
// 바꿔야 한다 — 페이징 쓰기 경로를 건드리는 변경이라 별도 작업으로 남긴다.
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
    api(project(":core:network"))
    api(project(":core:database"))
    api(libs.androidx.paging.common)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.sqlite.bundled)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.serialization)
}
