package eu.kanade.tachiyomi.extension.es.lectortmo

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class LectorTmoFactory : SourceFactory {

    override fun createSources() = listOf(
        LectorManga(),
        TuMangaOnline(),
    )
}

val rateLimitClient = Injekt.get<NetworkHelper>().cloudflareClient.newBuilder()
    .rateLimit(1, 1500, TimeUnit.MILLISECONDS)
    .build()

class TuMangaOnline : LectorTmo("TuMangaOnline", "https://zonatmo.com", "es", rateLimitClient) {
    override val id = 4146344224513899730
}

class LectorManga : LectorTmo("LectorManga", "https://lectormanga.com", "es", rateLimitClient) {
    override val id = 7925520943983324764

    override fun popularMangaSelector() = ".col-6 .card"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("a").text()
        thumbnail_url = element.select("img").attr("src")
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.selectFirst("h1:has(small)")?.let { title = it.ownText() }
        genre = document.select("a.py-2").joinToString(", ") {
            it.text()
        }
        description = document.select(".col-12.mt-2").text()
        status = parseStatus(document.select(".status-publishing").text())
        thumbnail_url = document.select(".text-center img.img-fluid").attr("src")
    }

    override fun chapterListParse(response: Response): List<SChapter> = mutableListOf<SChapter>().apply {
        val document = response.asJsoup()

        // One-shot
        if (document.select("#chapters").isEmpty()) {
            return document.select(oneShotChapterListSelector).map { chapterFromElement(it, oneShotChapterName) }
        }

        // Regular list of chapters
        val chapterNames = document.select("#chapters h4.text-truncate")
        val chapterInfos = document.select("#chapters .chapter-list")

        chapterNames.forEachIndexed { index, _ ->
            val scanlator = chapterInfos[index].select("li")
            if (getScanlatorPref()) {
                scanlator.forEach { add(chapterFromElement(it, chapterNames[index].text())) }
            } else {
                scanlator.last { add(chapterFromElement(it, chapterNames[index].text())) }
            }
        }
    }

    override fun chapterFromElement(element: Element, chName: String) = SChapter.create().apply {
        url = element.select("div.row > .text-right > a").attr("href")
        name = chName
        scanlator = element.select("div.col-12.text-truncate span").text()
        date_upload = element.select("span.badge.badge-primary.p-2").first()?.text()?.let {
            parseChapterDate(it)
        } ?: 0
    }
}
