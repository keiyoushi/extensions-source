package eu.kanade.tachiyomi.extension.de.onepiecetube

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
class ListDto(
    val category: CategoryDto,
    val specials: List<SpecialDto>,
    val entries: List<EntryDto>,
)

@Serializable
class CategoryDto(
    val description: String?,
    val published: String?,
)

@Serializable
class SpecialDto(
    val id: Int,
    val name: String,
)

@Serializable
class EntryDto(
    @SerialName("name") private val title: String,
    val number: Int,
    @SerialName("specials_id") val specialsId: Int,
    private val lang: String,
    @SerialName("is_available") val isAvailable: Boolean,
    private val date: String,
    val href: String,
) {
    // Spin-off links carry their series: /manga/spin-off/<series>/<number>/<page>
    val series get() = href.toHttpUrl().pathSegments.getOrNull(2)

    fun toSChapter() = SChapter.create().apply {
        url = href.toHttpUrl().encodedPath
        name = buildString {
            append(number)
            if (title != "-") append(" - ", title)
            if (lang != "ger") append(" (", LANGUAGES[lang] ?: lang, ")")
        }
        chapter_number = number.toFloat()
        date_upload = DATE_FORMAT.tryParseDate(date, ZONE)
    }
}

@Serializable
class ReaderDto(val chapter: ReaderChapterDto)

@Serializable
class ReaderChapterDto(val pages: List<PageDto>)

@Serializable
class PageDto(val url: String)

private val LANGUAGES = mapOf("jp" to "Japanisch", "eng" to "Englisch")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val ZONE = ZoneId.of("Europe/Berlin")
