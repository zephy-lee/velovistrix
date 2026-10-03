package com.regnius.photoprism.core.di

import android.content.Context
import com.regnius.photoprism.BuildConfig
import com.regnius.photoprism.core.imageloading.ImmutableThumbCacheInterceptor
import com.regnius.photoprism.core.network.AuthInterceptor
import com.regnius.photoprism.core.network.SessionAuthenticator
import com.regnius.photoprism.core.network.tls.CertificatePinStore
import com.regnius.photoprism.core.network.tls.PinAwareHostnameVerifier
import com.regnius.photoprism.core.network.tls.UserPinnedTrustManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

/**
 * 세션 인증(Bearer 헤더 · 401 시 재로그인/세션 정리)이 **붙지 않은** 클라이언트.
 *
 * TLS 신뢰 설정(셀프사인 인증서 지원)은 동일하게 받되, `AuthInterceptor` /
 * `SessionAuthenticator` 는 없다. WebDAV 폴백(§17.1)처럼 Bearer 토큰과 무관한
 * 인증 방식(Basic Auth 등)을 쓰는 요청에 이 클라이언트를 쓴다 — 안 그러면
 * WebDAV 가 401 을 돌려줄 때 `SessionAuthenticator` 가 "토큰 만료" 로 오인해
 * 재로그인을 시도하고, 그마저 실패로 이어지면 **세션 전체를 지워 로그인
 * 화면으로 튕겨버린다.**
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RawOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // PhotoPrism 은 릴리스마다 응답 필드를 추가한다. 이 옵션이 없으면
        // 서버를 업데이트한 사용자의 앱이 파싱 예외로 죽는다. §2
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    @RawOkHttpClient
    fun provideRawOkHttpClient(pinStore: CertificatePinStore): OkHttpClient {
        val trustManager = UserPinnedTrustManager(pinStore)
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }

        // 갤러리 앱 특성상 동일 호스트(PhotoPrism 서버)로 수많은 이미지와
        // 영상 스트리밍 요청이 집중된다. OkHttp 기본값(호스트당 5개)은
        // OkHttp 기본값(호스트당 5)은 뷰어가 좌우 페이지를 미리 로딩하는
        // 순간 바로 바닥나서 큐가 밀린다 (§12.5).
        val dispatcher = Dispatcher().apply {
            maxRequestsPerHost = 10
            maxRequests = 10
            // §17.37 — 50 에서 10 으로 내렸다. **많이 열수록 느려진다.**
            //
            // 서버는 동시 50개도 100% 성공(최대 0.12초)으로 받아낸다(맥에서
            // 직접 확인). 병목은 서버가 아니라 기기의 Wi-Fi 쪽이고, 한꺼번에
            // 50개를 열면 서로 대역을 갉아먹어 전체가 같이 느려진다.
            //
            // 같은 구간을 새로 받는 조건에서 두 번씩 측정:
            //   동시 50 → 중앙값 131·259ms, 90%tile 443·773ms, 1초 초과 11·22건
            //   동시 10 → 중앙값  91· 66ms, 90%tile 219·210ms, 1초 초과  1· 3건
            //
            // §12.5 가 5(OkHttp 기본)에서 50 으로 올린 건 뷰어 프리로딩이
            // 기본값에 막혀서였는데, 10 이면 그 문제도 안 생기면서 그리드
            // 플링에서 서로 밀어내지 않는다.
        }

        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            // §17.36 — 접속 대기를 15초에서 6초로 줄인다.
            //
            // 인터넷은 되는데 **내 서버만** 안 붙는 상황(집 밖, VPN 끊김, 서버
            // 다운)에서, 목록 화면은 이 시간 동안 진행 표시만 돌린다. 게다가
            // OkHttp 는 IPv6·IPv4 경로를 차례로 시도하므로 실제 체감은 그
            // 두 배까지 간다 — 사용자가 "무한 대기" 로 느낀 이유다.
            //
            // 사진 서버는 같은 랜이거나 가까운 VPS 라 정상이면 1~2초면 붙는다.
            // 6초는 느린 모바일 회선에도 충분하면서, 안 붙는 서버를 붙들고
            // 있지는 않는 값이다. 읽기(30초)는 그대로 둔다 — 큰 원본 이미지와
            // 동영상 구간 전송에는 그만큼이 필요하다.
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(
                PinAwareHostnameVerifier(pinStore, HttpsURLConnection.getDefaultHostnameVerifier())
            )
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            // BODY 는 토큰이 로그에 남을 수 있어 debug 에서도 쓰지 않는다.
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                        }
                    )
                }
            }
            .build()
    }

    /** API 호출용. [provideRawOkHttpClient] 에 세션 인증(Bearer + 401 재로그인)을 얹는다. */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @RawOkHttpClient raw: OkHttpClient,
        authInterceptor: AuthInterceptor,
        sessionAuthenticator: SessionAuthenticator,
        thumbCacheInterceptor: ImmutableThumbCacheInterceptor,
    ): OkHttpClient = raw.newBuilder()
        .addInterceptor(authInterceptor)
        // 썸네일 응답의 짧은 max-age 를 장기 캐시로 덮어쓴다 (§12.2).
        // authInterceptor 뒤에 두어, 재시도된 요청에도 동일하게 적용되게 한다.
        .addInterceptor(thumbCacheInterceptor)
        .authenticator(sessionAuthenticator)
        .build()
}
