package eu.kanade.tachiyomi.extension.en.leslievictims

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class LibraryEntry(
    private val id: String,
    val title: String,
    private val cover: String,
    private val chapters: List<String>,
    @SerialName("chapter_roots") private val chapterRoots: Map<String, ChapterRoot> = emptyMap(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        val urlBuilder = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("series", id)

        url = urlBuilder.build().run { "$encodedPath?$encodedQuery" }
        this.title = this@LibraryEntry.title
        thumbnail_url = "$baseUrl/$cover"
        initialized = true
    }

    fun getChapters(baseUrl: String) = chapters.reversed().map { chId ->
        SChapter.create().apply {
            val urlBuilder = "$baseUrl/".toHttpUrl().newBuilder()
                .addQueryParameter("series", id)
                .addQueryParameter("ch", chId)

            url = urlBuilder.build().run { "$encodedPath?$encodedQuery" }
            name = "Chapter $chId"
            chapter_number = chId.substringBefore(" ").toFloatOrNull() ?: -1f
        }
    }

    fun getId() = id
    fun getChapterRoot(chId: String) = chapterRoots[chId]
}

@Serializable
class ChapterRoot(
    val url: String,
    val mode: String,
    val data: JsonElement,
)
