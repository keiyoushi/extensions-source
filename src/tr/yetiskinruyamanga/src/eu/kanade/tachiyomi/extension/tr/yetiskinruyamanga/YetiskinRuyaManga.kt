package eu.kanade.tachiyomi.extension.tr.yetiskinruyamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import okio.IOException
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class YetiskinRuyaManga : Madara(
    "Yetiskin Ruya Manga",
    "https://www.yetiskinruyamanga.com",
    "tr",
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val filterNonMangaItems = false

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/$mangaSubString/${searchPage(page)}?m_orderby=trending", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).map { chapter ->
            chapter.apply {
                CHAPTER_NUMBER_REGEX.find(name)?.groups?.get(0)?.value?.toFloat()?.let {
                    chapter_number = it
                }
            }
        }.sortedByDescending(SChapter::chapter_number)
    }

    override fun pageListParse(document: Document): List<Page> {
        val isLoginRequired = document.select(".content-blocked.login-required").isNotEmpty()
        if (isLoginRequired) {
            throw IOException("You may need to login via WebView")
        }
        return super.pageListParse(document)
    }

    companion object {
        val CHAPTER_NUMBER_REGEX = """\d+(?:\.\d+)?""".toRegex()
    }
}
