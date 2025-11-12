package eu.kanade.tachiyomi.extension.en.comix
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.collections.distinct
import kotlin.text.isEmpty

@Serializable
class Metadata(
    val genres: List<Name>,
    val tags: List<Name>,
) {
    @Serializable
    class Name(
        val name: String,
        val slug: String,
    )
}

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
    @SerialName("term_ids")
    private val termIds: List<Int>,
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

    fun toSManga(posterQuality: String?) = SManga.create().apply {
        url = "/$hashId"
        title = this@Manga.title
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

    fun getGenres() = buildList {
        when (type) {
            "manhwa" -> add("Manhwa")
            "manhua" -> add("Manhua")
            else -> add("Manga")
        }
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
    val from: Int,
    val to: Int,
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

    fun toSChapter() = SChapter.create().apply {
        url = "/chapter/$chapterId"
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
