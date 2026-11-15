package eu.kanade.tachiyomi.extension.es.lhentaiunoriginal

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

// The site's placeholder for galleries without an artist
private const val UNKNOWN_AUTHOR = "Artista desconocido"

@Serializable
class GalleryDto(
    private val title: String,
    private val author: String? = null,
    private val genres: List<String> = emptyList(),
    private val status: String? = null,
    private val coverUrl: String? = null,
    val pages: Int,
    val sourceId: String,
) {
    fun toSManga(baseUrl: String, info: String?) = SManga.create().apply {
        title = this@GalleryDto.title
        author = this@GalleryDto.author?.takeUnless { it == UNKNOWN_AUTHOR }
        genre = genres.joinToString().ifEmpty { null }
        status = if (this@GalleryDto.status == "Completed") SManga.COMPLETED else SManga.UNKNOWN
        thumbnail_url = coverUrl?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }
        description = MarkdownDescription.Builder()
            .info(info)
            .field("Páginas", pages.toString())
            .build()
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }
}
