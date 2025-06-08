package eu.kanade.tachiyomi.extension.en.mayotune

import kotlinx.serialization.Serializable

@Serializable
data class ChapterDto(
    val id: String,
    val title: String,
    val number: Float,
    val pageCount: Int,
    val date: String,
) {
    fun getChapterURL(): String = "/chapter/${this.id}"

    fun getNumberStr(): String = if (this.number % 1 == 0f) {
        this.number.toInt().toString()
    } else {
        this.number.toString()
    }

    fun getChapterTitle(): String = if (!this.title.isEmpty()) {
        "Chapter ${this.getNumberStr()}: ${this.title}"
    } else {
        "Chapter ${this.getNumberStr()}"
    }
}
