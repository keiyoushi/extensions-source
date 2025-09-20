package eu.kanade.tachiyomi.extension.en.kagane

import android.util.Base64
import java.security.MessageDigest

fun ByteArray.toBase64(): String {
    return Base64.encodeToString(this, Base64.NO_WRAP)
}

fun String.sha256(): ByteArray {
    return MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray())
}
