package eu.kanade.tachiyomi.extension.all.dlsite

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
class SearchDto(
    @SerialName("search_result") val searchResult: String,
    @SerialName("page_info") val pageInfo: PageInfoDto,
)

@Serializable
class PageInfoDto(
    val count: Int,
    @SerialName("last_indice") val lastIndice: Int,
)

@Serializable
class ProductDto(
    val workno: String,
    @SerialName("site_id") val siteId: String,
    @SerialName("work_type") val workType: String,
    @SerialName("work_name") private val workName: String,
    @SerialName("work_type_string") private val workTypeString: String? = null,
    @SerialName("maker_name") private val makerName: String? = null,
    @SerialName("series_name") private val seriesName: String? = null,
    @SerialName("label_name") private val labelName: String? = null,
    @SerialName("page_number") private val pageNumber: String? = null,
    @SerialName("regist_date") private val registDate: String? = null,
    @SerialName("intro_s") private val intro: String? = null,
    @SerialName("rate_count_detail") private val rateCountDetail: JsonElement? = null,
    @SerialName("image_main") private val imageMain: FileDto? = null,
    // An object of credits by role, or an empty array when the work has none
    private val creaters: JsonElement? = null,
    // Credits of the books (BJ) works
    private val author: List<AuthorDto>? = null,
    private val genres: List<NameDto> = emptyList(),
    @SerialName("book_type") private val bookType: NameDto? = null,
    private val options: String? = null,
    // An object of options by value, or an empty array
    @SerialName("work_options") private val workOptions: JsonElement? = null,
    // An array of editions, or an object of them by index
    @SerialName("language_editions") private val languageEditions: JsonElement? = null,
    // An array of files, or false when the work has none
    private val trials: JsonElement? = null,
    @SerialName("image_samples") private val imageSamples: List<FileDto>? = null,
    @SerialName("sample_type") private val sampleType: String? = null,
    @SerialName("is_viewable_sample") private val isViewableSample: Boolean = false,
    // The windows in which DLsite Play lets anyone read the work for free
    @SerialName("limited_free_terms") private val freeTerms: List<FreeTermDto> = emptyList(),
) {
    val date get() = DATE_FORMAT.tryParseDateTime(registDate)

    val trialFiles get() = (trials as? JsonArray)?.parseAs<List<TrialFileDto>>().orEmpty()

    fun toSManga(baseUrl: String, workUrl: (String) -> String) = SManga.create().apply {
        val credits = (creaters as? JsonObject)?.parseAs<CreditsDto>() ?: CreditsDto()
        url = workno
        title = workName
        thumbnail_url = imageMain?.url?.let { baseUrl.toHttpUrl().resolve(it) }?.toString()
        author = (credits.createdBy.map { it.name } + credits.scenarioBy.map { it.name } + this@ProductDto.author.orEmpty().map { it.name })
            .distinct()
            .joinToString()
            .ifEmpty { makerName }
        artist = credits.illustBy.joinToString { it.name }.ifEmpty { null }
        genre = buildList {
            workTypeString?.let(::add)
            bookType?.name?.let(::add)
            addAll(genres.map { it.name })
            if (options.orEmpty().split('#').any { it in DLsite.AI_OPTIONS }) add("AI Generated")
        }.joinToString().ifEmpty { null }
        description = description(null, workUrl)
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        memo = buildJsonObject { put("site", siteId) }
    }

    fun description(page: Element?, workUrl: (String) -> String): String? {
        val counts = (rateCountDetail as? JsonObject)?.parseAs<Map<Int, Int>>().orEmpty()
        val votes = counts.values.sum()
        val rating = if (votes > 0) counts.entries.sumOf { it.key * it.value } / votes.toDouble() else null
        val event = (workOptions as? JsonObject)?.parseAs<Map<String, WorkOptionDto>>()?.values
            ?.firstOrNull { it.category == "event" }
            ?.let { it.nameEn?.ifEmpty { null } ?: it.name }
        val editions = when (languageEditions) {
            is JsonArray -> languageEditions.parseAs<List<EditionDto>>()
            is JsonObject -> languageEditions.parseAs<Map<String, EditionDto>>().values
            else -> emptyList()
        }.filter { it.workno != workno }.map { it.label to workUrl(it.workno) }
        return MarkdownDescription.Builder()
            .rating(rating, max = 5, votes = votes)
            .apply { if (page != null) description(page) else description(intro) }
            // Doujin works (RJ) have a circle, books (BJ) a publisher
            .field(if (workno.startsWith("BJ")) "Publisher" else "Circle", makerName)
            .field("Label", labelName)
            .field("Series", seriesName)
            .field("Event", event)
            .field("Pages", pageNumber)
            .links(editions, label = "Editions")
            .build()
    }

    fun toFreeChapter(): SChapter? {
        val now = Clock.System.now().toEpochMilliseconds()
        val term = freeTerms.firstOrNull { now in DATE_FORMAT.tryParseDateTime(it.start)..DATE_FORMAT.tryParseDateTime(it.end) } ?: return null
        return SChapter.create().apply {
            url = "/free/$workno"
            name = "Free"
            date_upload = DATE_FORMAT.tryParseDateTime(term.start)
            memo = buildJsonObject {
                put("type", DLsite.FREE)
                put("workno", workno)
            }
        }
    }

    // Books show their sample in the site's book viewer, other works the sample images
    fun toSampleChapter(baseUrl: String): SChapter? {
        val readable = sampleType == "epub" || (sampleType == "images" && !imageSamples.isNullOrEmpty())
        if (!isViewableSample || !readable) return null
        return SChapter.create().apply {
            url = "/sample/$workno"
            name = "Sample"
            date_upload = date
            memo = buildJsonObject {
                put("type", if (sampleType == "epub") DLsite.BOOK_SAMPLE else DLsite.IMAGE_SAMPLE)
                put("workno", workno)
                if (sampleType == "images") putJsonArray("images") { imageSamples!!.forEach { add(baseUrl.toHttpUrl().resolve(it.url!!).toString()) } }
            }
        }
    }
}

