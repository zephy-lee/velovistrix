package com.regnius.photoprism.feature.diagnostics

import com.regnius.photoprism.core.auth.AuthRepository
import com.regnius.photoprism.core.auth.Credentials
import com.regnius.photoprism.core.auth.LoginResult
import com.regnius.photoprism.core.auth.ProbeResult
import com.regnius.photoprism.core.model.ServerSupport
import com.regnius.photoprism.core.model.ThumbSize
import com.regnius.photoprism.core.network.PhotoPrismApi
import com.regnius.photoprism.core.network.PhotoPrismClientProvider
import com.regnius.photoprism.core.network.PhotoQuery
import com.regnius.photoprism.core.network.tls.UntrustedCertificateException
import com.regnius.photoprism.core.network.url.ThumbUrlFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/**
 * §4.3 의 "Phase 0 에서 실측 확인" 항목을 실제 서버에 대고 하나씩 검증한다.
 *
 * 이걸 자동화 테스트가 아니라 앱 안의 화면으로 만든 이유:
 * 사용자마다 PhotoPrism 버전과 설정이 다르고, 개발자 손에는 그 서버가 없다.
 * 실측 도구가 앱에 들어있으면 누구든 자기 서버로 돌려보고 결과를 붙여넣을 수
 * 있다. Phase 1 에서 버그 리포트를 받을 때도 이 결과 한 장이면 원인 절반이
 * 좁혀진다.
 */
