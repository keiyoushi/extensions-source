package eu.kanade.tachiyomi.extension.ja.klto9

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

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
            date_upload = dateFormat.tryParse(lastUpdate).let {
                if (it <= 0L) it else min(it, System.currentTimeMillis())
            }
        }
    }
}
