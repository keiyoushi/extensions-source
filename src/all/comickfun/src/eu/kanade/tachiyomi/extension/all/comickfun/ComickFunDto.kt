package eu.kanade.tachiyomi.extension.all.comickfun

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

@Serializable
data class SearchManga(
    val hid: String,
    val title: String,
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
data class Manga(
    val comic: Comic,
    val artists: List<Name> = emptyList(),
    val authors: List<Name> = emptyList(),
    val genres: List<Name> = emptyList(),
    val demographic: String? = null,
) {
    fun toSManga(
        includeMuTags: Boolean = false,
        scorePosition: String = "",
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
data class Comic(
    val hid: String,
    val title: String,
    val country: String? = null,
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
data class MdGenres(
    @SerialName("md_genres") val name: Name? = null,
)

@Serializable
data class MuComicCategories(
    @SerialName("mu_comic_categories") val categories: List<MuCategories?> = emptyList(),
)

@Serializable
data class MuCategories(
    @SerialName("mu_categories") val category: Title? = null,
)

@Serializable
data class Covers(
    val md_covers: List<MDcovers> = emptyList(),
)

@Serializable
data class MDcovers(
    val b2key: String?,
    val vol: String? = null,
)

@Serializable
data class Title(
    val title: String?,
)

@Serializable
data class Name(
    val name: String,
)

@Serializable
data class ChapterList(
    val chapters: MutableList<Chapter>,
    val total: Int,
)

@Serializable
data class Chapter(
    val hid: String,
    val lang: String = "",
    val title: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val chap: String = "",
    val vol: String = "",
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
data class PageList(
    val chapter: ChapterPageData,
)

@Serializable
data class ChapterPageData(
    val images: List<Page>,
)

@Serializable
data class Page(
    val url: String? = null,
)
