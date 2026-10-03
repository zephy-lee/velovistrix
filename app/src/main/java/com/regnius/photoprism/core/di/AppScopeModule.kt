package com.regnius.photoprism.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {
    /**
     * 앱과 수명을 같이하는 스코프.
     *
     * `PagingData` 를 `cachedIn` 으로 붙들어 두는 데 쓴다. ViewModel 스코프에
     * 캐시하면 그리드 → 뷰어로 이동할 때 ViewModel 이 새로 생겨 **같은 목록을
     * 서버에서 다시 받아온다.** 뷰어가 열리자마자 빈 화면이 되는 원인이다.
     * 앱 스코프에 캐시하면 두 화면이 동일한 페이징 스트림을 공유한다.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
