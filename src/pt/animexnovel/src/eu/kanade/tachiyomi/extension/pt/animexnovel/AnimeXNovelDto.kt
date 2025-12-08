package eu.kanade.tachiyomi.extension.pt.animexnovel

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class ChapterDto(
    private val title: Title,
    private val date: String,
    private val slug: String,
) {

    fun toSChapter() = SChapter.create().apply {
        name = title.value.substringAfter(";")
            .takeIf(String::isNotBlank) ?: title.value
        date_upload = DATE_FORMAT.tryParse(date)
        url = "/manga/$slug"
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}

@Serializable
class Title(
    @SerialName("rendered")
    val value: String,
)
