package eu.kanade.tachiyomi.extension.ja.mangaraw

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

@Serializable
class MangaListResponse(
    val list: List<MangaListItem>,
)

@Serializable
class MangaListItem(
    private val id: Long,
    private val name: String,
    @SerialName("alt_names") private val altNames: String?,
    private val slug: String,
    private val views: Long,
    private val thumbnail: String,
    @SerialName("c_name") private val cName: Double?,
    @SerialName("c_title") private val cTitle: String?,
    @SerialName("c_published") private val cPublished: String?,
    @SerialName("is_adult") private val isAdult: String,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = thumbnail
    }

    fun hasNavigableMangaSlug(): Boolean {
        val value = slug.trim()
        return value.isNotEmpty() &&
            !value.contains('/') &&
            !value.contains("-ch-", ignoreCase = true) &&
            MANGA_SLUG_SUFFIX.matcher(value).find()
    }

    fun getSlugKey(): String = slug.trim().lowercase(Locale.ROOT)

    fun getTitleKey(): String = name.trim().lowercase(Locale.ROOT)

    fun getViews(): Long = views

    fun toSMangaWithInfo() = toSManga().apply {
        description = buildString {
            if (!altNames.isNullOrBlank()) {
                append("Alternative names: $altNames\n")
            }
            append("Views: $views")
            if (cPublished != null) {
                append("\nLast updated: ${dateFormat.tryParse(cPublished)?.let { dateFormatOutput.format(it) } ?: cPublished.take(10)}")
            }
        }
    }

    fun matchesQuery(query: String): Boolean = name.contains(query, ignoreCase = true) ||
        altNames?.contains(query, ignoreCase = true) == true

    fun isAdult(): Boolean = isAdult == "yes"

    companion object {
        private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT) }
        private val dateFormatOutput by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT) }
        private val MANGA_SLUG_SUFFIX = Pattern.compile("-[0-9]+$")
    }
}

@Serializable
class MangaDetailsResponse(
    val pageProps: PageProps,
)

@Serializable
class PageProps(
    val data: MangaDetailsData,
)

@Serializable
class MangaDetailsData(
    val manga: MangaDetails,
)

@Serializable
class MangaDetails(
    private val id: Long,
    private val name: String,
    private val description: String?,
    private val content: String?,
    private val type: String?,
    private val views: Long,
    private val thumbnail: String,
    private val slug: String,
    private val genres: List<Genre>?,
    @SerialName("updated_at") private val updatedAt: String?,
    private val names: List<MangaName>?,
    val chapters: List<Chapter>,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = thumbnail
        description = buildFullDescription()
        author = "Unknown author"
        genre = genres?.joinToString { it.name }
        status = parseStatus(type)
    }

    private fun buildFullDescription(): String {
        val descriptionParts = mutableListOf<String>()

        val synopsis = parseDescription(content ?: description)
        if (synopsis != "No description available.") {
            descriptionParts.add(synopsis)
        }

        if (!names.isNullOrEmpty()) {
            val altNames = names.filter { it.name != name }.map { it.name }
            if (altNames.isNotEmpty()) {
                descriptionParts.add("Alternative titles: ${altNames.joinToString(", ")}")
            }
        }

        descriptionParts.add("Views: $views")

        if (!updatedAt.isNullOrBlank()) {
            val formattedDate = dateFormat.tryParse(updatedAt)?.let { dateFormatOutput.format(it) } ?: updatedAt.take(10)
            descriptionParts.add("Last updated: $formattedDate")
        }

        return descriptionParts.joinToString("\n\n").ifEmpty { "No description available." }
    }

    private fun parseDescription(descriptionJson: String?): String {
        if (descriptionJson.isNullOrBlank()) return "No description available."

        return try {
            val json = org.json.JSONObject(descriptionJson)
            val blocks = json.getJSONArray("blocks")
            val textBuilder = StringBuilder()

            for (i in 0 until blocks.length()) {
                val block = blocks.getJSONObject(i)
                if (block.getString("type") == "paragraph") {
                    val text = block.getJSONObject("data").getString("text")
                    if (textBuilder.isNotEmpty()) textBuilder.append("\n\n")
                    textBuilder.append(text)
                }
            }

            textBuilder.toString().ifEmpty { "No description available." }
        } catch (e: Exception) {
            descriptionJson.takeIf { it.length < 500 } ?: "Description available."
        }
    }

    private fun parseStatus(type: String?): Int = when (type?.lowercase()) {
        "ongoing", "incomplete" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    companion object {
        private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT) }
        private val dateFormatOutput by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT) }
    }
}

@Serializable
class Genre(
    val name: String,
)

@Serializable
class MangaName(
    val name: String,
)

@Serializable
class Chapter(
    private val id: Long,
    private val name: Double,
    private val title: String?,
    @SerialName("updated_at") private val updatedAt: String,
    private val path: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = path
        val chapterNum = this@Chapter.name.toString().let {
            if (it.endsWith(".0")) it.removeSuffix(".0") else it
        }
        name = buildString {
            append("Chapter $chapterNum")
            if (!title.isNullOrBlank()) {
                append(": $title")
            }
        }
        chapter_number = this@Chapter.name.toFloat()
        date_upload = dateFormat.tryParse(updatedAt)
    }

    companion object {
        private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT) }
    }
}

@Serializable
class ChapterResponse(
    val pageProps: ChapterPageProps,
)

@Serializable
class ChapterPageProps(
    val chapterId: String? = null,
    val data: ChapterData,
)

@Serializable
class ChapterData(
    val chapter: ChapterDetails,
)

@Serializable
class ChapterDetails(
    private val id: Long,
    private val uuid: String? = null,
    private val path: String? = null,
    private val mode: String,
    private val images: List<ChapterImage>?,
    private val manga: ChapterManga? = null,
    private val secret: String? = null,
    @SerialName("user_seed") private val userSeed: String? = null,
    @SerialName("scramble_seed") private val scrambleSeed: String? = null,
) {
    fun getPages(): List<ChapterImage> = images.orEmpty()

    fun getChapterId(): Long = id

    fun getChapterUuid(): String = uuid.orEmpty()

    fun isScrambled(): Boolean = mode == "canva"

    fun getSecret(): String? = secret

    fun getUserSeed(): String? = userSeed

    fun getScrambleSeed(): String? = scrambleSeed

    fun getMode(): String = mode

    fun getChapterPath(): String = path.orEmpty()

    fun getMangaSlug(): String {
        manga?.slug?.takeIf { it.isNotBlank() }?.let { return it }
        val chapterPath = path ?: return ""
        val matcher = CHAPTER_PATH_SUFFIX.matcher(chapterPath)
        return if (matcher.find()) chapterPath.substring(0, matcher.start()) else chapterPath
    }

    companion object {
        private val CHAPTER_PATH_SUFFIX = Pattern.compile("-ch-[0-9]+(?:\\.[0-9]+)?$")
    }
}

@Serializable
class ChapterManga(
    val slug: String? = null,
)

@Serializable
class ChapterImage(
    val id: Long,
    val order: Int,
)
