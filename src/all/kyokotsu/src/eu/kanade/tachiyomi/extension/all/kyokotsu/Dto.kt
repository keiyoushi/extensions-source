package eu.kanade.tachiyomi.extension.all.kyokotsu

import android.util.Base64
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.lib.i18n.Intl
import keiyoushi.utils.string
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

private val PROXY_DOMAINS = arrayOf(
    "remanga.org",
    "mixlib.me",
    "imglib.info",
    "inuko.me",
    "mangadex.org",
    "mangadex.network",
    "weebcentral.com",
    "planeptune.us",
    "compsci88.com",
    "lowee.us",
    "lastation.us",
)

private val statusMap = mapOf(
    "Манга" to "manga",
    "Манхва" to "manhwa",
    "Маньхуа" to "manhua",
    "Комикс" to "comics",
    "OEL-манга" to "oel-manga",
    "Руманга" to "rumanga",
    "Рунет-комикс" to "runet-comics",
)

private fun pickTitle(lang: String, eng: String?, ru: String?): String = when (lang) {
    "en" -> eng?.takeIf(String::isNotBlank) ?: ru?.takeIf(String::isNotBlank)
    else -> ru?.takeIf(String::isNotBlank) ?: eng?.takeIf(String::isNotBlank)
} ?: throw IllegalStateException("Title cannot be empty")

private fun pickCover(lang: String, eng: String?, ru: String?): String? = when (lang) {
    "en" -> eng?.takeIf(String::isNotBlank) ?: ru?.takeIf(String::isNotBlank)
    else -> ru?.takeIf(String::isNotBlank)
}

private fun pickLang(lang: String, eng: String?, ru: String?): String? = when (lang) {
    "en" -> eng?.takeIf(String::isNotBlank)
    else -> ru?.takeIf(String::isNotBlank)
}

private fun String?.toProxy(baseUrl: String): String? {
    val url = this?.takeIf(String::isNotBlank) ?: return null
    if (url.startsWith("/") || url.startsWith("data:")) return url

    val host = url.toHttpUrlOrNull()?.host ?: return url
    val shouldProxy = PROXY_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }
    if (!shouldProxy) return url

    val encoded = Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    return "$baseUrl/proxy?e=$encoded"
}

// ============================== API ===============================
@Serializable
class ApiResponse<T>(
    val data: T,
)

@Serializable
class ApiError(
    val error: String? = null,
)

// ============================== Search ===============================
@Serializable
class SearchDto(
    val total: Int,
    val items: List<SearchMangaDto>,
)

@Serializable
class SearchMangaDto(
    private val slug: String,
    @SerialName("title_en") private val titleEn: String? = null,
    @SerialName("title_ru") private val titleRu: String? = null,
    private val type: String,
    private val cover: String,
    @SerialName("cover_en") private val coverEn: String? = null,
    private val source: String,
) {
    fun toSManga(lang: String, baseUrl: String): SManga = SManga.create().apply {
        url = slug
        thumbnail_url = pickCover(lang, coverEn, cover).toProxy(baseUrl)
        title = pickTitle(lang, titleEn, titleRu)
        memo = buildJsonObject {
            put("type", statusMap[type])
            put("source", source)
        }
    }
}

// ============================== Manga ===============================
@Serializable
class FullMangaDto(
    private val slug: String,
    @SerialName("title_en") private val titleEn: String? = null,
    @SerialName("title_ru") private val titleRu: String? = null,
    private val type: String,
    private val source: String,
    private val cover: String,
    @SerialName("cover_en") private val coverEn: String? = null,
    private val description: String? = null,
    @SerialName("description_en") private val descriptionEn: String? = null,
    private val status: String? = null,
    private val genres: List<String>? = null,
    private val tags: List<String>? = null,
    private val author: String? = null,
    @SerialName("author_en") private val authorEn: String? = null,
    @SerialName("alt_titles") private val altTitles: List<String>? = null,
    private val translators: List<Names>? = null,
    @SerialName("translators_en") private val translatorsEn: List<Names>? = null,
    private val rating: Float,
    private val votes: Int,
    private val views: Int,
) {
    fun toSManga(lang: String, baseUrl: String, engGenres: Map<String, String> = emptyMap(), loc: Intl) = SManga.create().apply {
        url = slug
        thumbnail_url = pickCover(lang, coverEn, cover).toProxy(baseUrl)
        title = pickTitle(lang, titleEn, titleRu)
        author = pickLang(lang, authorEn, this@FullMangaDto.author)
        description = buildString {
            append(pickLang(lang, descriptionEn, this@FullMangaDto.description)?.replace("*", ""))
            if (isNotEmpty()) append("\n")
            append("**${loc["manga_rating"]}**: %.1f\n".format(rating))
            append("**${loc["manga_votes"]}**: $votes\n")
            append("**${loc["manga_views"]}**: $views\n")
            append("**${loc["manga_alt_titles"]}**:")
            append(altTitles?.joinToString { "\n- $it" })
        }

        genre = buildList {
            if (lang != "ru") {
                genres?.mapNotNull { engGenres[it]?.takeIf(String::isNotBlank) }?.let { addAll(it) }
                tags?.mapNotNull { engGenres[it]?.takeIf(String::isNotBlank) }?.let { addAll(it) }
            } else {
                genres?.let { addAll(it) }
                tags?.let { addAll(it) }
            }
        }.distinct().joinToString()

        status = when (this@FullMangaDto.status) {
            "Онгоинг" -> SManga.ONGOING
            "Выходит" -> SManga.ONGOING
            "Продолжается" -> SManga.ONGOING
            "Завершён" -> SManga.COMPLETED
            "Закончен" -> SManga.COMPLETED
            "Приостановлен" -> SManga.ON_HIATUS
            "Лицензировано" -> SManga.LICENSED
            // 1.7
            // "Анонс" -> SManga.UPCOMING
            // "Заброшен" -> SManga.DROPPED
            else -> SManga.UNKNOWN
        }

        memo = buildJsonObject {
            put("type", statusMap[type])
            put("source", source)
            put("translators", pickLang(lang, translatorsEn?.joinToString { it.name }, translators?.joinToString { it.name }))
        }
    }

    @Serializable
    class Names(
        val name: String,
    )
}

