package eu.kanade.tachiyomi.extension.ja.klto9

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.time.Clock

private val dateFormat = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    .withZone(ZoneId.of("UTC"))

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

@Serializable
class Dto(
    private val id: String,
    private val chapter: String,
    private val name: String? = null,
    private val manga: String,
    @SerialName("last_update") private val lastUpdate: String,
) {
    fun toSChapter(): SChapter {
        val chapterName = name
        return SChapter.create().apply {
            url = "$id#$manga#$chapter"
            this.name = buildString {
                append("Chapter ")
                append(chapter.removeSuffix(".0"))
                if (!chapterName.isNullOrEmpty()) {
                    append(" - ")
                    append(chapterName)
                }
            }
            date_upload = min(dateFormat.tryParseDateTime(lastUpdate), Clock.System.now().toEpochMilliseconds())
        }
    }
}
