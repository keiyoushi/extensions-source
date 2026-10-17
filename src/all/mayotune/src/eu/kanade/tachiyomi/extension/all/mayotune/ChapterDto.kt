package eu.kanade.tachiyomi.extension.all.mayotune

import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ChapterDto(
    val id: String,
    private val title: String,
    val number: Float,
    val pageCount: Int,
    private val date: String,
) {
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

    fun getDateTimestamp(): Long = Instant.tryParse(this.date)
}
