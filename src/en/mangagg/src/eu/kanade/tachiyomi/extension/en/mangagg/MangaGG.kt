package eu.kanade.tachiyomi.extension.en.mangagg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaGG : Madara() {
    override val mangaSubString = "comic"
    override val filterNonMangaItems = false
    override val chapterMode = ChapterMode.MangaAjaxPaginated
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)

    override fun getHomeUrl() = "$baseUrl/$mangaSubString/?m_orderby=trending"

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaPage(page, "trending")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaPage(page, "latest")

    private suspend fun getMangaPage(page: Int, order: String): MangasPage {
        val url = baseUrl.toHttpUrl().resolve("/$mangaSubString/")!!.newBuilder().apply {
            if (page > 1) addPathSegments("page/$page/")
            addQueryParameter("m_orderby", order)
        }.build()
        val document = client.get(url).asJsoup()
        val mangas = parseArchive(document)
        return MangasPage(mangas, document.selectFirst(".navigation-ajax, .load-ajax") != null && mangas.isNotEmpty())
    }

    override fun imageFromElement(element: Element): String? {
        val url = element.attr("data-src").trim().ifEmpty {
            element.attr("data-lazy-src").trim()
        }.ifEmpty {
            element.attr("data-cfsrc").trim()
        }.ifEmpty {
            element.attr("data-manga-src").trim()
        }.ifEmpty {
            element.attr("src").trim()
        }

        // Jsoup's absUrl fails if a URL has leading spaces.
        // If it starts with http, it is already an absolute URL.
        return if (url.startsWith("http")) url else super.imageFromElement(element)?.trim()
    }
}
