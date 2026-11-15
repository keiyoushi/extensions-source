package eu.kanade.tachiyomi.extension.fr.comicstracker

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class HomeDto(val topEngagement: List<EditionCardDto>)

@Serializable
class EditionListDto(val editions: List<EditionCardDto>)

@Serializable
class SeriesDto(val members: List<EditionCardDto>)

@Serializable
class SearchDto(
    val items: List<EditionCardDto>,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
class EditionCardDto(
    private val id: String,
    @SerialName("french_title") private val frenchTitle: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/comics/$id"
        title = frenchTitle
        thumbnail_url = coverUrl(baseUrl, id)
    }
}

@Serializable
class EditionPageDto(
    private val edition: EditionDto,
    private val breadcrumbs: List<List<BreadcrumbDto>>,
) {
    fun toSManga(baseUrl: String, url: String) = SManga.create().apply {
        this.url = url
        title = edition.frenchTitle
        thumbnail_url = coverUrl(baseUrl, edition.id)
        // The period of the publisher's line, as the site files its editions
        genre = breadcrumbs.firstOrNull()?.firstOrNull()?.label?.substringBefore(" · ")
        description = MarkdownDescription.Builder()
            .field("Sommaire", edition.labels)
            .field("ISBN", edition.isbn)
            .field("Sortie", edition.releaseDate)
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toSChapter(url: String) = SChapter.create().apply {
        this.url = url
        // The app strips the entry's title from chapter names: the edition's would leave nothing
        name = "Édition"
        // A date, or a date and a time
        date_upload = DATE_FORMAT.tryParseDate(edition.createdAt?.take(10), ZoneOffset.UTC)
        memo = buildJsonObject { put("folder", edition.link) }
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
    }
}

@Serializable
class EditionDto(
    val id: String,
    @SerialName("french_title") val frenchTitle: String,
    val link: String,
    val isbn: String?,
    @SerialName("release_date") val releaseDate: String?,
    @SerialName("created_at") val createdAt: String?,
    val labels: List<String>,
)

@Serializable
class BreadcrumbDto(val label: String)

// The originals weigh up to a few megabytes: covers come in the size of the site's edition page
private fun coverUrl(baseUrl: String, id: String) = "$baseUrl/api/image-proxy/french-editions/$id?w=800"
