package eu.kanade.tachiyomi.extension.all.dlsite

import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

// The key pair DLsite Play's viewer has the page key wrapped for: RSA-OAEP with SHA-256, MGF1 included
class ViewerKeys {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(4096) }.generateKeyPair()

    val publicKey: String get() = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

    // The page key, as the hex text the server wrapped
    fun unwrap(wrapped: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private, OAEP_SHA256)
        return String(cipher.doFinal(Base64.decode(wrapped, Base64.DEFAULT)), Charsets.US_ASCII)
    }

    companion object {
        private val OAEP_SHA256 = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
    }
}
