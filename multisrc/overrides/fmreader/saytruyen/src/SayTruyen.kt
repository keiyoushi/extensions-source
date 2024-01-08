package eu.kanade.tachiyomi.extension.vi.saytruyen

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document

class SayTruyen : FMReader("Say Truyen", "https://saytruyenvip.com", "vi") {
    override fun mangaDetailsParse(document: Document): SManga {
        val info = document.select("div.row").first()!!
        return SManga.create().apply {
            author = info.select("div.row li:has(b:contains(Tác giả)) small").text()
            genre = info.select("div.row li:has(b:contains(Thể loại)) small a").joinToString { it.text() }
            status = parseStatus(info.select("div.row li:has(b:contains(Tình trạng)) a").text())
            description = document.select("div.description").text()
            thumbnail_url = info.select("img.thumbnail").attr("abs:src")
        }
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup().let { document ->
            document.select(chapterListSelector()).map {
                chapterFromElement(it).apply {
                    scanlator = document.select("div.row li:has(b:contains(Nhóm dịch)) small").text()
                }
            }
        }
    }
    override fun pageListParse(document: Document): List<Page> = super.pageListParse(document).onEach { it.imageUrl!!.trim() }
}
