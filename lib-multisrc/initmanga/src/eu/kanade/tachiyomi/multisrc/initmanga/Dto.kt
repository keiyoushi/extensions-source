package eu.kanade.tachiyomi.multisrc.initmanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

@Serializable
class Dto(
    val title: String? = null,
    val url: String? = null,
    private val thumb: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        this.title = this@Dto.title?.let { Jsoup.parseBodyFragment(it).text() } ?: ""
        val fullUrl = this@Dto.url.orEmpty()

        val urlPath = try {
            fullUrl.toHttpUrlOrNull()?.encodedPath ?: fullUrl
        } catch (_: Exception) {
            fullUrl
        }
        this.url = urlPath
        thumbnail_url = thumb
    }
}
