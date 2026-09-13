package eu.kanade.tachiyomi.extension.en.thegirlfromrandomchattingmangaonline

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

typealias ChaptersListDto = @Serializable List<ChapterDto>

@Serializable
class ChapterDto(
    private val title: String,
    private val date: String,
    private val url: String,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        // url looks like https://thegirlfromrandomchatting.com/manga/the-girl-from-random-chatting-chapter-96/
        url = this@ChapterDto.url.toHttpUrl().pathSegments.last(String::isNotEmpty)
        // title looks like : The Girl from Random Chatting, Chapter 351
        name = title.split(", ")[1]
        chapter_number = name.split(" ")[1].toFloat()
        date_upload = Instant.tryParse(date)
    }
}
