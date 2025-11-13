package eu.kanade.tachiyomi.extension.en.comix

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class TermResponse(
    val status: Int,
    val result: Items,
) {
    @Serializable
    class Items(
        val items: List<Term>,
        val pagination: Pagination,
    )
}

@Serializable
data class Term(
    @SerialName("term_id")
    val termId: Int,
    val type: String,
    val title: String,
    val slug: String,
    val count: Int,
)

@Serializable
class Manga(
    @SerialName("manga_id")
    private val mangaId: Int,
    @SerialName("hash_id")
    private val hashId: String,
    private val title: String,
    @SerialName("alt_titles")
    private val altTitles: List<String>,
    private val synopsis: String,
    private val slug: String,
    private val type: String,
    private val poster: Poster,
    @SerialName("original_language")
    private val originalLanguage: String?,
    private val status: String,
    @SerialName("final_chapter")
    private val finalChapter: Double,
    @SerialName("has_chapters")
    private val hasChapters: Boolean,
    @SerialName("latest_chapter")
    private val latestChapter: Double,
    @SerialName("chapter_updated_at")
    private val chapterUpdatedAt: Long,
    @SerialName("end_date")
    private val endDate: String,
    @SerialName("created_at")
    private val createdAt: Long,
    @SerialName("updated_at")
    private val updatedAt: Long,
    @SerialName("rated_avg")
    private val ratedAvg: Double,
    @SerialName("rated_count")
    private val ratedCount: Int,
    @SerialName("follows_total")
    private val followsTotal: Int,
    private val links: Links,
    @SerialName("is_nsfw")
    private val isNsfw: Boolean,
    @SerialName("term_ids") val termIds: List<Int>,
) {
    @Serializable
    class Poster(
        val small: String,
        val medium: String,
        val large: String,
    ) {
        fun from(quality: String?) = when (quality) {
            "large" -> large
            "small" -> small
            else -> medium
        }
    }

    @Serializable
    class Links(
        private val al: String?,
        private val mal: String?,
        private val mu: String?,
    )

    fun toSManga(posterQuality: String?, terms: List<Term> = emptyList()) = SManga.create().apply {
        url = "/$hashId"
        title = this@Manga.title
        author = terms.takeUnless { it.isEmpty() }?.filter { it.type == "author" }
            ?.joinToString { it.title }
        artist = terms.takeUnless { it.isEmpty() }?.filter { it.type == "artist" }
            ?.joinToString { it.title }
        description = buildString {
            synopsis.takeUnless { it.isEmpty() }
                ?.let { append(it) }
            altTitles.takeUnless { it.isEmpty() }
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
        genre = getGenres(terms)
    }

    fun toBasicSManga(posterQuality: String?) = SManga.create().apply {
        url = "/$hashId"
        title = this@Manga.title
        thumbnail_url = this@Manga.poster.from(posterQuality)
    }

    fun getGenres(terms: List<Term> = emptyList()) = buildList {
        when (type) {
            "manhwa" -> add("Manhwa")
            "manhua" -> add("Manhua")
            "manga" -> add("Manga")
            else -> add("Other")
        }
        terms.takeUnless { it.isEmpty() }?.filter { it.type == "genre" }?.map { it.title }
            .let { addAll(it ?: emptyList()) }
        terms.takeUnless { it.isEmpty() }?.filter { it.type == "theme" }?.map { it.title }
            .let { addAll(it ?: emptyList()) }
        terms.takeUnless { it.isEmpty() }?.filter { it.type == "demographic" }?.map { it.title }
    }.distinct().joinToString()
}

@Serializable
class SingleMangaResponse(
    val status: Int,
    val result: Manga,
)

@Serializable
class Pagination(
    val count: Int,
    @SerialName("total")
    val totalCount: Int,
    @SerialName("per_page")
    val perPage: Int,
    @SerialName("current_page")
    val page: Int,
    @SerialName("last_page")
    val lastPage: Int,
    val from: Int?,
    val to: Int?,
)

@Serializable
class SearchResponse(
    val status: Int,
    val result: Items,
) {
    @Serializable
    class Items(
        val items: List<Manga>,
        val pagination: Pagination,
    )
}

@Serializable
class ChapterResponse(
    val status: Int,
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
    val chapterId: Int,
    @SerialName("manga_id")
    val mangaId: Int,
    @SerialName("scanlation_group_id")
    val scanlationGroupId: Int,
    val number: Double,
    val name: String,
    val language: String,
    val volume: Int,
    val votes: Int,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("updated_at")
    val updatedAt: Long,
    @SerialName("scanlation_group")
    val scanlationGroup: ScanlationGroup?,
) {
    @Serializable
    class ScanlationGroup(
        @SerialName("scanlation_group_id")
        val scanlationGroupId: Int,
        val name: String,
        val slug: String,
    )

    fun toSChapter(mangaId: String) = SChapter.create().apply {
        url = "title/$mangaId/$chapterId"
        name = buildString {
            append("Chapter ")
            append(this@Chapter.number.toString().removeSuffix(".0"))
            this@Chapter.name.takeUnless { it.isEmpty() }?.let { append(" - $it") }
        }
        date_upload = this@Chapter.updatedAt * 1000
        chapter_number = this@Chapter.number.toFloat()
        scanlator = this@Chapter.scanlationGroup?.name ?: "Unknown"
    }
}
