package eu.kanade.tachiyomi.extension.all.mangaomega

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.text.Normalizer
import kotlin.time.Instant

@Serializable
class PostDto(
    val id: Int,
    private val slug: String,
    private val title: Rendered,
    @SerialName("date_gmt") private val dateGmt: String,
    private val categories: List<Int>,
    private val content: Rendered,
) {
    fun toCachedPost(): CachedPost? {
        val rawTitle = Parser.unescapeEntities(title.rendered, false).trim()
        val lower = rawTitle.lowercase()
        val lang = when {
            "español" in lower -> "es"
            "english" in lower -> "en"
            CATEGORY_EN in categories -> "en"
            CATEGORY_ES in categories -> "es"
            else -> return null
        }

        // "TITLE【Chapter 12 Part 2】Manga English", or "TITLE 12 Español【Manga Online】" with the number outside
        val prefix = rawTitle.substringBefore('【').trim()
        val bracket = rawTitle.substringAfter('【', "")
        val number: String
        var name: String
        val bracketNumber = numberRegex.find(bracket)
        if (bracketNumber != null) {
            number = bracketNumber.value
            name = prefix.removeSuffix(" $number")
        } else {
            val trailing = trailingNumberRegex.find(prefix) ?: return null
            number = trailing.groupValues[1]
            name = prefix.substring(0, trailing.range.first)
        }
        name = name.replace(trailingWordsRegex, "").trim()

        val document = Jsoup.parseBodyFragment(content.rendered)

        return CachedPost(
            id = id,
            slug = slug,
            lang = lang,
            key = name.toKey(),
            title = name,
            number = number,
            part = partRegex.find(bracket)?.groupValues?.get(1),
            date = Instant.tryParse("${dateGmt}Z"),
            cover = document.selectFirst("img.alignleft")?.attrOrNull("src"),
            hasPages = document.selectFirst("img:not(.alignleft)") != null,
        )
    }
}

@Serializable
class Rendered(val rendered: String)

@Serializable
class ContentDto(val content: Rendered)

@Serializable
class PostCache(
    val syncedAt: Long,
    val posts: List<CachedPost>,
)

@Serializable
class CachedPost(
    val id: Int,
    val slug: String,
    val lang: String,
    val key: String,
    val title: String,
    val number: String,
    val part: String? = null,
    val date: Long,
    val cover: String? = null,
    val hasPages: Boolean,
) {
    val chapterNumber: Float
        // part 1 is the plain chapter, part 2 is .5, later parts stay below the next chapter
        get() = number.toFloat() + (part?.toFloat()?.let { (it - 1) / it } ?: 0f)

    fun toSChapter(chapterLabel: String, partLabel: String) = SChapter.create().apply {
        url = id.toString()
        memo = buildJsonObject { put("slug", slug) }
        name = buildString {
            append(chapterLabel, " ", number)
            part?.let { append(" ", partLabel, " ", it) }
        }
        chapter_number = chapterNumber
        date_upload = date
    }
}

fun String.toKey(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
    .replace(nonAlphanumericRegex, "")

private const val CATEGORY_EN = 5
private const val CATEGORY_ES = 6

private val numberRegex = Regex("""\d+(?:\.\d+)?""")
private val trailingNumberRegex = Regex("""\s+(?:chapter\s+)?(\d+(?:\.\d+)?)(?:\s+(?:english|español))?$""", RegexOption.IGNORE_CASE)
private val trailingWordsRegex = Regex("""(?:\s+(?:chapter|english|español))+$""", RegexOption.IGNORE_CASE)
private val partRegex = Regex("""parte?\s*(\d+)""", RegexOption.IGNORE_CASE)
private val nonAlphanumericRegex = Regex("[^a-z0-9]")