// ============================== Chapters ===============================
@Serializable
class ChapterDto(
    private val id: Int,
    @SerialName("title_slug") private val titleSlug: String,
    @SerialName("chapter_id") private val chapterId: String? = null,
    private val number: String,
    private val name: String? = null,
    private val uploader: String? = null,
    @SerialName("pub_date") private val date: String? = null,
    @SerialName("is_mangalib") private val mangalib: Boolean? = false,
    @SerialName("is_mangadex") private val mangadex: Boolean? = false,
    @SerialName("is_weebcentral") private val weebcentral: Boolean? = false,
    @SerialName("is_inkstory") private val inkstory: Boolean? = false,
    private val branches: List<Branches>? = null,
) {
    fun toSChapter(lang: String, mangaMemo: JsonObject) = SChapter.create().apply {
        url = id.toString()

        val parts = check.find(chapterId.toString())
        val vol = parts?.groupValues[1]
        val num = parts?.groupValues[2]

        name = buildString {
            num?.takeIf(String::isNotBlank)?.let { ch ->
                vol?.takeIf(String::isNotBlank)?.let { v ->
                    append(pickLang(lang, "Volume $v ", "Том $v "))
                }
                append(pickLang(lang, "Chapter $ch", "Глава $ch")?.removeSuffix(".0"))
            }

            if (isEmpty()) append(pickLang(lang, "Chapter $number", "Глава $number"))

            this@ChapterDto.name?.takeIf(String::isNotBlank)?.let {
                if (!it.startsWith("Episode")) append(" $it")
            }
        }

        chapter_number = num?.takeIf(String::isNotBlank)?.toFloatOrNull() ?: number.toFloatOrNull() ?: -1f

        date_upload = date?.takeIf(String::isNotBlank)?.let {
            if (it.endsWith("Z")) {
                Instant.tryParse(it)
            } else {
                dateFormat.tryParseDate(it)
            }
        } ?: 0L

        scanlator = uploader?.takeIf(String::isNotBlank) ?: mangaMemo["translators"]?.stringOrNull

        memo = buildJsonObject {
            put("source", mangaMemo["source"]!!.string)
            put("slug", titleSlug)
            put("id", chapterId)
            put("mangalib", mangalib)
            put("mangadex", mangadex)
            put("weebcentral", weebcentral)
            put("inkstory", inkstory)
            put("vol", vol)
            put("num", num)
            put("number", number)
            put("branch", branches?.firstOrNull()?.branchId)
        }
    }

    @Serializable
    class Branches(
        @SerialName("branch_id") val branchId: Int,
    )
    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val check = """(\d+):([\d.]+)""".toRegex()
    }
}

@Serializable
class RemangaDto(
    val content: RemangaContentDto,
)

@Serializable
class RemangaContentDto(
    val pages: List<List<RemangaPageDto>>? = emptyList(),
)

@Serializable
class RemangaPageDto(
    val link: String? = null,
)

@Serializable
class ImageRequest(
    val urls: List<String>? = emptyList(),
)

// ============================== Filters ===============================
@Serializable
class Genres(
    @SerialName("name_ru") val nameRu: String,
    @SerialName("name_en") val nameEn: String,
)

@Serializable
class FiltersDto(
    val genres: List<Pair<String, String>>? = emptyList(),
)