@Serializable
class FileDto(val url: String? = null)

@Serializable
class TrialFileDto(
    @SerialName("relative_url") val relativeUrl: String,
    private val url: String,
) {
    val zipUrl get() = "https:$url"
}

@Serializable
class CreditsDto(
    @SerialName("created_by") val createdBy: List<NameDto> = emptyList(),
    @SerialName("scenario_by") val scenarioBy: List<NameDto> = emptyList(),
    @SerialName("illust_by") val illustBy: List<NameDto> = emptyList(),
)

@Serializable
class NameDto(val name: String)

@Serializable
class WorkOptionDto(
    val category: String? = null,
    val name: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
)

@Serializable
class AuthorDto(@SerialName("author_name") val name: String)

@Serializable
class EditionDto(val workno: String, val label: String)

@Serializable
class GenreOption(val name: String, val id: String)

@Serializable
class ViewerTokenDto(
    private val prefix: String,
    private val expireAt: String,
    private val parameters: Map<String, String>,
    // The page key, wrapped with the public key sent for it
    val key: String? = null,
) {
    val expiresAt get() = Instant.parse(expireAt)

    fun url(path: String): HttpUrl = prefix.toHttpUrl().newBuilder()
        .addPathSegments(path)
        .apply { parameters.forEach { addEncodedQueryParameter(it.key, it.value) } }
        .build()
}

@Serializable
class ViewerMetaDto(val pages: List<ViewerPageDto>)

@Serializable
class ViewerPageDto(val type: String, val src: String)

@Serializable
class FreeTermDto(
    @SerialName("start_date") val start: String,
    @SerialName("end_date") val end: String,
)

@Serializable
class FreeZipTreeDto(val revision: String, val playfile: Map<String, FreePlayFileDto>)

@Serializable
class FreePlayFileDto(val type: String)

@Serializable
class FreeViewerTokenRequest(
    @SerialName("play_type") val playType: String,
    val revision: String,
    @SerialName("public_key") val publicKey: String,
)

@Serializable
class SaleDto(val workno: String)

@Serializable
class WorknosDto(val worknos: List<String>)

@Serializable
class LibraryDto(val works: List<LibraryWorkDto>)

@Serializable
class LibraryWorkDto(
    val workno: String,
    private val name: Map<String, String>,
    private val images: LibraryImagesDto? = null,
    @SerialName("work_type") val workType: String,
    val downloadable: Boolean = false,
    @SerialName("play_encode_type") val playEncodeType: String? = null,
) {
    fun title(locale: String) = name[locale] ?: name["ja_JP"] ?: name.values.first()

    fun toSManga(locale: String) = SManga.create().apply {
        url = workno
        title = this@LibraryWorkDto.title(locale)
        thumbnail_url = images?.main
    }
}

@Serializable
class LibraryImagesDto(val main: String? = null)

@Serializable
class PlaySignDto(val url: String, private val expires: String) {
    val expiresAt get() = Instant.parse(expires)
}

@Serializable
class ZipTreeDto(private val tree: List<ZipTreeNodeDto>, private val playfile: Map<String, PlayFileDto>) {
    // The image folders, in the work's file order; each PDF is a folder of its own pages
    fun folders(): Map<String, List<OptimizedDto>> {
        val folders = linkedMapOf<String, MutableList<OptimizedDto>>()
        fun walk(nodes: List<ZipTreeNodeDto>, path: String) {
            nodes.forEach { node ->
                val nodePath = if (path.isEmpty()) node.name else "$path/${node.name}"
                val file = node.hashname?.let { playfile[it] }
                when {
                    node.type == "folder" -> walk(node.children, nodePath)
                    file?.image != null -> folders.getOrPut(path) { mutableListOf() } += file.image.optimized
                    file?.pdf != null -> folders.getOrPut(nodePath) { mutableListOf() } += file.pdf.page.map { it.optimized }
                }
            }
        }
        walk(tree, "")
        return folders
    }
}

@Serializable
class ZipTreeNodeDto(
    val type: String,
    val name: String,
    val hashname: String? = null,
    val children: List<ZipTreeNodeDto> = emptyList(),
)

@Serializable
class PlayFileDto(val image: PlayImageDto? = null, val pdf: PlayPdfDto? = null)

@Serializable
class PlayImageDto(val optimized: OptimizedDto)

@Serializable
class PlayPdfDto(val page: List<PlayImageDto>)

@Serializable
class OptimizedDto(val name: String, val width: Int, val height: Int, val crypt: Boolean)

@Serializable
class ArchiveEntry(
    val name: String,
    val offset: Long,
    val compressedSize: Long,
    val method: Int,
    val headerAllowance: Int,
    // The download page, which purchase archives need as Referer
    val referer: String? = null,
)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.of("Asia/Tokyo"))
