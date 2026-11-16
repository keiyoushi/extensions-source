package eu.kanade.tachiyomi.extension.all.jellyfin

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class LoginDto(
    private val username: String,
    private val pw: String,
)

@Serializable
class AuthResultDto(val accessToken: String)

@Serializable
class ItemsDto(
    val items: List<ItemDto>,
    val totalRecordCount: Int,
)

@Serializable
class ItemDto(
    val id: String,
    val name: String,
    val type: String,
    val parentId: String? = null,
    val collectionType: String? = null,
    private val path: String? = null,
    private val imageTags: Map<String, String> = emptyMap(),
    private val overview: String? = null,
    private val taglines: List<String> = emptyList(),
    private val originalTitle: String? = null,
    private val genres: List<String> = emptyList(),
    private val tags: List<String> = emptyList(),
    private val studios: List<NameDto> = emptyList(),
    private val people: List<PersonDto> = emptyList(),
    private val communityRating: Float? = null,
    private val officialRating: String? = null,
    private val externalUrls: List<ExternalUrlDto> = emptyList(),
    private val productionYear: Int? = null,
    private val indexNumber: Int? = null,
    private val premiereDate: String? = null,
    private val dateCreated: String? = null,
) {
    val isBook get() = type == "Book"

    val extension get() = path?.substringAfterLast('.', "")?.lowercase()

    // Books in the formats the site's comics reader opens; text books (epub, mobi, azw) are left out
    val isComic get() = extension in COMIC_EXTENSIONS

    fun cover(baseUrl: String): String? {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments("Items/$id/Images/Primary")
        return when {
            "Primary" in imageTags -> url.addQueryParameter("tag", imageTags["Primary"]).build().toString()
            type == "Folder" -> url.fragment(FOLDER_COVER).build().toString()
            else -> null
        }
    }

    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = id
        title = name
        thumbnail_url = cover(baseUrl)
    }

    // A folder has little metadata of its own, so the rest comes from its books
    fun toSManga(baseUrl: String, books: List<ItemDto>) = SManga.create().apply {
        val all = listOf(this@ItemDto) + books
        val people = all.flatMap { it.people }
        val years = all.mapNotNull { it.productionYear }
        url = id
        title = name
        author = people.namesOf("Author")
        artist = people.namesOf("Penciller", "Inker")
        genre = all.flatMap { it.genres + it.tags }.distinct().joinToString().ifEmpty { null }
        thumbnail_url = all.firstNotNullOfOrNull { it.cover(baseUrl) }
        description = MarkdownDescription.Builder()
            .rating(communityRating)
            .info(
                years.minOrNull()?.let { first -> years.max().let { last -> if (first == last) "$first" else "$first–$last" } },
                all.mapNotNull { it.officialRating }.distinct().joinToString().ifEmpty { null },
                all.flatMap { it.studios }.map { it.name }.distinct().joinToString().ifEmpty { null },
            )
            .separator()
            .description(taglines.firstOrNull())
            .description(all.firstNotNullOfOrNull { it.overview }?.let { Jsoup.parseBodyFragment(it).body() })
            .separator()
            .altTitles(all.mapNotNull { it.originalTitle }.distinct())
            .links(externalUrls.map { it.name to it.url })
            .build()
    }

    fun toSChapter(prefix: String?) = SChapter.create().apply {
        url = id
        name = listOfNotNull(prefix, this@ItemDto.name).joinToString(" / ")
        indexNumber?.let { chapter_number = it.toFloat() }
        scanlator = people.namesOf("Translator")
        date_upload = Instant.tryParse(premiereDate ?: dateCreated)
    }
}

@Serializable
class NameDto(val name: String)

@Serializable
class PersonDto(
    val name: String,
    val type: String,
)

@Serializable
class ExternalUrlDto(
    val name: String,
    val url: String,
)

private fun List<PersonDto>.namesOf(vararg types: String) = filter { it.type in types }.map { it.name }.distinct().joinToString().ifEmpty { null }

@Serializable
class FiltersDto(
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val officialRatings: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
)

@Serializable
class FilterData(
    val libraries: List<NamedId>,
    val collections: List<NamedId>,
    val genres: List<String>,
    val tags: List<String>,
    val officialRatings: List<String>,
    val years: List<Int>,
    val studios: List<NamedId>,
    val people: List<NamedId>,
)

@Serializable
class NamedId(
    val id: String,
    val name: String,
)

@Serializable
class ZipPageDto(
    val name: String,
    val offset: Long,
    val compressedSize: Long,
    val method: Int,
    val localHeaderAllowance: Int,
)

const val FOLDER_COVER = "first-book"

private val COMIC_EXTENSIONS = setOf("cbz", "zip", "cbr", "rar", "cb7", "7z", "cbt", "tar", "pdf")
