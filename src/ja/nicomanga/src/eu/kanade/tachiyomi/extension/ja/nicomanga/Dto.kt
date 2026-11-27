package eu.kanade.tachiyomi.extension.ja.nicomanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.toMarkdown
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

@Serializable
class DeferredPayload(
    val payloadKey: String,
)

@Serializable
class ListResponse(
    val items: JsonElement?,
    val pagination: NPagination?,
    @SerialName("genres_list") val genresList: List<NGenre> = emptyList(),
)

@Serializable
class NPagination(
    private val page: Int,
    @SerialName("last_page") private val lastPage: Int,
) {
    val hasNextPage get() = page < lastPage
}

@Serializable
class NListManga(
    @SerialName("n") private val name: String,
    @SerialName("c") private val cover: String?,
    @SerialName("ur") private val mangaUrl: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        url = mangaUrl.toHttpUrlOrNull()?.encodedPath ?: mangaUrl
        thumbnail_url = cover
    }
}

@Serializable
class Payload(
    @SerialName("chaotic_payload") val chaoticPayload: String?,
    val manga: NManga?,
    val images: List<String>?,
    @SerialName("chapters_list") val chaptersList: List<NChapter>?,
)

@Serializable
class NManga(
    @SerialName("n") private val name: String,
    @SerialName("c") private val cover: String?,
    private val artists: String?,
    @SerialName("other_name") private val otherName: String?,
    private val description: String?,
    @SerialName("status_text") private val statusText: String?,
    @SerialName("authors_list") private val authorsList: List<NLink> = emptyList(),
    @SerialName("genres_list") private val genresList: List<NLink> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = name
        thumbnail_url = cover
        author = authorsList.takeIf { it.isNotEmpty() }?.joinToString { it.name }
        artist = artists?.ifEmpty { null } ?: authorsList.firstOrNull()?.name
        genre = genresList.takeIf { it.isNotEmpty() }?.joinToString { it.name }
        status = when (statusText?.lowercase()) {
            "on going", "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        val desc = this@NManga.description
            ?.takeIf { it.isNotBlank() }
            ?.let { Jsoup.parseBodyFragment(it, baseUrl).body().toMarkdown(keepNewlines = true) }
            ?.takeUnless { it.equals("Updating", ignoreCase = true) }
        description = MarkdownDescription.Builder()
            .markdown(desc)
            .altTitles(listOfNotNull(otherName?.takeIf { it.isNotBlank() }))
            .build()
    }
}

@Serializable
class NChapter(
    private val chapter: String?,
    @SerialName("n") private val title: String?,
    @SerialName("u") private val updatedAt: Long?,
    @SerialName("ur") private val chapterUrl: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = chapterUrl.toHttpUrlOrNull()?.encodedPath ?: chapterUrl
        name = title?.takeIf { it.isNotBlank() } ?: "Chapter ${chapter.orEmpty()}"
        date_upload = updatedAt?.times(1000L) ?: 0L
    }
}

@Serializable
class NLink(
    @SerialName("n") val name: String,
)

@Serializable
class GenresPayload(
    val genres: List<NGenre> = emptyList(),
)

@Serializable
class NGenre(
    @SerialName("slug")
    @JsonNames("id")
    val id: String,
    val name: String,
)
