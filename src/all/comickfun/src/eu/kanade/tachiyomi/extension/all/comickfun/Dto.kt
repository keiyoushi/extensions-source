package eu.kanade.tachiyomi.extension.all.comickfun

import eu.kanade.tachiyomi.extension.all.comickfun.Comick.Companion.INCLUDE_MU_TAGS_DEFAULT
import eu.kanade.tachiyomi.extension.all.comickfun.Comick.Companion.SCORE_POSITION_DEFAULT
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

@Serializable
class SearchManga(
    private val hid: String,
    private val title: String,
    @SerialName("md_covers") val mdCovers: List<MDcovers> = emptyList(),
    @SerialName("cover_url") val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        // appending # at end as part of migration from slug to hid
        url = "/comic/$hid#"
        title = this@SearchManga.title
        thumbnail_url = parseCover(cover, mdCovers)
    }
}

@Serializable
class Manga(
    val comic: Comic,
    private val artists: List<Name> = emptyList(),
    private val authors: List<Name> = emptyList(),
    private val genres: List<Name> = emptyList(),
    private val demographic: String? = null,
) {
    fun toSManga(
        includeMuTags: Boolean = INCLUDE_MU_TAGS_DEFAULT,
        scorePosition: String = SCORE_POSITION_DEFAULT,
        covers: List<MDcovers>? = null,
    ) =
        SManga.create().apply {
            // appennding # at end as part of migration from slug to hid
            url = "/comic/${comic.hid}#"
            title = comic.title
            description = buildString {
                if (scorePosition == "top") append(comic.fancyScore)
                val desc = comic.desc?.beautifyDescription()
                if (!desc.isNullOrEmpty()) {
                    if (this.isNotEmpty()) append("\n\n")
                    append(desc)
                }
                if (scorePosition == "middle") {
                    if (this.isNotEmpty()) append("\n\n")
                    append(comic.fancyScore)
                }
                if (comic.altTitles.isNotEmpty()) {
                    if (this.isNotEmpty()) append("\n\n")
                    append("Alternative Titles:\n")
                    append(
                        comic.altTitles.mapNotNull { title ->
                            title.title?.let { "• $it" }
                        }.joinToString("\n"),
                    )
                }
                if (scorePosition == "bottom") {
                    if (this.isNotEmpty()) append("\n\n")
                    append(comic.fancyScore)
                }
            }

            status = comic.status.parseStatus(comic.translationComplete)
            thumbnail_url = parseCover(
                comic.cover,
                covers ?: comic.mdCovers,
            )
            artist = artists.joinToString { it.name.trim() }
            author = authors.joinToString { it.name.trim() }
            genre = buildList {
                comic.origination?.let(::add)
                demographic?.let { add(Name(it)) }
                addAll(genres)
                addAll(comic.mdGenres.mapNotNull { it.name })
                if (includeMuTags) {
                    comic.muGenres.categories.forEach { category ->
                        category?.category?.title?.let { add(Name(it)) }
                    }
                }
            }
                .distinctBy { it.name }
                .filter { it.name.isNotBlank() }
                .joinToString { it.name.trim() }
        }
}

@Serializable
class Comic(
    val hid: String,
    val title: String,
    private val country: String? = null,
    val slug: String? = null,
    @SerialName("md_titles") val altTitles: List<Title> = emptyList(),
    val desc: String? = null,
    val status: Int? = 0,
    @SerialName("translation_completed") val translationComplete: Boolean? = true,
    @SerialName("md_covers") val mdCovers: List<MDcovers> = emptyList(),
    @SerialName("cover_url") val cover: String? = null,
    @SerialName("md_comic_md_genres") val mdGenres: List<MdGenres>,
    @SerialName("mu_comics") val muGenres: MuComicCategories = MuComicCategories(emptyList()),
    @SerialName("bayesian_rating") val score: String? = null,
) {
    val origination = when (country) {
        "jp" -> Name("Manga")
        "kr" -> Name("Manhwa")
        "cn" -> Name("Manhua")
        else -> null
    }
    val fancyScore: String = if (score.isNullOrEmpty()) {
        ""
    } else {
        val stars = score.toBigDecimal().div(BigDecimal(2))
            .setScale(0, RoundingMode.HALF_UP).toInt()
        buildString {
            append("★".repeat(stars))
            if (stars < 5) append("☆".repeat(5 - stars))
            append(" $score")
        }
    }
}

@Serializable
class MdGenres(
    @SerialName("md_genres") val name: Name? = null,
)

@Serializable
class MuComicCategories(
    @SerialName("mu_comic_categories") val categories: List<MuCategories?> = emptyList(),
)

@Serializable
class MuCategories(
    @SerialName("mu_categories") val category: Title? = null,
)

@Serializable
class Covers(
    @SerialName("md_covers") val mdCovers: List<MDcovers> = emptyList(),
)

@Serializable
class MDcovers(
    val b2key: String?,
    val vol: String? = null,
    val locale: String? = null,
)

@Serializable
class Title(
    val title: String?,
)

@Serializable
class Name(
    val name: String,
)

@Serializable
class ChapterList(
    val chapters: List<Chapter>,
)

@Serializable
class Chapter(
    private val hid: String,
    private val lang: String = "",
    private val title: String = "",
    @SerialName("created_at") private val createdAt: String = "",
    @SerialName("publish_at") val publishedAt: String = "",
    private val chap: String = "",
    private val vol: String = "",
    @SerialName("group_name") val groups: List<String> = emptyList(),
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        url = "$mangaUrl/$hid-chapter-$chap-$lang"
        name = beautifyChapterName(vol, chap, title)
        date_upload = createdAt.parseDate()
        scanlator = groups.joinToString().takeUnless { it.isBlank() } ?: "Unknown"
    }
}

@Serializable
class PageList(
    val chapter: ChapterPageData,
)

@Serializable
class ChapterPageData(
    val images: List<Page>,
)

@Serializable
class Page(
    val url: String? = null,
)

@Serializable
class Error(
    val statusCode: Int,
    val message: String,
)
