package eu.kanade.tachiyomi.extension.ja.kisslove

import eu.kanade.tachiyomi.extension.ja.kisslove.KissLove.Companion.DATE_FORMATTER
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup

@Serializable
data class PagedManga(
    val currentPage: Long,
    val items: List<Manga>,
    val totalItems: Long,
    val totalPages: Long,
)

@Serializable
data class Manga(
    val artists: String?,
    val authors: String,
    val chapters: List<Chapter> = emptyList(),
    val cover: String,
    @SerialName("daily_views") val dailyViews: Long?,
    val description: String = "",
    @SerialName("genres") val genresRaw: JsonElement? = null,
    @SerialName("group_uploader") val groupUploader: Long?,
    val hidden: Int?,
    val id: Long,
    @SerialName("last_chapter") val lastChapter: Double,
    @SerialName("last_update") val lastUpdate: String?,
    @SerialName("m_status") val mStatus: Int,
    val name: String,
    @SerialName("other_name") val otherName: String,
    val released: Int?,
    val showads: Int?,
    val slug: String,
    val submitter: Long?,
    val total: Long?,
    @SerialName("trans_group") val transGroup: String,
    val views: Long,
    @SerialName("vote_count") val voteCount: Long?,
) {
    fun toSManga(useRomajiTitle: Boolean = true): SManga {
        val (mainName, extraName) = if (useRomajiTitle) {
            name to otherName
        } else {
            val parts = otherName.split(Regex(",\\s*"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val (jpParts, nonJpParts) = parts.partition { hasJapaneseKana(it) }

            val mainTitle = if (jpParts.isNotEmpty()) {
                jpParts.maxByOrNull { it.length } ?: jpParts.first()
            } else {
                parts.firstOrNull() ?: name
            }

            val extra = buildExtraName(mainTitle, name, jpParts, nonJpParts)

            mainTitle to extra
        }

        val genres = when (val raw = genresRaw) {
            is JsonArray -> raw.map { it.jsonPrimitive.content }
            is JsonPrimitive -> raw.content.split(",")
            else -> emptyList()
        }

        return SManga.create().apply {
            url = this@Manga.slug
            title = mainName
            artist = this@Manga.artists
            author = this@Manga.authors
            description = "${Jsoup.parse(this@Manga.description).text()}\n\n$extraName"
            thumbnail_url = this@Manga.cover
            genre = genres.joinToString { it.trim() }
            status = if (mStatus == 1) SManga.COMPLETED else SManga.ONGOING
        }
    }

    private fun hasJapaneseKana(text: String): Boolean {
        val kanaRegex = Regex("[\\p{Script=Hiragana}\\p{Script=Katakana}]")
        return kanaRegex.containsMatchIn(text)
    }

    private fun buildExtraName(
        mainTitle: String,
        originalName: String,
        jpParts: List<String>,
        nonJpParts: List<String>,
    ): String {
        val extraParts = mutableListOf<String>()

        extraParts.add(originalName)

        extraParts.addAll(nonJpParts)

        jpParts
            .filter { it != mainTitle }
            .forEach { extraParts.add(it) }

        return extraParts
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")
    }
}

@Serializable
data class Chapter(
    val chapter: Double,
    val content: String = "",
    val hidden: Int?,
    val id: Long,
    @SerialName("last_update") val lastUpdate: String,
    val manga: String?,
    val name: String?,
    val showads: Int?,
    val submitter: Long?,
    val views: Long?,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        url = "$id/$slug-chapter-$chapter"
        name = this@Chapter.name?.takeIf { it.isNotBlank() } ?: "Chapter $chapter"

        date_upload = runCatching {
            DATE_FORMATTER.tryParse(lastUpdate)
        }.getOrDefault(0L)
    }
}

@Serializable
data class Genre(
    val name: String,
)
