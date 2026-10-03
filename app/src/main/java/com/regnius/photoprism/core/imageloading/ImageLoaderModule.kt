package com.regnius.photoprism.core.imageloading

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    /**
     * §12.2 — 그리드 스크롤 성능의 대부분이 이 설정에서 나온다.
     *
     * Retrofit 과 **같은 [OkHttpClient] 를 공유한다.** 커넥션 풀이 하나로 유지되고,
     * 셀프사인 인증서 신뢰 설정(§4.5)도 자동으로 이미지 요청에 적용된다.
     * 이걸 분리하면 "API 는 되는데 썸네일만 TLS 오류" 가 난다.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        thumbSizeFallback: ThumbSizeFallbackInterceptor,
    ): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, MEMORY_CACHE_PERCENT)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(DISK_CACHE_DIR))
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .components {
            // 요청한 크기가 없으면 캐시에 있는 다른 크기로 대신한다 (§17.36).
            add(thumbSizeFallback)
            add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            // GIF(2.8) 자동 루프 재생 — 이게 없으면 Coil 은 GIF 의 첫 프레임만
            // 정지 이미지로 그린다.
            add(AnimatedImageDecoder.Factory())
        }
        .crossfade(CROSSFADE_MILLIS)
        .build()

    private const val MEMORY_CACHE_PERCENT = 0.25
    private const val DISK_CACHE_DIR = "image_cache"
    private const val DISK_CACHE_BYTES = 512L * 1024 * 1024
    private const val CROSSFADE_MILLIS = 120
}
