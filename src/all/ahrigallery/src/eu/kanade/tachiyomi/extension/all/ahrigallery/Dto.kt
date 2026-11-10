package eu.kanade.tachiyomi.extension.all.ahrigallery

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.parser.Parser
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class ComicData(
    @SerialName("title") private val englishTitle: String,
    @SerialName("title_jp") private val japaneseTitle: String?,
    @SerialName("num_fav") private val favorites: String?,
    private val score: String?,
    private val turnout: String?,
    @SerialName("post_date") private val postDate: String?,
    private val imgs: List<ComicImage>,
    private val tags: List<Tag>,
    private val category: List<Name>,
    private val language: List<Name>,
) {
    // Listings show the Japanese title when the gallery has one
    val title get() = Parser.unescapeEntities(japaneseTitle ?: englishTitle, false)

    fun toSManga(manga: SManga, cover: String?) = manga.apply {
        val tagNames = tags.groupBy({ it.type }, { it.name }).mapValues { it.value.distinct() }
        val votes = turnout?.toIntOrNull() ?: 0
        title = this@ComicData.title
        artist = tagNames["artist"]?.joinToString()
        author = (tagNames["group"] ?: tagNames["artist"])?.joinToString()
        genre = (category.map { it.name } + GENRE_TYPES.flatMap { tagNames[it].orEmpty() }).joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .rating(score?.toDoubleOrNull()?.takeIf { votes > 0 }, votes = votes)
            .info(language.firstOrNull()?.name, "${imgs.size} pages")
            .field("Favorites", favorites)
            .field("Parodies", tagNames["parody"].orEmpty())
            .field("Characters", tagNames["character"].orEmpty())
            .altTitles(listOfNotNull(Parser.unescapeEntities(englishTitle, false).takeIf { japaneseTitle != null }))
            .build()
        thumbnail_url = cover
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toSChapter(url: String) = SChapter.create().apply {
        this.url = url
        name = "Gallery"
        date_upload = DATE_FORMAT.tryParseDateTime(postDate, ZoneOffset.UTC)
    }

    companion object {
        private val GENRE_TYPES = listOf("female", "male", "mixed", "other")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    }
}

@Serializable
class ComicImage

@Serializable
class Tag(val type: String, val name: String)

@Serializable
class Name(val name: String)

@Serializable
class ReaderImage(val sort: String, private val extension: String) {
    // The reader asks for .jpg unless the image is a gif, webp or avif
    val fileExtension get() = extension.lowercase().takeIf { it in listOf("gif", "webp", "avif") } ?: "jpg"
}
