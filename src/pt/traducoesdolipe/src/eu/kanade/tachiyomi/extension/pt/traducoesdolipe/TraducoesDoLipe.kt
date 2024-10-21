package eu.kanade.tachiyomi.extension.pt.traducoesdolipe

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

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

    override fun chapterListRequest(manga: SManga): Request {
        val projectName = manga.url.substringAfterLast("#")
        val url = apiUrl(chapterCategory)
            .addPathSegments(projectName)
            .addQueryParameter("max-results", maxChapterResults.toString())
            .build()
        return GET(url, headers)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val project = client.newCall(mangaDetailsRequest(manga)).execute().let {
            it.asJsoup().select("script").map(Element::html)
                .firstOrNull { script -> script.contains("catNameProject") }
                ?.let { script -> PROJECT_NAME_REGEX.find(script)?.groups?.get("project")?.value }
        }

        return client.newCall(chapterListRequest(manga.apply { url = "$url#$project" }))
            .asObservableSuccess()
            .map(::chapterListParse)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.decodeFromString<ZeistMangaDto>(response.body.string())
        return result.feed?.entry?.filter { it.category.orEmpty().any { category -> category.term == chapterCategory } }
            ?.map { it.toSChapter(baseUrl) }
            ?: throw Exception("Failed to parse from chapter API")
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
