package com.regnius.photoprism.core.network.tls

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** 사용자가 명시적으로 승인한 인증서 지문 목록. */
@Singleton
class CertificatePinStore @Inject constructor(context: Context) {

    private val prefs = context.getSharedPreferences("cert_pins", Context.MODE_PRIVATE)

    fun isPinned(fingerprint: String): Boolean = prefs.getStringSet(KEY, emptySet())
        .orEmpty()
        .contains(fingerprint)

    fun pin(fingerprint: String) {
        val updated = prefs.getStringSet(KEY, emptySet()).orEmpty() + fingerprint
        prefs.edit().putStringSet(KEY, updated).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    fun all(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty()

    private companion object { const val KEY = "pins" }
}

/**
 * 신뢰할 수 없는 인증서를 만났을 때 던진다.
 *
 * 그냥 실패시키지 않고 [fingerprint] 와 [subject] 를 실어 보내는 이유는,
 * UI 가 사용자에게 "이 지문을 신뢰하시겠습니까?" 를 물어볼 수 있어야 하기
 * 때문이다. 셀프호스팅에서 셀프사인 인증서는 예외가 아니라 일상이다.
 */
class UntrustedCertificateException(
    val fingerprint: String,
    val subject: String,
    val issuer: String,
    cause: Throwable? = null,
) : CertificateException("untrusted certificate: $subject ($fingerprint)", cause)

/**
 * 시스템 신뢰 저장소를 먼저 쓰고, 실패하면 사용자가 승인한 핀을 확인한다. §4.5
 *
 * **의도적으로 trust-all 을 구현하지 않는다.** 모든 인증서를 무조건 신뢰하는
 * 코드는 한 줄이면 되지만, 그 순간 이 앱은 중간자 공격에 무방비가 되고
 * "토큰이 안전하게 처리된다"는 약속이 거짓이 된다. 대신 사용자가 지문을 직접
 * 보고 승인한 인증서만, 그것도 정확히 그 인증서만 신뢰한다.
 */
class UserPinnedTrustManager(
    private val pinStore: CertificatePinStore,
) : X509TrustManager {

    private val systemTrustManager: X509TrustManager = run {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as java.security.KeyStore?)
        factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        systemTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        try {
            systemTrustManager.checkServerTrusted(chain, authType)
            return
        } catch (systemFailure: CertificateException) {
            val leaf = chain.firstOrNull() ?: throw systemFailure
            val fingerprint = leaf.sha256Fingerprint()
            if (pinStore.isPinned(fingerprint)) return
            throw UntrustedCertificateException(
                fingerprint = fingerprint,
                subject = leaf.subjectX500Principal.name,
                issuer = leaf.issuerX500Principal.name,
                cause = systemFailure,
            )
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = systemTrustManager.acceptedIssuers
}

/**
 * 호스트명 검증. 사용자가 핀한 인증서라면 호스트명 불일치도 허용한다.
 *
 * 셀프사인 인증서는 CN 이 `localhost` 나 IP 로 발급된 경우가 흔해서, 핀을
 * 승인했는데도 호스트명 검증에서 다시 막히면 사용자는 원인을 알 수 없다.
 * 핀 승인은 "이 특정 인증서를 이 서버로 인정한다"는 의사표시이므로 여기까지
 * 포함하는 것이 맞다.
 */
class PinAwareHostnameVerifier(
    private val pinStore: CertificatePinStore,
    private val delegate: HostnameVerifier,
) : HostnameVerifier {

    override fun verify(hostname: String, session: SSLSession): Boolean {
        if (delegate.verify(hostname, session)) return true
        val leaf = runCatching {
            session.peerCertificates.firstOrNull() as? X509Certificate
        }.getOrNull() ?: return false
        return pinStore.isPinned(leaf.sha256Fingerprint())
    }
}

/** `sha256/BASE64` — OkHttp CertificatePinner 와 같은 표기법. */
fun X509Certificate.sha256Fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
    return "sha256/" + Base64.encodeToString(digest, Base64.NO_WRAP)
}

/** 사람이 눈으로 대조하기 좋은 16진 표기 — `AB:CD:EF:...` */
fun X509Certificate.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded)
        .joinToString(":") { "%02X".format(it) }
