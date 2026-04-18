package eu.kanade.tachiyomi.extension.es.enchiladascan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Catalog(
    val items: List<Manga>,
)

@Serializable
class Manga(
    val title: String,
    @SerialName("post_url") private val postUrl: String,
    @SerialName("portada") private val coverUrl: String,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = this@Manga.title
        url = postUrl
        thumbnail_url = baseUrl + coverUrl
    }
}
