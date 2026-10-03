package com.regnius.photoprism.core.auth

import androidx.annotation.StringRes
import com.regnius.photoprism.core.data.AlbumRepository
import com.regnius.photoprism.core.data.PhotoFeedController
import com.regnius.photoprism.core.model.ServerSupport
import com.regnius.photoprism.core.network.PhotoPrismClientProvider
import com.regnius.photoprism.core.network.dto.ClientConfigDto
import com.regnius.photoprism.core.network.dto.SessionRequestDto
import com.regnius.photoprism.core.network.tls.UntrustedCertificateException
import com.regnius.photoprism.core.network.url.ServerUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/** 로그인 전 서버 프로브 결과. */
sealed interface ProbeResult {
    data class Reachable(
        val baseUrl: HttpUrl,
        val config: ClientConfigDto,
        val support: ServerSupport,
        val isCleartext: Boolean,
    ) : ProbeResult

    data class InvalidUrl(val input: String) : ProbeResult

    /**
     * 모든 후보가 실패했다.
     *
     * [attempts] 에 **시도한 URL 전부와 각각의 실패 사유**가 들어간다.
     * 스킴·경로를 여러 조합으로 시도하는 구조라, 이걸 보여주지 않으면
     * 사용자도 개발자도 다음에 무엇을 해야 할지 알 수 없다.
     */
    data class Unreachable(
        val baseUrl: HttpUrl,
        val cause: Throwable,
        val attempts: List<ProbeAttempt> = emptyList(),
        /** 처방 문구의 리소스. 0 이면 없음 — 문장은 화면이 만든다(§11.8). */
        @StringRes val hintRes: Int = 0,
    ) : ProbeResult
}

sealed interface LoginResult {
    data class Success(val session: Session) : LoginResult
    data object InvalidCredentials : LoginResult
    data class MissingPreviewToken(val config: ClientConfigDto?) : LoginResult
    data class Failed(val cause: Throwable) : LoginResult
}

