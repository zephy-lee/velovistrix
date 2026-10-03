package com.regnius.photoprism.core.auth

import com.regnius.photoprism.R
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 실패 진단은 사용자가 다음에 무엇을 할지 정하는 유일한 단서다.
 * 분류가 틀리면 엉뚱한 조치를 안내하게 되므로 테스트로 고정한다.
 *
 * 검증 대상은 **어떤 처방을 고르는가**(리소스 ID)이지 그 문장이 아니다 —
 * 문장은 로케일이 정하는 값이라 여기서 고정할 것이 못 된다(§11.8).
 */
class ProbeDiagnosticsTest {

    private val base = "http://192.168.0.10:2342/".toHttpUrl()

    private fun httpException(code: Int): retrofit2.HttpException {
        val raw = Response.Builder()
            .code(code).message("err").protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://x/").build())
            .build()
        return retrofit2.HttpException(
            retrofit2.Response.error<Any>("".toResponseBody("text/plain".toMediaType()), raw)
        )
    }

    @Test
    fun `시도한 전체 URL 을 그대로 기록한다`() {
        // 사용자가 눈으로 확인할 값이므로 base 가 아니라 실제 요청 URL 이어야 한다.
        val attempt = ProbeDiagnostics.classify(base, httpException(404))
        assertEquals("http://192.168.0.10:2342/api/v1/config", attempt.url)
        assertEquals(R.string.probe_http_error, attempt.detailRes)
        assertEquals("404", attempt.detailArg)
    }

    @Test
    fun `오류 종류를 구분한다`() {
        assertEquals(ProbeAttempt.Outcome.HttpError, ProbeDiagnostics.classify(base, httpException(404)).outcome)
        assertEquals(ProbeAttempt.Outcome.TlsError, ProbeDiagnostics.classify(base, SSLException("bad")).outcome)
        assertEquals(ProbeAttempt.Outcome.NoConnection, ProbeDiagnostics.classify(base, ConnectException()).outcome)
        assertEquals(ProbeAttempt.Outcome.NoConnection, ProbeDiagnostics.classify(base, UnknownHostException()).outcome)
        assertEquals(ProbeAttempt.Outcome.NoConnection, ProbeDiagnostics.classify(base, SocketTimeoutException()).outcome)
    }

    @Test
    fun `404 는 주소는 살아있고 경로나 포트가 틀렸다고 안내한다`() {
        // 사용자가 실제로 겪은 증상. 연결은 되므로 "네트워크 확인" 안내는 틀렸다.
        // 연결은 됐으므로 "네트워크 확인" 안내는 틀렸다 — 경로·포트를 짚어야 한다.
        assertEquals(
            R.string.probe_hint_no_api,
            ProbeDiagnostics.hintRes(
                listOf(ProbeDiagnostics.classify(base, httpException(404))),
                photoPrismDetected = false,
            ),
        )
    }

    @Test
    fun `status 가 응답하면 PhotoPrism 은 있다고 안내한다`() {
        // config 만 404 인 경우 — 처방이 완전히 다르다.
        assertEquals(
            R.string.probe_hint_config_blocked,
            ProbeDiagnostics.hintRes(
                listOf(ProbeDiagnostics.classify(base, httpException(404))),
                photoPrismDetected = true,
            ),
        )
    }

    @Test
    fun `TLS 오류만 있으면 http 명시를 제안한다`() {
        assertEquals(
            R.string.probe_hint_tls,
            ProbeDiagnostics.hintRes(
                listOf(ProbeDiagnostics.classify(base, SSLException("Unable to parse TLS packet header"))),
                photoPrismDetected = false,
            ),
        )
    }

    @Test
    fun `연결 실패는 네트워크 확인을 안내한다`() {
        assertEquals(
            R.string.probe_hint_no_connection,
            ProbeDiagnostics.hintRes(
                listOf(ProbeDiagnostics.classify(base, ConnectException("refused"))),
                photoPrismDetected = false,
            ),
        )
    }

    @Test
    fun `401 은 프록시 인증을 의심한다`() {
        assertEquals(
            R.string.probe_hint_auth_required,
            ProbeDiagnostics.hintRes(
                listOf(ProbeDiagnostics.classify(base, httpException(401))),
                photoPrismDetected = false,
            ),
        )
    }
}
