package com.regnius.photoprism

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.regnius.photoprism.core.auth.SessionStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PhotoPrismApp : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var sessionStore: SessionStore

    // Hilt 가 만든, OkHttp 클라이언트를 Retrofit 과 공유하고 GIF 디코더가 등록된
    // 바로 그 ImageLoader (§ImageLoaderModule). 이 Factory 를 등록하지 않으면
    // AsyncImage 는 Coil 의 기본 SingletonImageLoader(별도 OkHttp, GIF 디코더 없음)
    // 를 쓰게 되어 커넥션 풀도 갈라지고 GIF 도 정지 이미지로만 보인다.
    @Inject lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        // 저장된 세션을 메모리로 올린다. 이게 있어야 앱 재시작 후에도
        // AuthInterceptor 가 첫 요청부터 Bearer 를 붙일 수 있다.
        sessionStore.load()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
