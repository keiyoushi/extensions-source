package eu.kanade.tachiyomi.extension.fr.mangakawaii

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Dto(
    @SerialName("page_image") private val pageImage: String,
    private val external: Int = 0,
    @SerialName("page_version") private val pageVersion: Long = 0L,
) {
    fun getImageUrl(cdnUrl: String, mangaSlug: String, appLocale: String, chapterSlug: String): String = if (external == 1) {
        pageImage
    } else {
        val versionQuery = if (pageVersion > 0L) "?$pageVersion" else ""
        "$cdnUrl/uploads/manga/$mangaSlug/chapters_$appLocale/$chapterSlug/$pageImage$versionQuery"
    }
}
