package eu.kanade.tachiyomi.extension.en.comix

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Term(
    @SerialName("term_id")
    private val termId: Int,
    private val type: String,
    val title: String,
    private val slug: String,
    private val count: Int?,
)

@Serializable
class Manga(
    @SerialName("hash_id")
    private val hashId: String,
    private val title: String,
    @SerialName("alt_titles")
    private val altTitles: List<String>,
    private val synopsis: String?,
    private val type: String,
    private val poster: Poster,
    private val status: String,
    @SerialName("is_nsfw")
    private val isNsfw: Boolean,
    private val author: List<Term>?,
    private val artist: List<Term>?,
    private val genre: List<Term>?,
    private val theme: List<Term>?,
    private val demographic: List<Term>?,
) {
    @Serializable
    class Poster(
        private val small: String,
        private val medium: String,
        private val large: String,
    ) {
        fun from(quality: String?) = when (quality) {
            "large" -> large
            "small" -> small
            else -> medium
        }
    }

    fun toSManga(
        posterQuality: String?,
        altTitlesInDesc: Boolean = false,
    ) = SManga.create().apply {
        url = "/$hashId"
        title = this@Manga.title
        author = this@Manga.author.takeUnless { it.isNullOrEmpty() }?.joinToString { it.title }
        artist = this@Manga.artist.takeUnless { it.isNullOrEmpty() }?.joinToString { it.title }
        description = buildString {
            synopsis.takeUnless { it.isNullOrEmpty() }
                ?.let { append(it) }
            altTitles.takeIf { altTitlesInDesc && it.isNotEmpty() }
                ?.let { altName ->
                    append("\n\n")
                    append("Alternative Names:\n")
                    append(altName.joinToString("\n"))
                }
        }
        initialized = true
        status = when (this@Manga.status) {
            "releasing" -> SManga.ONGOING
            "on_hiatus" -> SManga.ON_HIATUS
            "finished" -> SManga.COMPLETED
            "discontinued" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = this@Manga.poster.from(posterQuality)
        genre = getGenres()
    }

    fun toBasicSManga(posterQuality: String?) = SManga.create().apply {
        url = "/$hashId"
        title = this@Manga.title
        thumbnail_url = this@Manga.poster.from(posterQuality)
    }

    fun getGenres() = buildList {
        when (type) {
            "manhwa" -> add("Manhwa")
            "manhua" -> add("Manhua")
            "manga" -> add("Manga")
            else -> add("Other")
        }
        genre.takeUnless { it.isNullOrEmpty() }?.map { it.title }
            .let { addAll(it ?: emptyList()) }
        theme.takeUnless { it.isNullOrEmpty() }?.map { it.title }
            .let { addAll(it ?: emptyList()) }
        demographic.takeUnless { it.isNullOrEmpty() }?.map { it.title }
            .let { addAll(it ?: emptyList()) }
        if (isNsfw) add("NSFW")
    }.distinct().joinToString()
}

@Serializable
class SingleMangaResponse(
    val result: Manga,
)

@Serializable
class Pagination(
    @SerialName("current_page") val page: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class SearchResponse(
    val result: Items,
) {
    @Serializable
    class Items(
        val items: List<Manga>,
        val pagination: Pagination,
    )
}

@Serializable
class ChapterDetailsResponse(
    val result: Items,
) {
    @Serializable
    class Items(
        val items: List<Chapter>,
        val pagination: Pagination,
    )
}

@Serializable
class Chapter(
    @SerialName("chapter_id")
    private val chapterId: Int,
    @SerialName("scanlation_group_id") val scanlationGroupId: Int,
    val number: Double,
    private val name: String,
    val votes: Int,
    @SerialName("updated_at")
    val updatedAt: Long,
    @SerialName("scanlation_group")
    private val scanlationGroup: ScanlationGroup?,
) {
    @Serializable
    class ScanlationGroup(
        val name: String,
    )

    fun toSChapter(mangaId: String) = SChapter.create().apply {
        url = "title/$mangaId/$chapterId"
        name = buildString {
            append("Chapter ")
            append(this@Chapter.number.toString().removeSuffix(".0"))
            this@Chapter.name.takeUnless { it.isEmpty() }?.let { append(": $it") }
        }
        date_upload = this@Chapter.updatedAt * 1000
        chapter_number = this@Chapter.number.toFloat()
        scanlator = this@Chapter.scanlationGroup?.name ?: "Unknown"
    }
}

@Serializable
class ChapterResponse(
    val result: Items?,
) {
    @Serializable
    class Items(
        @SerialName("chapter_id")
        val chapterId: Int,
        val images: List<String>,
    )
}
