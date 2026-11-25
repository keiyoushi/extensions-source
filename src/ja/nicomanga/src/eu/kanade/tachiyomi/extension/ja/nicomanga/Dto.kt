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
    private val n: String,
    private val c: String?,
    private val ur: String,
) {
    fun toSManga() = SManga.create().apply {
        title = n
        url = ur.toHttpUrlOrNull()?.encodedPath ?: ur
        thumbnail_url = c
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
    private val n: String,
    private val c: String?,
    private val artists: String?,
    @SerialName("other_name") private val otherName: String?,
    private val description: String?,
    @SerialName("status_text") private val statusText: String?,
    @SerialName("authors_list") private val authorsList: List<NLink> = emptyList(),
    @SerialName("genres_list") private val genresList: List<NLink> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        title = n
        thumbnail_url = c
        author = authorsList.takeIf { it.isNotEmpty() }?.joinToString { it.n }
        artist = artists?.ifEmpty { null } ?: authorsList.firstOrNull()?.n
        genre = genresList.takeIf { it.isNotEmpty() }?.joinToString { it.n }
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
    private val n: String?,
    private val u: Long?,
    private val ur: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = ur.toHttpUrlOrNull()?.encodedPath ?: ur
        name = n?.takeIf { it.isNotBlank() } ?: "Chapter ${chapter.orEmpty()}"
        date_upload = u?.times(1000L) ?: 0L
    }
}

@Serializable
class NLink(
    val n: String,
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
