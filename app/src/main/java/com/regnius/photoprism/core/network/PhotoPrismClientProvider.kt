package com.regnius.photoprism.core.network

import com.regnius.photoprism.core.auth.SessionStore
import com.regnius.photoprism.core.network.url.ServerUrl
import com.regnius.photoprism.core.network.url.ThumbUrlFactory
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * base URL 이 런타임에 정해지므로 Retrofit 인스턴스를 DI 그래프에 상수로 둘 수 없다.
 *
 * 서버 주소는 사용자가 입력하는 값이고, 로그아웃 후 다른 서버로 다시 로그인할
 * 수도 있다. 그래서 Retrofit 은 여기서 필요할 때 만들고, [OkHttpClient] 만
 * 싱글턴으로 공유한다 — 커넥션 풀·TLS 세션·인증서 신뢰 설정이 한 곳에 모인다(§3).
 */
@Singleton
class PhotoPrismClientProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val sessionStore: SessionStore,
) {
    // base 와 api 를 **한 쌍으로** 바꾼다. 둘을 따로 두면 병렬 프로브에서
    // "base 는 A 인데 api 는 B" 인 순간이 생겨 엉뚱한 서버로 요청이 나간다.
    @Volatile
    private var cached: Pair<HttpUrl, PhotoPrismApi>? = null

    /** 현재 세션 기준 API. 세션이 없으면 null. */
    val currentApi: PhotoPrismApi?
        get() {
            val serverUrl = sessionStore.session.value?.serverUrl ?: return null
            val base = ServerUrl.normalize(serverUrl) ?: return null
            return apiFor(base)
        }

    /**
     * 임의의 base URL 에 대한 API. 로그인 전 서버 프로브에도 쓴다.
     * 같은 URL 이면 인스턴스를 재사용한다.
     */
    fun apiFor(base: HttpUrl): PhotoPrismApi {
        val apiBase = ServerUrl.apiBase(base)
        cached?.let { (cachedBase, api) -> if (cachedBase == apiBase) return api }
        val created = build(apiBase, okHttpClient)
        cached = apiBase to created
        return created
    }

    /**
     * 서버 탐색용 API. 캐시하지 않고 **짧은 타임아웃**을 쓴다.
     *
     * 로그인 화면은 스킴·포트·경로 조합을 여러 개 동시에 찔러본다. 여기에 일반
     * 요청용 15초 타임아웃을 쓰면, 응답 없는 주소 하나 때문에 화면이 그만큼
     * 멈춰 있는 것처럼 보인다. 탐색은 빨리 포기하고 다음으로 넘어가는 편이 맞다.
     */
    fun probeApiFor(base: HttpUrl): PhotoPrismApi =
        build(ServerUrl.apiBase(base), probeClient)

    private val probeClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(PROBE_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .build()
    }

    private fun build(apiBase: HttpUrl, client: OkHttpClient): PhotoPrismApi =
        Retrofit.Builder()
            .baseUrl(apiBase)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PhotoPrismApi::class.java)

    /** 현재 세션의 previewToken 으로 만든 썸네일 URL 팩토리. */
    fun thumbUrlFactory(): ThumbUrlFactory? {
        val session = sessionStore.session.value ?: return null
        val base = ServerUrl.normalize(session.serverUrl) ?: return null
        return ThumbUrlFactory(ServerUrl.apiBase(base), session.previewToken)
    }

    fun invalidate() {
        cached = null
    }

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 4L
    }
}
