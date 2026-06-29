package eu.kanade.tachiyomi.extension.all.mayotune

import keiyoushi.utils.tryParse
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class ChapterDto(
    val id: String,
    val title: String,
    val number: Float,
    val pageCount: Int,
    val date: String,
) {
    @Contextual
    private val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    fun getChapterURL(chapterEndpoint: String): String = "/api/$chapterEndpoint/chapters?id=$id&number=${this.getNumberStr()}"

    fun getNumberStr(): String = if (this.number % 1 == 0f) {
        this.number.toInt().toString()
    } else {
        this.number.toString()
    }

    fun getChapterTitle(): String = if (this.title.isNotBlank()) {
        "Chapter ${this.getNumberStr()}: ${this.title}"
    } else {
        "Chapter ${this.getNumberStr()}"
    }

    fun getDateTimestamp(): Long = this.sdf.tryParse(this.date)
}
