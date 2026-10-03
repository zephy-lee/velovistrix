// 순수 Kotlin/JVM 모듈 — Room 을 **안드로이드가 아닌** JVM 타깃으로 컴파일한다.
//
// 이게 이 모듈이 존재하는 이유다(§17.10). 단일 `com.android.application` 모듈에
// 있을 때는 Room 이 android 타깃으로 컴파일돼, `RoomDatabase` 의 android 구현이
// `Looper.getMainLooper()` 를 부르고 로컬 유닛테스트에서는 그게 null 이라 NPE 가
//났다. 그래서 Phase 2.9 의 트랜잭션 테스트 3종을 쓰다가 버려야 했다.
//
// Room 2.7+ 는 KMP 라 `room-runtime` 에 진짜 jvm 변형(`room-runtime-jvm`)이 있고,
// 여기서는 그게 선택된다 — `Looper` 를 아예 거치지 않으므로 `src/test` 에서 실제
// DB 를 열어 트랜잭션 동작을 검증할 수 있다.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
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
    api(libs.androidx.room.runtime)
    // PagingSource 는 paging-common 에 있다 — paging-runtime(안드로이드 전용)이
    // 아니다. 그래서 로컬 PagingSource 도 이 모듈에서 컴파일된다.
    api(libs.androidx.paging.common)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // 호스트 JVM 용 SQLite 네이티브 바이너리. android 변형(`sqlite-bundled`)에는
    // 기기용 `.so` 만 들어 있어 맥/리눅스에서 UnsatisfiedLinkError 가 난다.
    testImplementation(libs.androidx.sqlite.bundled)
}
