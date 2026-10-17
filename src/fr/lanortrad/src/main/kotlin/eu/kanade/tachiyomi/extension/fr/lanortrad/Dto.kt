package eu.kanade.tachiyomi.extension.fr.lanortrad

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class Dto(
    val id: String,
    val title: String,
    val type: String = "",
    val lastUpdate: String = "",
    private val genres: List<String> = emptyList(),
    private val status: String = "",
    private val description: String = "",
    private val cover: String = "",
    private val author: String = "",
    private val artist: String = "",
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        url = this@Dto.id
        title = this@Dto.title
        thumbnail_url = this@Dto.cover.let {
            if (it.startsWith("http")) it else "$baseUrl/${it.removePrefix("/")}"
        }
        author = this@Dto.author
        artist = this@Dto.artist
        description = this@Dto.description
        genre = this@Dto.genres.filterNot { it == "Collaboration" }.joinToString()
        status = when (this@Dto.status.lowercase()) {
            "en cours" -> SManga.ONGOING
            "terminé" -> SManga.COMPLETED
            "en pause" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}
