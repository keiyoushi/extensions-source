package eu.kanade.tachiyomi.extension.en.mayotune

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
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun getChapterURL(): String = "/chapter/${this.id}"

    fun getChapterTitle(): String = if (!this.title.isEmpty()) {
        "Chapter ${this.number.asString()}: ${this.title}"
    } else {
        "Chapter ${this.number.asString()}"
    }

    fun getDateTimestamp(): Long = this.sdf.tryParse(this.date)

    companion object {
        fun Float.asString(): String = if (this % 1 == 0f) {
            this.toInt().toString()
        } else {
            this.toString()
        }
    }
}
