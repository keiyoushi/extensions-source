package eu.kanade.tachiyomi.extension.ja.comicmeteor

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class ApiTitlesResponse(
    val data: List<ApiTitle>,
)

@Serializable
class ApiTitle(
    private val name: String,
    private val url: String,
    private val thumbnail: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        url = this@ApiTitle.url.toHttpUrl().encodedPath
        thumbnail_url = thumbnail
    }
}

@Serializable
class FilterOption(
    val name: String,
    val key: String,
    val value: String,
)