class ApiProbeRunner @Inject constructor(
    private val authRepository: AuthRepository,
    private val clientProvider: PhotoPrismClientProvider,
    private val okHttpClient: OkHttpClient,
) {

    suspend fun run(
        serverUrlInput: String,
        credentials: Credentials,
        onStep: (ProbeStep) -> Unit,
    ) {
        // 1 — URL 정규화 (§4.5)
        val probe = step(S_URL, "서버 주소 정규화", "스킴 보정 · 서브패스 보존 · /api 접미사 제거", onStep) {
            when (val r = authRepository.probe(serverUrlInput)) {
                is ProbeResult.InvalidUrl -> fail("주소를 해석할 수 없습니다: ${r.input}")
                is ProbeResult.Unreachable -> {
                    val cause = r.cause
                    if (cause is UntrustedCertificateException) {
                        fail(
                            "TLS 인증서를 신뢰할 수 없습니다.\n지문: ${cause.fingerprint}\n" +
                                "셀프사인 인증서라면 로그인 화면에서 이 지문을 승인해야 합니다.",
                            evidence = "subject=${cause.subject}\nissuer=${cause.issuer}",
                        )
                    } else {
                        fail("연결 실패: ${cause.javaClass.simpleName}: ${cause.message}")
                    }
                }
                is ProbeResult.Reachable -> {
                    val warn = r.isCleartext
                    Outcome(
                        status = if (warn) ProbeStep.Status.Warn else ProbeStep.Status.Pass,
                        detail = if (warn) "평문 HTTP 로 접속합니다 (LAN 이 아니면 위험)" else "HTTPS",
                        evidence = "base=${r.baseUrl}",
                        payload = r,
                    )
                }
            }
        }
        val reachable = probe as? ProbeResult ?: return
        if (reachable !is ProbeResult.Reachable) return

        // 2 — /config 응답 (인증 전)
        step(S_CONFIG, "GET /api/v1/config", "인증 없이 서버 식별 · 버전 확인", onStep) {
            val c = reachable.config
            Outcome(
                status = ProbeStep.Status.Pass,
                detail = "name=${c.name ?: "?"} version=${c.version ?: "?"} mode=${c.mode ?: "?"}",
                evidence = "public=${c.public} readonly=${c.readonly}",
            )
        }

        // 3 — 지원 버전 확인 (§4.1)
        step(S_VERSION, "서버 버전 지원 확인", "260601 미만은 Bearer 인증이 없어 지원하지 않는다", onStep) {
            when (val s = reachable.support) {
                is ServerSupport.Supported ->
                    Outcome(ProbeStep.Status.Pass, "빌드 ${s.build} (${s.build.toIsoDate()})")
                is ServerSupport.TooOld ->
                    Outcome(
                        ProbeStep.Status.Fail,
                        "빌드 ${s.build} — 최소 ${s.minimum} (${s.minimum.toIsoDate()}) 필요",
                    )
                is ServerSupport.Unknown ->
                    Outcome(
                        ProbeStep.Status.Warn,
                        "버전을 읽을 수 없습니다: ${s.reported ?: "(없음)"} — 개발 빌드일 수 있어 진행합니다",
                    )
            }
        }

        // 4 — 로그인 (§4.1)
        val loginOk = step(S_SESSION, "POST /api/v1/session", "Bearer access_token 발급 · ${credentials.kind}", onStep) {
            when (val r = authRepository.login(reachable.baseUrl, credentials)) {
                is LoginResult.Success -> Outcome(
                    ProbeStep.Status.Pass,
                    "토큰 발급 성공 (${r.session.accessToken.take(6)}…)",
                    evidence = "previewToken=${r.session.previewToken.take(6)}… downloadToken=${r.session.downloadToken.take(6)}…",
                    payload = r.session,
                )
                LoginResult.InvalidCredentials -> fail("자격증명이 거부되었습니다 (401/400)")
                is LoginResult.MissingPreviewToken ->
                    fail("세션은 만들어졌지만 previewToken 이 없습니다 — 썸네일을 띄울 수 없습니다")
                is LoginResult.Failed -> fail("로그인 실패: ${r.cause.message}")
            }
        } != null
        if (!loginOk) return

        val api = clientProvider.currentApi ?: return
        val thumbs = clientProvider.thumbUrlFactory() ?: return

        // 5 — 앨범 목록
        val albumUid = step(S_ALBUMS, "GET /api/v1/albums", "앨범 DTO 파싱 · Phase 1 첫 화면", onStep) {
            val albums = api.getAlbums(count = 5)
            if (albums.isEmpty()) {
                Outcome(ProbeStep.Status.Warn, "앨범이 0개입니다 — 이후 앨범 범위 검증을 건너뜁니다")
            } else {
                Outcome(
                    ProbeStep.Status.Pass,
                    "${albums.size}건 · 첫 항목 \"${albums.first().title}\" (${albums.first().photoCount}장)",
                    evidence = albums.take(3).joinToString("\n") { "${it.uid}  ${it.type}  ${it.title}" },
                    payload = albums.first().uid,
                )
            }
        } as? String

        // 6 — 전체 사진 (merged)
        step(S_PHOTOS, "GET /api/v1/photos?merged=true", "merged 가 RAW+JPEG 를 합치는지 · Files 배열 확인", onStep) {
            val photos = api.getPhotos(count = 20)
            if (photos.isEmpty()) {
                Outcome(ProbeStep.Status.Warn, "사진이 0건입니다")
            } else {
                val multiFile = photos.count { it.files.size > 1 }
                Outcome(
                    ProbeStep.Status.Pass,
                    "${photos.size}건 · 복수 파일 항목 ${multiFile}건",
                    evidence = photos.take(3).joinToString("\n") {
                        "${it.uid}  type=${it.type}  files=${it.files.size}  ${it.title}"
                    },
                )
            }
        }

        // 7 — 앨범 범위 파라미터 (§4.3 확인 필요 항목)
        if (albumUid != null) {
            step(S_SCOPE, "GET /photos?s={albumUid}", "앨범 범위 지정이 s 파라미터로 되는지 — 표에 미확정으로 남은 항목", onStep) {
                val scoped = api.getPhotos(count = 10, scope = albumUid)
                val all = api.getPhotos(count = 10)
                when {
                    scoped.isEmpty() && all.isNotEmpty() ->
                        Outcome(ProbeStep.Status.Fail, "s= 로 0건 — 이 서버는 album= 파라미터를 쓸 수 있습니다")
                    else ->
                        Outcome(ProbeStep.Status.Pass, "${scoped.size}건 반환 — s 파라미터 동작 확인")
                }
            }
        } else {
            skip(S_SCOPE, "GET /photos?s={albumUid}", "앨범이 없어 건너뜀", onStep)
        }

        // 8 — 이미지 탭 커버리지 (§5.3 결정의 전제)
        //
        // 단순히 "OR 문법이 되는가" 만 보면 부족하다. PhotoPrism 은 부정 문법을
        // 지원하지 않아 이미지 탭을 화이트리스트로 정의할 수밖에 없는데, 그러면
        // 서버가 새 타입을 추가할 때 탭에서 조용히 사라진다. 그래서 여기서
        // **비-video 전체 개수와 이미지 탭 개수가 일치하는지** 확인한다.
        step(S_IMG_FILTER, "이미지 탭 커버리지", "화이트리스트가 video 아닌 모든 타입을 덮는지 (§5.3)", onStep) {
            val probeCount = 500
            val all = api.getPhotos(count = probeCount)
            val images = runCatching { api.getPhotos(count = probeCount, query = PhotoQuery.IMAGES) }.getOrNull()
                ?: return@step Outcome(
                    ProbeStep.Status.Warn,
                    "OR 문법이 거부됨 — PhotoQuery.IMAGES_SIMPLE 로 폴백해야 합니다",
                )

            val nonVideo = all.filter { !it.type.equals("video", ignoreCase = true) }
            val covered = images.map { it.uid }.toSet()
            val missing = nonVideo.filterNot { it.uid in covered }
            val missingTypes = missing.map { it.type }.distinct()

            when {
                all.isEmpty() -> Outcome(ProbeStep.Status.Warn, "사진이 없어 검증 불가")
                missing.isEmpty() -> Outcome(
                    ProbeStep.Status.Pass,
                    "비-video ${nonVideo.size}건 전부 커버 · 이미지 탭 ${images.size}건",
                    evidence = "포함된 타입: " + images.map { it.type }.distinct().sorted().joinToString(", "),
                )
                else -> Outcome(
                    ProbeStep.Status.Fail,
                    "${missing.size}건이 어느 탭에도 안 나옵니다 — PhotoQuery.IMAGES 에 타입 추가 필요",
                    evidence = "누락 타입: ${missingTypes.joinToString(", ")}\n" +
                        missing.take(3).joinToString("\n") { "${it.uid} type=${it.type} ${it.title}" },
                )
            }
        }

        // 8b — 부정 문법이 여전히 못 쓰는지 확인.
        // `type:!video` 는 오류가 아니라 조용히 0건을 돌려준다. 나중에 누군가
        // "화이트리스트 대신 부정을 쓰면 간단하겠네" 라고 바꿨다가 빈 탭을
        // 만드는 것을 막기 위해 실측 결과를 기록으로 남긴다.
        step(S_NEGATION, "부정 문법 확인", "type:!video 가 쓸 수 있게 됐는지 (현재는 조용히 0건)", onStep) {
            val negated = runCatching { api.getPhotos(count = 50, query = "type:!video") }.getOrNull()
            when {
                negated == null -> Outcome(ProbeStep.Status.Pass, "거부됨 — 화이트리스트 유지가 맞습니다")
                negated.isEmpty() -> Outcome(
                    ProbeStep.Status.Pass,
                    "0건 반환 — 부정 문법 미지원 확인. 화이트리스트 유지가 맞습니다",
                )
                else -> Outcome(
                    ProbeStep.Status.Warn,
                    "${negated.size}건 반환 — 이 서버는 부정 문법을 지원합니다. " +
                        "PhotoQuery.IMAGES 를 부정 방식으로 단순화할 수 있습니다",
                )
            }
        }

        // 9 — 동영상 탭 필터
        step(S_VID_FILTER, "GET /photos?q=type:video", "앨범 상세의 동영상 탭 (§5.3)", onStep) {
            val videos = api.getPhotos(count = 20, query = PhotoQuery.VIDEOS)
            val nonVideo = videos.filter { !it.type.equals("video", ignoreCase = true) }
            when {
                videos.isEmpty() -> Outcome(ProbeStep.Status.Warn, "동영상 0건 — 라이브러리에 영상이 없을 수 있습니다")
                nonVideo.isNotEmpty() -> Outcome(
                    ProbeStep.Status.Warn,
                    "${videos.size}건 중 ${nonVideo.size}건이 video 가 아님",
                    evidence = nonVideo.take(3).joinToString("\n") { "${it.uid} type=${it.type}" },
                )
                else -> Outcome(ProbeStep.Status.Pass, "${videos.size}건 전부 video")
            }
        }

        // 10 — 즐겨찾기 필터 (Phase 2)
        step(S_FAV, "GET /photos?q=favorite:true", "즐겨찾기 탭 (Phase 2.4)", onStep) {
            val favs = api.getPhotos(count = 10, query = PhotoQuery.FAVORITES)
            Outcome(ProbeStep.Status.Pass, "${favs.size}건")
        }

        // 10b — 검색 필터 문법 확인 (Phase 2.2)
        //
        // before:/after:/camera: 는 PhotoPrism 문서 기준 문법을 그대로 썼을 뿐,
        // type:/favorite: 와 달리 이 앱에서 실서버로 검증한 적이 없다
        // (SearchFilters.kt 주석 참고). 임의 사진 한 장을 기준으로 "그 날짜를
        // 포함하는 기간 필터"와 "그 카메라로 찍힌 것만" 필터를 걸어, 그 사진이
        // 실제로 결과에 포함되는지로 문법이 맞는지 판정한다.
        step(S_SEARCH_FILTERS, "검색 필터 문법 확인 (기간/카메라)", "Phase 2.2 SearchFilters 의 before:/after:/camera: 문법 검증", onStep) {
            val sample = api.getPhotos(count = 50).firstOrNull { !it.takenAtLocal.isNullOrBlank() }
                ?: return@step Outcome(ProbeStep.Status.Skipped, "날짜 있는 사진이 없어 건너뜀")
            val date = sample.takenAtLocal!!.take(10) // "2024-05-01T12:00:00Z" -> "2024-05-01"

            val dateResult = runCatching {
                api.getPhotos(count = 20, query = "after:$date before:$date")
            }.getOrNull()
            val dateOutcome = when {
                dateResult == null -> "before:/after: 거부됨"
                dateResult.any { it.uid == sample.uid } -> "정상 — ${sample.uid} 포함"
                else -> "문법은 통과했지만 기준 사진이 결과에 없음 (경계 조건 다를 수 있음)"
            }

            val camera = sample.cameraModel?.takeIf { it.isNotBlank() }
            val cameraOutcome = if (camera == null) {
                "카메라 정보 없는 사진이라 건너뜀"
            } else {
                val cameraResult = runCatching { api.getPhotos(count = 20, query = "camera:\"$camera\"") }.getOrNull()
                when {
                    cameraResult == null -> "camera: 거부됨"
                    cameraResult.any { it.uid == sample.uid } -> "정상 — ${sample.uid} 포함"
                    else -> "문법은 통과했지만 기준 사진이 결과에 없음"
                }
            }

            val ok = dateResult != null && dateResult.any { it.uid == sample.uid } &&
                (camera == null || cameraOutcome.startsWith("정상"))
            Outcome(
                status = if (ok) ProbeStep.Status.Pass else ProbeStep.Status.Warn,
                detail = "기간: $dateOutcome / 카메라: $cameraOutcome",
                evidence = "기준 사진 ${sample.uid} · date=$date · camera=${camera ?: "(없음)"}",
            )
        }

        // 11 — 썸네일 (§4.2 previewToken 검증)
        step(S_THUMB, "GET /api/v1/t/{hash}/{previewToken}/tile_224", "previewToken 유효성 · 인증 헤더 없이 받아지는지", onStep) {
            val sample = api.getPhotos(count = 1).firstOrNull()
                ?: return@step Outcome(ProbeStep.Status.Skipped, "사진이 없어 건너뜀")
            val hash = sample.files.firstOrNull { !it.video }?.hash ?: sample.hash
            if (hash.isBlank()) return@step Outcome(ProbeStep.Status.Fail, "파일 해시가 비어 있습니다")

            val url = thumbs.thumb(hash, ThumbSize.GRID_COMPACT)
            // 인증 헤더를 일부러 붙이지 않는다 — URL 의 previewToken 만으로 받아져야 한다.
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                val bytes = response.body.contentLength()
                if (response.isSuccessful) {
                    Outcome(
                        ProbeStep.Status.Pass,
                        "HTTP ${response.code} · ${response.header("Content-Type")} · ${bytes}B",
                        evidence = "cacheKey=${ThumbUrlFactory.diskCacheKey(hash, ThumbSize.GRID_COMPACT)}",
                    )
                } else {
                    Outcome(ProbeStep.Status.Fail, "HTTP ${response.code} — previewToken 이 유효하지 않습니다")
                }
            }
        }
    }

    // ---- 실행 헬퍼 -------------------------------------------------------

    private class Outcome(
        val status: ProbeStep.Status,
        val detail: String,
        val evidence: String = "",
        val payload: Any? = null,
    )

    private fun fail(detail: String, evidence: String = "") =
        Outcome(ProbeStep.Status.Fail, detail, evidence)

    private suspend fun step(
        id: String,
        title: String,
        purpose: String,
        onStep: (ProbeStep) -> Unit,
        block: suspend () -> Outcome,
    ): Any? {
        onStep(ProbeStep(id, title, purpose, ProbeStep.Status.Running))
        val outcome = try {
            block()
        } catch (e: Exception) {
            Outcome(ProbeStep.Status.Fail, "${e.javaClass.simpleName}: ${e.message}")
        }
        onStep(ProbeStep(id, title, purpose, outcome.status, outcome.detail, outcome.evidence))
        return if (outcome.status == ProbeStep.Status.Pass || outcome.status == ProbeStep.Status.Warn) {
            outcome.payload
        } else {
            null
        }
    }

    private fun skip(id: String, title: String, reason: String, onStep: (ProbeStep) -> Unit) {
        onStep(ProbeStep(id, title, reason, ProbeStep.Status.Skipped, reason))
    }

    private companion object {
        const val S_URL = "url"
        const val S_CONFIG = "config"
        const val S_VERSION = "version"
        const val S_SESSION = "session"
        const val S_ALBUMS = "albums"
        const val S_PHOTOS = "photos"
        const val S_SCOPE = "scope"
        const val S_IMG_FILTER = "img_filter"
        const val S_NEGATION = "negation"
        const val S_VID_FILTER = "vid_filter"
        const val S_FAV = "favorites"
        const val S_SEARCH_FILTERS = "search_filters"
        const val S_THUMB = "thumb"
    }
}
