package com.regnius.photoprism.core.imageloading

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 썸네일 응답의 캐시 헤더를 장기 보관으로 덮어쓴다. §12.2
 *
 * PhotoPrism 썸네일 URL 은 `/api/v1/t/{fileHash}/...` 형태라 **내용이 파일 해시에
 * 종속이다. 즉 같은 URL 의 응답은 영원히 같다.** 그런데 서버가 짧은 max-age 를
 * 내려보내면 캐시된 이미지마다 재검증 요청이 날아가고, 스크롤 중에 그게 수백 건
 * 쌓이면 체감 속도가 눈에 띄게 나빠진다.
 *
 * Coil 설정이 아니라 OkHttp 레이어에서 처리하는 이유는, 이쪽이 Coil 버전의
 * 캐시 정책 API 변화와 무관하게 동작하기 때문이다.
 */
@Singleton
class ImmutableThumbCacheInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!chain.request().isImmutableMedia()) return response

        return response.newBuilder()
            .removeHeader("Pragma")
            .removeHeader("Expires")
            .header("Cache-Control", "public, max-age=$ONE_YEAR_SECONDS, immutable")
            .build()
    }

    private fun okhttp3.Request.isImmutableMedia(): Boolean {
        val segments = url.pathSegments
        val i = segments.indexOf("v1")
        return i >= 0 && i + 1 < segments.size && segments[i + 1] == "t"
    }

    private companion object { const val ONE_YEAR_SECONDS = 31_536_000 }
}
