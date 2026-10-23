package com.regnius.photoprism.core.network.tls

import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 셀프사인 인증서 승인 대화상자가 뜨려면 감싸인 예외에서도
 * [UntrustedCertificateException] 을 찾아야 한다 (§24).
 */
class FindUntrustedCertificateTest {

    private val untrusted = UntrustedCertificateException(
        fingerprint = "sha256/RApe",
        subject = "CN=192.168.20.87",
        issuer = "CN=192.168.20.87",
    )

    @Test
    fun `finds it wrapped in an SSLHandshakeException, as Android's TLS stack delivers it`() {
        val handshake = SSLHandshakeException("handshake failed").apply { initCause(untrusted) }

        assertSame(untrusted, handshake.findUntrustedCertificate())
    }

    @Test
    fun `finds it several levels down`() {
        val handshake = SSLHandshakeException("handshake failed").apply { initCause(untrusted) }
        val outer = IOException("unexpected end of stream", handshake)

        assertSame(untrusted, outer.findUntrustedCertificate())
    }

    @Test
    fun `finds it when thrown directly`() {
        assertSame(untrusted, untrusted.findUntrustedCertificate())
    }

    @Test
    fun `null for an unrelated TLS failure`() {
        assertNull(SSLHandshakeException("protocol version").findUntrustedCertificate())
    }
}
