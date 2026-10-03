package com.regnius.photoprism.core.auth

import androidx.annotation.StringRes
import android.content.Context
import com.regnius.photoprism.R
import okhttp3.HttpUrl
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 후보 URL 하나를 시도한 결과.
 *
 * 스킴과 경로를 여러 조합으로 시도하는 구조에서는, 실패했을 때 **무엇을 시도해
 * 어떻게 실패했는지 사용자에게 보여주지 않으면 디버깅이 불가능하다.**
 * "연결할 수 없습니다" 한 줄만 띄우면 사용자도 개발자도 다음에 뭘 해야 할지
 * 알 수 없다.
 */
data class ProbeAttempt(
    val url: String,
    val outcome: Outcome,
    @StringRes val detailRes: Int,
    /** [detailRes] 가 `%1$s` 를 가진 경우 채울 값(HTTP 코드, 라이브러리 메시지 등). */
    val detailArg: String? = null,
) {
    enum class Outcome {
        /** 서버가 HTTP 응답을 줬지만 PhotoPrism API 가 아니었다. */
        HttpError,
        /** TCP 연결 자체가 안 됐다. */
        NoConnection,
        /** TLS 협상 실패 — 보통 https 로 평문 서버를 친 경우. */
        TlsError,
        /** 응답은 왔지만 JSON 을 해석할 수 없었다. */
        BadResponse,
    }

    fun detail(context: Context): String =
        if (detailArg != null) context.getString(detailRes, detailArg) else context.getString(detailRes)

    fun line(context: Context): String = "$url → ${detail(context)}"
}

/**
 * 시도 기록으로부터 사용자가 실제로 취할 수 있는 조치를 도출한다.
 *
 * **판정만 하고 문장은 만들지 않는다** — 어떤 처방을 내릴지는 언어와 무관한
 * 규칙이고, 그 문장을 무슨 말로 쓰는지는 로케일이 정한다. 갈라 두면 판정
 * 규칙을 안드로이드 없이 그대로 테스트할 수 있다(§11.8).
 */
object ProbeDiagnostics {

    fun classify(url: HttpUrl, error: Throwable): ProbeAttempt {
        val full = url.newBuilder().addPathSegments("api/v1/config").build().toString()
        return when {
            error is retrofit2.HttpException ->
                ProbeAttempt(full, ProbeAttempt.Outcome.HttpError, R.string.probe_http_error, error.code().toString())

            error is SSLException ->
                ProbeAttempt(full, ProbeAttempt.Outcome.TlsError, R.string.probe_tls_error, error.message)

            error is UnknownHostException ->
                ProbeAttempt(full, ProbeAttempt.Outcome.NoConnection, R.string.probe_host_not_found)

            error is ConnectException ->
                ProbeAttempt(full, ProbeAttempt.Outcome.NoConnection, R.string.probe_connection_refused)

            error is SocketTimeoutException ->
                ProbeAttempt(full, ProbeAttempt.Outcome.NoConnection, R.string.probe_timeout)

            error is IOException ->
                if (error.message != null) {
                    ProbeAttempt(full, ProbeAttempt.Outcome.NoConnection, R.string.probe_raw, error.message)
                } else {
                    ProbeAttempt(full, ProbeAttempt.Outcome.NoConnection, R.string.probe_network_error)
                }

            else ->
                ProbeAttempt(
                    full,
                    ProbeAttempt.Outcome.BadResponse,
                    R.string.probe_raw,
                    "${error.javaClass.simpleName}: ${error.message}",
                )
        }
    }

    /**
     * @param photoPrismDetected `/api/v1/status` 가 응답했는가.
     *   이게 true 면 서버는 PhotoPrism 이 맞고 `/config` 경로만 문제인 것이다.
     */
    @StringRes
    fun hintRes(attempts: List<ProbeAttempt>, photoPrismDetected: Boolean): Int {
        val outcomes = attempts.map { it.outcome }.toSet()
        return when {
            photoPrismDetected ->
                R.string.probe_hint_config_blocked

            attempts.any { it.outcome == ProbeAttempt.Outcome.HttpError && it.detailArg == "404" } ->
                R.string.probe_hint_no_api

            attempts.any { it.outcome == ProbeAttempt.Outcome.HttpError && it.detailArg == "401" } ->
                R.string.probe_hint_auth_required

            outcomes == setOf(ProbeAttempt.Outcome.TlsError) ->
                R.string.probe_hint_tls

            outcomes.contains(ProbeAttempt.Outcome.NoConnection) ->
                R.string.probe_hint_no_connection

            else -> R.string.probe_hint_bad_response
        }
    }
}
