package eu.kanade.tachiyomi.extension.all.comicomoe

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import kotlin.time.Instant

@Serializable
class MangaListDto(
    val items: List<MangaDto>,
    val total: Int,
)

@Serializable
class MangaDto(
    private val id: String,
    private val title: String,
    private val coverUrl: String?,
    private val status: String,
    private val contentRating: String,
    private val format: String,
    private val year: Int?,
    private val tags: List<TagDto>,
    private val comickTags: List<String>,
    private val description: String? = null,
    private val altTitles: List<AltTitleDto> = emptyList(),
    private val authors: List<AuthorDto> = emptyList(),
    private val demographic: String? = null,
    private val links: List<LinkDto> = emptyList(),
    private val originalLanguage: String? = null,
    private val sourceId: String? = null,
    private val sourceMangaId: String? = null,
) {
    fun toSManga(baseUrl: String, metadata: MetadataDto? = null) = SManga.create().apply {
        url = id
        title = this@MangaDto.title
        thumbnail_url = coverUrl?.let { coverUrl(baseUrl, it) }
        status = STATUSES[this@MangaDto.status] ?: SManga.UNKNOWN
        // The site shows the metadata provider's fields when the title's own record lacks them
        val tagNames = tags.map { it.name }.ifEmpty { metadata?.genres?.map { it.name }.orEmpty() }
        val extraTags = comickTags.ifEmpty { metadata?.tags?.map { it.name }.orEmpty() }
        genre = (listOfNotNull(format, demographic ?: metadata?.demographic).map { it.replaceFirstChar(Char::uppercase) } + tagNames + extraTags)
            .distinct()
            .joinToString()
        author = authors.filter { it.role == "author" }.joinToString { it.name }.ifEmpty { null }
        artist = authors.filter { it.role == "artist" }.joinToString { it.name }.ifEmpty { null }
        val mangaDexLink = sourceMangaId?.takeIf { sourceId == "mangadex" }?.let {
            "MangaDex" to HttpUrl.Builder().scheme("https").host("mangadex.org").addPathSegment("title").addPathSegment(it).build().toString()
        }
        val metadataLinks = metadata?.links.orEmpty().map { it.label to it.url } + listOfNotNull(metadata?.url?.let { metadata.providerName to it })
        description = MarkdownDescription.Builder()
            .rating(metadata?.rating?.takeIf { (metadata.ratingCount ?: 0) > 0 }, votes = metadata?.ratingCount)
            .info(
                year?.toString(),
                contentRating.replaceFirstChar(Char::uppercase),
                originalLanguage?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH) },
                metadata?.publishers?.joinToString(),
                metadata?.followCount?.let { "$it follows" },
            )
            .description(metadata?.description?.takeIf(String::isNotBlank) ?: this@MangaDto.description)
            .altTitles(altTitles.ifEmpty { metadata?.altTitles.orEmpty() }.map { it.title })
            // The site merges both link lists, one per host
            .links(
                (links.map { it.label to it.url } + metadataLinks + listOfNotNull(mangaDexLink))
                    .distinctBy { (_, url) -> url.toHttpUrlOrNull()?.host ?: url },
            )
            .build()
    }

    companion object {
        private val STATUSES = mapOf(
            "ongoing" to SManga.ONGOING,
            "completed" to SManga.COMPLETED,
            "hiatus" to SManga.ON_HIATUS,
            "cancelled" to SManga.CANCELLED,
        )

        // Covers on the site's own host come from its resizing proxy (256 px by default)
        private fun coverUrl(baseUrl: String, path: String): String {
            val base = baseUrl.toHttpUrl()
            val url = base.resolve(path)!!
            if (url.host != base.host) return url.toString()
            return url.newBuilder().setQueryParameter("size", "512").build().toString()
        }
    }
}

@Serializable
class MetadataDto(
    val providerName: String,
    val url: String?,
    val description: String?,
    val altTitles: List<AltTitleDto>,
    val genres: List<NameDto>,
    val tags: List<NameDto>,
    val demographic: String?,
    val rating: Double?,
    val ratingCount: Int?,
    val followCount: Int?,
    val publishers: List<String>,
    val links: List<LinkDto>,
)

@Serializable
class NameDto(
    val name: String,
)

@Serializable
class AuthorTitlesDto(
    val titles: List<MangaDto>,
)

@Serializable
class TagDto(
    val id: String,
    val name: String,
    val group: String,
)

@Serializable
class AltTitleDto(
    val title: String,
)

@Serializable
class AuthorDto(
    val name: String,
    val role: String,
)

@Serializable
class LinkDto(
    val label: String,
    val url: String,
)

@Serializable
class ProviderDto(
    val sourceId: String,
    val name: String,
    val readableChapterCount: Int,
)

@Serializable
class ChapterListDto(
    val items: List<ChapterDto>,
    val total: Int,
)

@Serializable
class ChapterDto(
    private val id: String,
    private val title: String?,
    private val volume: String?,
    private val number: String?,
    private val scanlationGroup: String?,
    private val publishedAt: String?,
) {
    fun toSChapter(provider: String) = SChapter.create().apply {
        url = id
        name = buildString {
            volume?.let { append("Vol. ", it, " ") }
            number?.let { append("Ch. ", it) }
            title?.let { if (isEmpty()) append(it) else append(" - ", it) }
            if (isEmpty()) append("Oneshot")
        }.trim()
        number?.toFloatOrNull()?.let { chapter_number = it }
        date_upload = Instant.tryParse(publishedAt)
        scanlator = listOfNotNull(scanlationGroup?.trim(), provider).distinct().joinToString(" · ")
    }
}

@Serializable
class ChapterInfoDto(
    val chapter: ChapterMangaDto,
)

@Serializable
class ChapterMangaDto(
    val mangaId: String,
)

@Serializable
class AccessDto(
    val token: String,
)

@Serializable
class OpenBodyDto(
    private val proxyMethod: String,
    private val readerSourceId: String?,
)

@Serializable
class SessionDto(
    val sessionId: String,
    val readerSourceId: String,
    val pages: List<String>,
    val drmData: List<String?>,
)
