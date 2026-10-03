package com.regnius.photoprism.core.pro

import kotlinx.coroutines.flow.StateFlow

/**
 * 유료 해제 대상 기능. §10.3
 *
 * **Phase 1~2 의 기능은 여기 없다.** 사진을 보고, 넘기고, 검색하는 것은 이 앱의
 * 본질이라 무료다. 거기에 벽을 세우면 리뷰가 무너지고 유료 전환할 모수 자체가
 * 안 생긴다. 유료 경계는 Phase 3~4 의 부가 기능에만 둔다.
 *
 * Phase 0 시점에는 이 기능들이 아직 구현되지 않았다. 열거형과 게이트만 미리
 * 세워두는 이유는, 나중에 붙일 때 호출부를 찾아다니지 않기 위해서다.
 */
enum class ProFeature {
    SLIDESHOW,
    CAST,
    OFFLINE_KEEP,
    HOME_WIDGET,
    MAP_EXPLORE,
}

/**
 * 스토어별로 다르게 구현되는 유일한 경계면. §10.4
 *
 * `play`   : Google Play Billing 으로 일회성 Pro 를 해제한다.
 * `fdroid` : 전 기능 해제 상태로 배포한다. Play Billing 은 비자유 라이선스라
 *            F-Droid 빌드에 포함할 수 없고, 애초에 소스가 공개된 이상 결제
 *            우회는 자명하다. 막으려 애쓰는 대신 후원 경로를 안내한다.
 *
 * 이 인터페이스가 존재하는 진짜 이유는 **나머지 코드가 flavor 를 몰라도 되게**
 * 하는 것이다. 기능 코드 어디에도 `BuildConfig.STORE_NAME` 분기가 없어야 한다.
 */
interface ProFeatures {
    /** Pro 해제 여부. UI 가 구독할 수 있도록 Flow 로 노출한다. */
    val isUnlocked: StateFlow<Boolean>

    /** 이 빌드에서 결제 UI 를 띄울 수 있는가 (F-Droid 는 false). */
    val supportsPurchase: Boolean

    /** 후원 안내 링크 — F-Droid 빌드의 주 수익 경로다. */
    val donationUrl: String

    fun isAvailable(feature: ProFeature): Boolean = isUnlocked.value
}

/** 후원 링크 — §10.2 에서 GitHub Sponsors 를 주력으로 정했다. */
object Funding {
    const val GITHUB_SPONSORS = "https://github.com/sponsors/zephy-lee"
}