@Singleton
class AuthRepository @Inject constructor(
    private val clientProvider: PhotoPrismClientProvider,
    private val sessionStore: SessionStore,
    private val photoFeedController: PhotoFeedController,
    private val albumRepository: AlbumRepository,
) {
    val session get() = sessionStore.session

    fun restore(): Session? = sessionStore.load()

    /**
     * 저장 전에 서버를 확인한다. §4.5
     *
     * `/api/v1/config` 는 인증 없이도 응답하므로, 자격증명을 입력받기 **전에**
     * "이 주소가 정말 PhotoPrism 인지"와 "지원하는 버전인지"를 알 수 있다.
     * 이 단계를 건너뛰면 사용자는 오타 난 주소에 비밀번호를 입력하고 나서야
     * 실패를 보게 된다.
     *
     * [ServerUrl.candidates] 가 준 후보를 **순서대로** 시도한다. 스킴(http/https)과
     * 경로를 사용자가 정확히 맞춰 입력하도록 요구하는 대신, 실제로 응답하는
     * 조합을 앱이 찾아낸다. 셀프호스팅에서는 이 두 가지가 가장 흔한 실패
     * 원인이다.
     */
    suspend fun probe(input: String): ProbeResult = coroutineScope {
        val candidates = ServerUrl.candidates(input)
        if (candidates.isEmpty()) return@coroutineScope ProbeResult.InvalidUrl(input)

        // 후보를 **동시에** 찔러본다.
        //
        // IP 만 입력해도 되도록 포트(2342/기본)와 흔한 서브패스까지 자동으로
        // 시도하다 보니 후보가 십여 개가 된다. 이걸 순차로 돌리면 응답 없는
        // 주소마다 타임아웃을 기다려 수십 초가 걸린다. 병렬로 던지면 전체
        // 소요는 가장 느린 하나(탐색용 4초 타임아웃) 수준으로 수렴한다.
        //
        // 승자는 "먼저 응답한 것" 이 아니라 **후보 목록에서 가장 앞선 것**이다.
        // 순서에 담긴 우선순위(사용자가 적은 경로 > 루트 > 추측한 서브패스)를
        // 경합 결과가 뒤집으면 안 되기 때문이다.
        val outcomes = candidates.map { base ->
            async {
                base to runCatching { clientProvider.probeApiFor(base).getConfig() }
            }
        }.awaitAll()

        outcomes.firstNotNullOfOrNull { (base, result) ->
            result.getOrNull()?.let { config ->
                ProbeResult.Reachable(
                    baseUrl = base,
                    config = config,
                    support = ServerSupport.of(config.version),
                    isCleartext = ServerUrl.isCleartext(base),
                )
            }
        }?.let { return@coroutineScope it }

        // 인증서를 못 믿는 경우는 사용자가 지문을 승인하면 바로 통과할 수 있는
        // 상태다. 다른 실패보다 우선해 보여줘야 조치를 안내할 수 있다.
        outcomes.firstNotNullOfOrNull { (base, result) ->
            (result.exceptionOrNull() as? UntrustedCertificateException)?.let { base to it }
        }?.let { (base, e) -> return@coroutineScope ProbeResult.Unreachable(base, e) }

        val attempts = outcomes.mapNotNull { (base, result) ->
            result.exceptionOrNull()?.let { ProbeDiagnostics.classify(base, it) }
        }

        // 전부 실패했다면 그 주소에 PhotoPrism 이 있기는 한지 따로 확인한다.
        // /config 는 404 인데 /status 가 200 이면 진단이 완전히 달라진다.
        val photoPrismDetected = candidates.map { base ->
            async { runCatching { clientProvider.probeApiFor(base).getStatus() }.isSuccess }
        }.awaitAll().any { it }

        val (base, cause) = outcomes.firstNotNullOfOrNull { (b, r) ->
            r.exceptionOrNull()?.let { b to it }
        } ?: (candidates.first() to IllegalStateException("no candidate reachable"))

        ProbeResult.Unreachable(
            baseUrl = base,
            cause = cause,
            attempts = attempts,
            hintRes = ProbeDiagnostics.hintRes(attempts, photoPrismDetected),
        )
    }

    /**
     * 로그인하고 세션을 저장한다.
     *
     * 토큰이 두 종류라는 점이 여기서 중요하다 (§4.2). 세션 응답에 config 가
     * 함께 오면 그 안의 previewToken 을 쓰고, 안 오면 인증된 상태로 `/config` 를
     * 한 번 더 호출해서 받아온다. previewToken 이 없으면 썸네일을 한 장도 못
     * 띄우므로, 세션만 만들어두고 성공했다고 보고하면 안 된다.
     */
    suspend fun login(baseUrl: HttpUrl, credentials: Credentials): LoginResult {
        val api = clientProvider.apiFor(baseUrl)
        return try {
            val response = api.createSession(
                SessionRequestDto(credentials.username, credentials.secret)
            )
            val token = response.bearerToken
                ?: return LoginResult.InvalidCredentials

            // 토큰을 먼저 저장해야 이어지는 /config 호출에 Bearer 가 붙는다.
            val provisional = Session(
                serverUrl = baseUrl.toString(),
                username = credentials.username,
                accessToken = token,
                previewToken = response.config?.previewToken.orEmpty(),
                downloadToken = response.config?.downloadToken.orEmpty(),
                serverVersion = response.config?.version,
            )
            sessionStore.save(provisional, credentials)

            val config = response.config?.takeIf { !it.previewToken.isNullOrBlank() }
                ?: api.getConfig()

            val previewToken = config.previewToken
            if (previewToken.isNullOrBlank()) {
                return LoginResult.MissingPreviewToken(config)
            }

            val session = provisional.copy(
                previewToken = previewToken,
                downloadToken = config.downloadToken.orEmpty(),
                serverVersion = config.version ?: provisional.serverVersion,
            )
            sessionStore.save(session, credentials)
            LoginResult.Success(session)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401 || e.code() == 400) LoginResult.InvalidCredentials
            else LoginResult.Failed(e)
        } catch (e: Exception) {
            LoginResult.Failed(e)
        }
    }

    /**
     * public 모드 서버에 자격증명 없이 접속한다.
     *
     * PhotoPrism 을 public 모드로 띄우면 `/config` 가 `public: true` 와
     * `previewToken: "public"` 을 돌려주고, 모든 읽기 API 가 인증 없이 응답한다.
     * 홈랩에서 가족과 공유하려고 이렇게 운영하는 사례가 있다.
     *
     * 이 경우 사용자에게 입력할 것이 없는데 로그인 폼을 보여주면, 뭘 넣어야
     * 하는지 알 수 없는 화면 앞에서 막히게 된다. 그래서 프로브 단계에서
     * public 을 감지하면 자격증명 입력을 통째로 건너뛴다.
     */
    fun connectPublic(baseUrl: HttpUrl, config: ClientConfigDto): LoginResult {
        val previewToken = config.previewToken
        if (previewToken.isNullOrBlank()) {
            // public 모드인데 previewToken 이 없으면 썸네일을 못 띄운다.
            // 목록만 보이고 이미지가 전부 깨지는 상태로 들여보내면 안 된다.
            return LoginResult.MissingPreviewToken(config)
        }
        val session = Session(
            serverUrl = baseUrl.toString(),
            username = "",
            accessToken = "",
            previewToken = previewToken,
            downloadToken = config.downloadToken.orEmpty(),
            serverVersion = config.version,
            isPublic = true,
        )
        sessionStore.save(session, credentials = null)
        return LoginResult.Success(session)
    }

    /**
     * `photoFeedController`/`albumRepository` 의 `clear()` 는 Phase 2.9 부터
     * Room 쓰기까지 포함해 `suspend` 다 — 인메모리 `Map.clear()` 와 달리
     * 즉시 끝나지 않으므로, 로그아웃→새 서버 로그인 사이에 이전 서버 데이터가
     * 한 프레임이라도 비치는 경쟁을 없애려면 여기서 끝나길 기다려야 한다.
     */
    suspend fun logout() {
        sessionStore.clear()
        clientProvider.invalidate()
        // 세션만 지우고 이 둘을 안 비우면, 로그아웃 후 다른 계정이나 다른
        // 서버로 들어갔을 때 이전 세션에서 불러온 앨범·사진 목록이 캐시에서
        // 그대로 튀어나온다 — 새 서버 응답이 아니라 남의 서버 데이터를 보여주는
        // 사고다. Phase 2.11 로그아웃 버튼을 실제로 연결하면서 발견했다.
        photoFeedController.clear()
        albumRepository.clear()
    }
}
