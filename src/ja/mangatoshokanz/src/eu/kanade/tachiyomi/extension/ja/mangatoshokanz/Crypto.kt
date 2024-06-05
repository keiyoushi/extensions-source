package eu.kanade.tachiyomi.extension.ja.mangatoshokanz

import android.util.Base64
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.RSAKeyGenParameterSpec
import javax.crypto.Cipher

private val json: Json by injectLazy()

internal fun getKeys(): KeyPair {
    return KeyPairGenerator.getInstance("RSA").run {
        initialize(RSAKeyGenParameterSpec(512, RSAKeyGenParameterSpec.F4))
        generateKeyPair()
    }
}

internal fun PublicKey.toPem(): String {
    val base64Encoded = Base64.encodeToString(encoded, Base64.DEFAULT)

    return StringBuilder("-----BEGIN PUBLIC KEY-----")
        .appendLine()
        .append(base64Encoded)
        .append("-----END PUBLIC KEY-----")
        .toString()
}

internal fun Response.decryptPages(privateKey: PrivateKey): Decrypted {
    val encrypted = json.decodeFromString<Encrypted>(body.string())

    val biDecoded = Base64.decode(encrypted.bi, Base64.DEFAULT)
    val ekDecoded = Base64.decode(encrypted.ek, Base64.DEFAULT)

    val ekDecrypted = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
        init(Cipher.DECRYPT_MODE, privateKey)
        doFinal(ekDecoded)
    }
    val dataDecrypted = CryptoAES.decrypt(encrypted.data, ekDecrypted, biDecoded)

    return json.decodeFromString<Decrypted>(dataDecrypted)
}

@Serializable
private class Encrypted(
    val bi: String,
    val ek: String,
    val data: String,
)

@Serializable
internal class Decrypted(
    @SerialName("Images")
    val images: List<Image>,
    @SerialName("Location")
    val location: Location,
) {
    @Serializable
    internal class Image(
        val file: String,
    )

    @Serializable
    internal class Location(
        val base: String,
        val st: String,
    )
}
