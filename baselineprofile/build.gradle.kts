plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

/**
 * §11.7 — 베이스라인 프로파일 생성 모듈.
 *
 * 이 모듈은 앱에 들어가지 않는다. 실기기에서 앱을 실제로 조작해 본 뒤,
 * 그때 실행된 코드 경로(클래스·메서드)를 목록으로 뽑아 `:app` 의
 * `src/main/generated/baselineProfiles/` 에 저장하는 도구다. 그 목록이 APK 에
 * 실려 나가면 설치 시점에 그 부분만 미리 AOT 컴파일돼, 첫 실행에서
 * 인터프리터/JIT 를 거치지 않는다.
 */
android {
    namespace = "com.regnius.photoprism.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // 프로파일을 만들려면 언루팅 기기에서는 API 33 이상이 필요하다.
        // 앱의 minSdk(31)보다 높지만, 이 모듈은 배포물이 아니라서 상관없다.
        minSdk = 33
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // :app 은 play/fdroid 두 flavor 를 갖지만 둘은 BuildConfig 상수만
        // 다르고 실행 경로가 같다 — 프로파일은 play 하나로 만들어 양쪽이
        // 함께 쓴다(:app 의 mergeIntoMain).
        missingDimensionStrategy("store", "play")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // 에뮬레이터는 쓰지 않는다(프로젝트 표준) — 항상 연결된 실기기에서 돈다.
    useConnectedDevices = true
}

dependencies {
    // com.android.test 모듈은 소스가 src/main 이라 implementation 을 쓴다.
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
