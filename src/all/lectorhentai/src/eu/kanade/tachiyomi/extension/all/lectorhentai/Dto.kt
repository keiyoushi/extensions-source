package eu.kanade.tachiyomi.extension.all.lectorhentai

import android.util.Base64
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ReaderBlobDto(
    @SerialName("k") private val key: String,
    private val iv: String,
    @SerialName("c") private val content: String,
) {
    fun decrypt(): String = content.decodeBase64().aesDecrypt(aesGcm(key.decodeBase64(), iv.decodeBase64())).decodeToString()

    private fun String.decodeBase64() = Base64.decode(this, Base64.DEFAULT)
}

@Serializable
class ReaderPagesDto(@SerialName("p") val pages: List<String>)
