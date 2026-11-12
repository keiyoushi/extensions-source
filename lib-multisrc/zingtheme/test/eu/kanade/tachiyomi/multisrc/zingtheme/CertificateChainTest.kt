package eu.kanade.tachiyomi.multisrc.zingtheme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class CertificateChainTest {
    @Test
    fun bundledIntermediateHasTheVerifiedFingerprint() {
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(ZingTheme.missingIntermediate.encoded)
            .joinToString("") { "%02x".format(it) }
        assertEquals("a883559231f8388daf35ce41c8101040ae8fd9b656434247b9475af592cc08ca", fingerprint)
    }

    @Test
    fun completionStillRejectsAChainThePlatformRejects() {
        val rejecting = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException("untrusted client")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException("untrusted server")
        }
        val manager = ChainCompletingTrustManager(rejecting, ZingTheme.missingIntermediate)
        assertThrows(CertificateException::class.java) { manager.checkServerTrusted(emptyArray(), "RSA") }
        assertThrows(CertificateException::class.java) { manager.checkClientTrusted(emptyArray(), "RSA") }
    }
}
