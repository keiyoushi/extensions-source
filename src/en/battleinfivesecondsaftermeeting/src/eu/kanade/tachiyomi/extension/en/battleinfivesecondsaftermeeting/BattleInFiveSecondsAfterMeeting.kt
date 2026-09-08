package eu.kanade.tachiyomi.extension.en.battleinfivesecondsaftermeeting

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class BattleInFiveSecondsAfterMeeting : Madara() {
    override val supportsLatest = false

    override val mangaDetailsSelectorTitle = "h1"
    override val mangaDetailsSelectorAuthor = "h5:contains(Author) + h4 a"
    override val mangaDetailsSelectorArtist = "h5:contains(Artist) + h4 a"
    override val mangaDetailsSelectorDescription = ".synopsis p"
    override val mangaDetailsSelectorThumbnail = ".cover_managa img"
    override val mangaDetailsSelectorStatus = "h5:contains(Status) + h4"
    override val mangaDetailsSelectorTag = "h5:contains(Tag) + h4 a"
    override val seriesTypeSelector = "h5:contains(Type) + h4"
    override val altNameSelector = "h5:contains(Alternative) + h4"

    override fun parseChapterList(document: Document, mangaPath: String): List<SChapter> {
        val dates = document.select(".chapter-item").associate { element ->
            element.selectFirst("a")!!.attr("abs:href") to parseChapterDate(element.selectFirst(".post-on")?.text())
        }
        return document.select(".main-chapter").mapNotNull { element ->
            val href = element.selectFirst("a")?.attr("abs:href") ?: return@mapNotNull null
            val slug = href.toHttpUrl().encodedPath.trimEnd('/').substringAfterLast('/').takeIf(String::isNotEmpty) ?: return@mapNotNull null
            SChapter.create().apply {
                url = slug
                name = element.selectFirst(".chapter-content")!!.text().removePrefix("Battle in 5 Seconds After Meeting, ")
                date_upload = dates[href] ?: 0L
                memo = buildJsonObject { put("mangaPath", mangaPath) }
            }
        }
    }
}
