package com.regnius.photoprism.store

import com.regnius.photoprism.BuildConfig
import com.regnius.photoprism.core.pro.Funding
import com.regnius.photoprism.core.pro.ProFeatures
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play 빌드의 Pro 게이트.
 *
 * Phase 0 에서는 **결제 코드를 넣지 않는다.** 아직 팔 기능이 없기 때문이다(§10.3).
 * 여기서 하는 일은 경계면을 확정해 두는 것뿐이고, Phase 3 에서 팔 물건이
 * 생겼을 때 이 클래스 안에서만 Billing 을 붙이면 된다.
 *
 * Play Billing 의존성은 반드시 이 소스셋에만 추가해야 한다
 * (`playImplementation(...)`). main 에 넣으면 F-Droid 빌드가 등재 거부된다.
 */
@Singleton
class PlayProFeatures @Inject constructor() : ProFeatures {

    private val _isUnlocked = MutableStateFlow(BuildConfig.PRO_UNLOCKED_BY_DEFAULT)
    override val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    override val supportsPurchase: Boolean = true

    override val donationUrl: String = Funding.GITHUB_SPONSORS

    // Phase 3: BillingClient 연결 → 구매 이력 조회 → _isUnlocked 갱신.
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProFeaturesModule {
    @Binds
    abstract fun bindProFeatures(impl: PlayProFeatures): ProFeatures
}
