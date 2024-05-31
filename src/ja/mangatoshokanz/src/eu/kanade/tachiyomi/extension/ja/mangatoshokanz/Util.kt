package eu.kanade.tachiyomi.extension.ja.mangatoshokanz

import android.util.Base64
import java.net.URLEncoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.RSAKeyGenParameterSpec

internal fun getKeys(): KeyPair {
    return KeyPairGenerator.getInstance("RSA").run {
        initialize(RSAKeyGenParameterSpec(512, RSAKeyGenParameterSpec.F4))
        generateKeyPair()
    }
}

internal fun PublicKey.toPem(): String {
    val base64Encoded = Base64.encodeToString(encoded, Base64.NO_WRAP)
    val urlEncoded = URLEncoder.encode(base64Encoded, "UTF-8")
    val crlfEncoded = "%0D%0A"

    val crlfAdded = StringBuilder(urlEncoded)
        .insert(64, crlfEncoded)
        .toString()

    return StringBuilder("-----BEGIN+PUBLIC+KEY-----")
        .append(crlfEncoded)
        .append(crlfAdded)
        .append(crlfEncoded)
        .append("-----END+PUBLIC+KEY-----")
        .append(crlfEncoded)
        .toString()
}
