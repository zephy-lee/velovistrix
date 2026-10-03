package com.regnius.photoprism.store

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
 * F-Droid 빌드의 Pro 게이트 — **전 기능 해제**. §10.4
 *
 * 결제 코드가 한 줄도 없다. Google Play Billing 은 OSI 승인 라이선스가 아니라
 * F-Droid 에 포함될 수 없고, 소스가 공개된 이상 어차피 우회는 자명하다.
 * 이 커뮤니티에서는 잠그는 것보다 "직접 빌드할 수 있으면 그냥 쓰고, 마음에 들면
 * 후원해 달라"가 훨씬 잘 통한다.
 */
@Singleton
class FdroidProFeatures @Inject constructor() : ProFeatures {

    private val _isUnlocked = MutableStateFlow(true)
    override val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    override val supportsPurchase: Boolean = false

    override val donationUrl: String = Funding.GITHUB_SPONSORS
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProFeaturesModule {
    @Binds
    abstract fun bindProFeatures(impl: FdroidProFeatures): ProFeatures
}
