package eu.kanade.tachiyomi.extension.all.achymanga

import android.util.Base64
import keiyoushi.utils.parseAs
import keiyoushi.utils.xorInPlace
import kotlinx.serialization.Serializable

@Serializable
class ManifestDto(private val d: String) {
    fun pageUrls(key: ByteArray): List<String> = Base64.decode(d.replace('-', '+').replace('_', '/'), Base64.DEFAULT)
        .xorInPlace(key)
        .decodeToString()
        .parseAs()
}

@Serializable
class SelectDto(
    val name: String,
    val label: String,
    val options: List<Pair<String, String>>,
)
