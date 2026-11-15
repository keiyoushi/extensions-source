package eu.kanade.tachiyomi.extension.all.onisaga

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.Serializable

@Serializable
class RpcBody<T>(val json: T)

@Serializable
class BrowseInput(
    private val limit: Int,
    private val q: String? = null,
    private val genres: Terms? = null,
    private val themes: Terms? = null,
    private val tags: Terms? = null,
    private val origin: List<String>? = null,
    private val form: List<String>? = null,
    private val status: List<String>? = null,
    private val demographic: List<String>? = null,
    private val ratings: List<String>? = null,
    private val year: YearRange? = null,
    private val languages: List<String>? = null,
    private val sort: String? = null,
) {
    var cursor: String? = null
}

@Serializable
class Terms(
    private val include: List<String>,
    private val exclude: List<String>,
)

@Serializable
class YearRange(
    private val from: Int? = null,
    private val to: Int? = null,
)

@Serializable
class BrowseDto(
    val items: List<WorkDto>,
    val nextCursor: String?,
)

@Serializable
class WorkDto(
    private val slug: String,
    private val title: String,
    private val coverUrl: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/title/$slug"
        title = this@WorkDto.title
        thumbnail_url = coverUrl
    }
}

@Serializable
class SeriesDto(
    val name: String,
    val alternateName: List<String> = emptyList(),
    val author: List<PersonDto> = emptyList(),
    val creator: List<PersonDto> = emptyList(),
    val genre: List<String> = emptyList(),
)

@Serializable
class PersonDto(val name: String)

@Serializable
class ReadingStartDto(val alternatives: List<AlternativeDto>)

@Serializable
class AlternativeDto(
    val href: String,
    val current: Boolean,
)

@Serializable
class ReaderDto(private val manifest: ManifestDto) {
    val pages get() = manifest.pages
}

@Serializable
class ManifestDto(val pages: List<PageDto>)

@Serializable
class PageDto(val url: String)

@Serializable
class SlugInput(private val slug: String)

@Serializable
class ReleaseInput(
    private val slug: String,
    private val releaseId: String,
)

@Serializable
class GatedTitleDto(
    private val title: String,
    private val synopsis: String?,
    private val publicationStatus: String,
    val language: String,
    val units: List<GatedUnitDto>,
) {
    fun toSManga(slug: String) = SManga.create().apply {
        url = "/title/$slug"
        title = this@GatedTitleDto.title
        description = MarkdownDescription.Builder().markdown(synopsis).build()
        status = when (publicationStatus) {
            "releasing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class GatedUnitDto(
    val label: String,
    val title: String?,
    val releaseId: String?,
)

@Serializable
class GatedReaderDto(val bootstrap: ReaderDto)
