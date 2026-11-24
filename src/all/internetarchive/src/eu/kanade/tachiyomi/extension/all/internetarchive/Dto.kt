package eu.kanade.tachiyomi.extension.all.internetarchive

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

@Serializable
class SearchDto(
    val response: SearchResponseDto,
)

@Serializable
class SearchResponseDto(
    val numFound: Int,
    val docs: List<SearchDocDto>,
)

@Serializable
class SearchDocDto(
    private val identifier: String,
    private val title: String? = null,
) {
    fun toSManga(baseUrl: String) = title?.let {
        SManga.create().apply {
            url = identifier
            title = it
            thumbnail_url = thumbnailUrl(baseUrl, identifier)
        }
    }
}

@Serializable
class ItemDto(
    val server: String? = null,
    val dir: String? = null,
    val files: List<FileDto> = emptyList(),
    val metadata: MetadataDto? = null,
    private val reviews: List<ReviewDto> = emptyList(),
) {
    fun toSManga(manga: SManga, baseUrl: String) = manga.apply {
        val metadata = metadata!!
        url = metadata.identifier
        title = metadata.title
        author = metadata.creator.joinToString().ifEmpty { null }
        genre = metadata.subject
            .flatMap { it.split(';') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString()
            .ifEmpty { null }
        thumbnail_url = thumbnailUrl(baseUrl, metadata.identifier)
        // Reviews posted without a rating carry 0 stars
        val stars = reviews.mapNotNull { review -> review.stars.toIntOrNull()?.takeIf { it > 0 } }
        description = MarkdownDescription.Builder()
            .rating(stars.average(), max = 5, votes = stars.size)
            .info(metadata.date, metadata.language.joinToString())
            .description(Jsoup.parseBodyFragment(metadata.description.joinToString("<br>"), baseUrl).body())
            .field("Publisher", metadata.publisher)
            .field("Contributor", metadata.contributor)
            .field("Notes", metadata.notes)
            .links(listOf("License" to metadata.licenseurl))
            .build()
    }

    fun toChapters(baseUrl: String): List<SChapter> {
        val metadata = metadata!!
        if (metadata.accessRestricted == "true") throw Exception("This item is lending-only on the Internet Archive")

        val byName = files.associateBy { it.name }
        val books = files.filter { it.format.startsWith("Single Page Processed") }
        val derived = books.mapNotNullTo(HashSet()) { it.original }

        val bookChapters = books.map { file ->
            val subPrefix = file.name.substringBeforeLast('_')
            val original = file.original?.let(byName::get)
            ChapterEntry(
                path = subPrefix,
                name = original?.title ?: subPrefix.substringAfterLast('/'),
                url = itemUrl(baseUrl, metadata.identifier, "details", subPrefix),
                date = (original ?: file).date,
            )
        }
        val archiveChapters = files
            .filter { it.source == "original" && it.format in ARCHIVE_FORMATS && it.name !in derived }
            .map { file ->
                ChapterEntry(
                    path = file.name,
                    name = file.title ?: file.name.substringAfterLast('/').substringBeforeLast('.'),
                    url = itemUrl(baseUrl, metadata.identifier, "download", file.name),
                    date = file.date,
                )
            }
        val imageChapters = files
            .filter { it.isImage }
            .groupBy { it.folder }
            .map { (dir, images) ->
                ChapterEntry(
                    path = dir,
                    name = dir.substringAfterLast('/').ifEmpty { "Images" },
                    url = itemUrl(baseUrl, metadata.identifier, "download", dir),
                    date = images.maxOf { it.date },
                )
            }

        return (bookChapters + archiveChapters + imageChapters)
            .sortedByDescending { naturalKey(it.path) }
            .map { entry ->
                SChapter.create().apply {
                    url = entry.url
                    name = entry.name
                    // Folder holding an edition or one uploader's set within the item
                    scanlator = entry.path.substringBeforeLast('/', "").substringAfterLast('/').ifEmpty { null }
                    date_upload = entry.date
                }
            }
    }
}

private class ChapterEntry(
    val path: String,
    val name: String,
    val url: String,
    val date: Long,
)

@Serializable
class FileDto(
    val name: String,
    val source: String? = null,
    val format: String = "",
    val original: String? = null,
    val title: String? = null,
    private val mtime: String? = null,
) {
    val date get() = mtime?.toLongOrNull()?.times(1000) ?: 0L
    val isImage get() = source == "original" && format in IMAGE_FORMATS
    val folder get() = name.substringBeforeLast('/', "")
}

@Serializable
class MetadataDto(
    val identifier: String,
    val title: String,
    val mediatype: String? = null,
    @Serializable(with = StringListSerializer::class) val creator: List<String> = emptyList(),
    @Serializable(with = StringListSerializer::class) val contributor: List<String> = emptyList(),
    @Serializable(with = StringListSerializer::class) val publisher: List<String> = emptyList(),
    @Serializable(with = StringListSerializer::class) val subject: List<String> = emptyList(),
    @Serializable(with = StringListSerializer::class) val language: List<String> = emptyList(),
    @Serializable(with = StringListSerializer::class) val description: List<String> = emptyList(),
    @Serializable(with = StringListSerializer::class) val notes: List<String> = emptyList(),
    val date: String? = null,
    val licenseurl: String? = null,
    @SerialName("access-restricted-item") val accessRestricted: String? = null,
)

@Serializable
class ReviewDto(
    val stars: String,
)

@Serializable
class BookReaderDto(
    val data: BookReaderDataDto,
)

@Serializable
class BookReaderDataDto(
    val brOptions: BookReaderOptionsDto,
)

@Serializable
class BookReaderOptionsDto(
    val data: List<List<BookReaderPageDto>>,
)

@Serializable
class BookReaderPageDto(
    val uri: String,
)

/** Archive.org metadata fields hold a single value or a list of them. */
object StringListSerializer : JsonTransformingSerializer<List<String>>(ListSerializer(String.serializer())) {
    override fun transformDeserialize(element: JsonElement) = element as? JsonArray ?: JsonArray(listOf(element))
}

private val NUMBER_REGEX = Regex("""\d+""")

/** Sort key comparing the numbers in a file name as numbers (Volume 2 before Volume 10). */
fun naturalKey(name: String) = NUMBER_REGEX.replace(name.lowercase()) { it.value.padStart(20, '0') }

private val ARCHIVE_FORMATS = setOf("Comic Book ZIP", "Comic Book RAR", "ZIP")
private val IMAGE_FORMATS = setOf("JPEG", "PNG", "Animated GIF")

private fun thumbnailUrl(baseUrl: String, identifier: String) = "$baseUrl/services/img/$identifier"

private fun itemUrl(baseUrl: String, identifier: String, kind: String, path: String) = baseUrl.toHttpUrl().newBuilder()
    .addPathSegment(kind)
    .addPathSegment(identifier)
    .apply { if (path.isNotEmpty()) addPathSegments(path) }
    .build()
    .encodedPath
