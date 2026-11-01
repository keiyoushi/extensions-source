package eu.kanade.tachiyomi.extension.fr.bdplus

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CorpusDto(val docs: List<CorpusEntry>)

@Serializable
class CorpusEntry(
    private val id: String,
    val title: String,
    private val alias: List<String> = emptyList(),
    private val authors: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val rating: Float? = null,
    @SerialName("nb_tomes") val nbTomes: Int? = null,
    val popularity: Float? = null,
    private val cover: String? = null,
) {
    fun searchTerms() = listOf(title) + alias + authors

    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id
        title = this@CorpusEntry.title
        thumbnail_url = cover?.let { baseUrl + it }
    }
}

@Serializable
class HomeConfigDto(val upd: String)

@Serializable
class UpdatesDto(val g: List<UpdateEntry>)

@Serializable
class UpdateEntry(
    private val s: String,
    private val t: String,
    private val c: String? = null,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = s
        title = t
        thumbnail_url = c?.let { baseUrl + it }
    }
}

@Serializable
class PagesDto(val pages: List<PageDto>)

@Serializable
class PageDto(val url: String)
