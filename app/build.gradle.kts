plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.regnius.photoprism"
    // 의존 라이브러리(core-ktx 1.19, okhttp-android 5.5)가 SDK 37 컴파일을 요구한다.
    // targetSdk 는 36 에 둔다 — compileSdk 는 "새 API 를 쓸 수 있는가",
    // targetSdk 는 "새 런타임 동작을 받아들이는가" 로 서로 독립이다.
    // 37 의 동작 변경은 Phase 1 에서 검토한 뒤 올린다.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.regnius.photoprism"
        minSdk = 31
        targetSdk = 36
        // §11.5 — versionName = <메이저>.<페이즈>.<MMDD+증분>.
        //
        // 릴리스마다 **손으로** 바꾼다. 빌드 시각에서 날짜를 뽑아 자동 생성하면
        // 같은 소스가 날마다 다른 산출물을 내서 재현 가능한 빌드(§10.5,
        // F-Droid 권장)가 깨진다.
        //
        // versionCode 는 versionName 을 그대로 못 쓴다 — Play 는 정수를 요구하고
        // 업로드마다 반드시 증가해야 한다. **연도를 앞에 붙인 YYMMDDR** 을 쓴다:
        // versionName 의 `09060` 을 그대로 정수로 만들면 다음 해 1월(01050)에
        // 값이 **작아져서** Play 가 업로드를 거부한다.
        versionCode = 2609120   // YYMMDD + 증분 1자리
        versionName = "1.3.09120"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // §4.1 — 지원 서버 하한. 이 아래 버전은 로그인 단계에서 차단한다.
        buildConfigField("int", "MIN_SERVER_BUILD", "260601")
    }

    // §10.4 — Play Billing 은 비자유 라이선스라 F-Droid 빌드에 포함할 수 없다.
    // Phase 0 에서는 두 flavor 의 코드가 사실상 동일하지만, 골격을 먼저 세워두면
    // Phase 3 에서 결제/Cast/지도를 붙일 때 리팩터링이 발생하지 않는다.
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
            // Pro 기능은 IAP 로 해제한다 (Phase 3 이후 활성화).
            buildConfigField("boolean", "PRO_UNLOCKED_BY_DEFAULT", "false")
            buildConfigField("String", "STORE_NAME", "\"play\"")
        }
        create("fdroid") {
            dimension = "store"
            applicationIdSuffix = ".fdroid"
            versionNameSuffix = "-fdroid"
            // 소스 공개 빌드는 Pro 전 기능을 해제한 상태로 배포한다 (§10.4).
            buildConfigField("boolean", "PRO_UNLOCKED_BY_DEFAULT", "true")
            buildConfigField("String", "STORE_NAME", "\"fdroid\"")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(org.gradle.jvm.toolchain.JvmVendorSpec.ADOPTIUM)
        }
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // §11.8 — 안드로이드 13+ 의 앱별 언어 설정에 노출할 언어.
    // locales_config.xml 을 직접 관리하므로 AGP 가 따로 생성하지 않게 둔다.
    androidResources {
        localeFilters += listOf("en", "ko")
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * §11.7 — 프로파일은 flavor 마다 따로 두지 않고 src/main 하나로 합친다.
 * play/fdroid 는 BuildConfig 상수만 다르고 실행 경로가 같아서, 각각 만들면
 * 같은 내용을 두 벌 유지하게 된다.
 */
baselineProfile {
    mergeIntoMain = true
}

androidComponents {
    // baselineprofile 플러그인이 release 를 복제해 만드는 변형들
    // (nonMinifiedRelease = 프로파일 채집용, benchmarkRelease = 측정용)은
    // release 를 그대로 물려받아 **서명 설정이 비어 있다**. 서명이 없으면
    // 기기에 설치가 안 돼 생성 자체가 실패한다. 이 둘은 배포물이 아니므로
    // 디버그 키로 서명한다 — release 본체의 서명 설정은 건드리지 않는다.
    finalizeDsl { dsl ->
        val debugSigning = dsl.signingConfigs.getByName("debug")
        listOf("nonMinifiedRelease", "benchmarkRelease").forEach { name ->
            dsl.buildTypes.findByName(name)?.signingConfig = debugSigning
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))

    // §11.7 — 설치 직후 베이스라인 프로파일을 ART 에 등록해 준다.
    // (Play 설치본은 스토어가 대신 해주지만, F-Droid·사이드로드에는 이게 있어야 한다.)
    implementation(libs.androidx.profileinstaller)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test.junit4)
    debugImplementation(libs.compose.test.manifest)

    baselineProfile(project(":baselineprofile"))
}
