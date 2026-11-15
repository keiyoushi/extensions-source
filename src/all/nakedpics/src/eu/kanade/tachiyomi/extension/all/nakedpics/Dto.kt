package eu.kanade.tachiyomi.extension.all.nakedpics

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
class GalleryDto(
    @SerialName("g_url") private val url: String,
    private val desc: String,
    @SerialName("t_url_460") private val thumbnailUrl: String,
    private val gid: String,
) {
    // Null for galleries on partner sites, which related lists carry
    fun toSManga(baseUrl: HttpUrl): SManga? {
        val galleryUrl = baseUrl.resolve(url)?.takeIf { it.host == baseUrl.host && it.pathSegments[0] == "gals" } ?: return null
        return SManga.create().apply {
            url = galleryUrl.encodedPath
            title = desc.removeSuffix(" $gid")
            thumbnail_url = thumbnailUrl
        }
    }
}

@Serializable
class FilterData(val categories: List<CategoryOption>)

@Serializable
class CategoryOption(val name: String, val path: String)
