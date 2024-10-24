package eu.kanade.tachiyomi.extension.pt.traducoesdolipe

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class TraducoesDoLipe : ZeistManga(
    "Traduções do Lipe",
    "https://traducoesdolipe.blogspot.com",
    "pt-BR",
) {
    override val supportsLatest = false
    override val mangaCategory = "Projeto"
    override val chapterCategory = "Capítulo"
    override val hasFilters = true
    override val hasStatusFilter = false
    override val hasTypeFilter = false
    override val hasLanguageFilter = false
    override val hasGenreFilter = true

    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)

    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("meta[property='og:description']")!!.attr("content").trim()
        description = document.selectFirst(".synopsis")?.text()
        thumbnail_url = document.selectFirst("meta[property='og:image']")?.attr("content")
        genre = document.select(".genres a").joinToString { it.text() }
        status = parseStatus(document.selectFirst(".status")!!.ownText().trim())
        setUrlWithoutDomain(document.location())
    }

    override fun getChapterFeedUrl(doc: Document): String {
        val feed = doc.select("script").map(Element::html)
            .firstOrNull { script -> script.contains("catNameProject") }
            ?.let { script -> PROJECT_NAME_REGEX.find(script)?.groups?.get("project")?.value }
            ?: throw Exception("Não foi possivel encontrar o nome do projeto")

        return apiUrl(chapterCategory)
            .addPathSegments(feed)
            .addQueryParameter("max-results", maxChapterResults.toString())
            .build().toString()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = document.selectFirst(".chapter script")!!.html().let {
            val list = PAGES_REGEX.find(it)?.groups?.get("pages")?.value
            json.decodeFromString<List<String>>(list!!)
        }

        return pages.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    companion object {
        val PROJECT_NAME_REGEX = """=\s+?\('(?<project>[^']+)""".toRegex()
        val PAGES_REGEX = """=(?<pages>\[[^]]+])""".toRegex()
    }
}
