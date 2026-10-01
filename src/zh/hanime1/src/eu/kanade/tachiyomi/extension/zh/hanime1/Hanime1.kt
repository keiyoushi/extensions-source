package eu.kanade.tachiyomi.extension.zh.hanime1

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element

@Source
abstract class Hanime1 : KeiSource() {
    private val comicHomepage get() = "$baseUrl/comics"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(comicHomepage).asJsoup()
        val mangas = document.select("h3:containsOwn(發燒漫畫) ~ div.comic-rows-videos-div")
            .map { comicDivToManga(it) }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$comicHomepage?page=$page").asJsoup()
        val mangas = document.select("h3:containsOwn(最新上傳) ~ div.comic-rows-videos-div")
            .map { comicDivToManga(it) }
        val hasNextPage = document.select("ul.pagination a[rel=next]").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchUrl = comicHomepage.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("query", query)
            .addQueryParameter("page", "$page")

        filters.firstInstanceOrNull<SortFilter>()?.selected?.let {
            searchUrl.addQueryParameter("sort", it)
        }

        val document = client.get(searchUrl.build()).asJsoup()
        val mangas = document.select("div#comics-search-tag-top-row + div div.comic-rows-videos-div")
            .map { comicDivToManga(it) }
        val hasNextPage = document.select("ul.pagination a[rel=next]").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val requestUrl = response.request.url.toString()
        val document = response.asJsoup()

        val brief = document.select("h3.title.comics-metadata-top-row").first()?.parent()
        val updatedManga = SManga.create().apply {
            url = manga.url
            title = brief?.select(".title.comics-metadata-top-row")?.first()?.text() ?: manga.title
            thumbnail_url =
                brief?.parent()?.select("div.col-md-4 img")?.attr("data-srcset")?.extraSrc()
            author = selectInfo("作者：", brief) ?: selectInfo("社團：", brief)
            genre = selectInfo("分類：", brief)
        }

        val chapterList =
            document.select("h3:containsOwn(相關集數列表) ~ div.comic-rows-videos-div")
                .map { element ->
                    SChapter.create().apply {
                        val comicUrl = element.select("a").attr("abs:href")
                        setUrlWithoutDomain("$comicUrl/1")
                        val title = element.select("div.comic-rows-videos-title").text()
                        if (requestUrl == comicUrl) {
                            name = "當前：$title"
                        } else {
                            name = "關聯：$title"
                        }
                    }
                }
                .ifEmpty {
                    listOf(
                        SChapter.create().apply {
                            setUrlWithoutDomain("$requestUrl/1")
                            name = "單章節"
                        },
                    )
                }

        return SMangaUpdate(updatedManga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val currentImage = document.select("img#current-page-image")
        val dataExtension = currentImage.attr("data-extension")
        val dataPrefix = currentImage.attr("data-prefix")
        val pageSize = document.select(".comic-show-content-nav").attr("data-pages").toInt()

        // Galleries mix jpg/webp per page and data-extension only matches the first one; the comic page
        // lists a thumbnail ("<n>t.<ext>") per page with the right extension
        val comicUrl = getChapterUrl(chapter).substringBeforeLast("/")
        val extensions = client.get(comicUrl).asJsoup()
            .select("a[href^=$comicUrl/] img[data-srcset]")
            .associate {
                val number = it.parent()!!.attr("href").substringAfterLast("/")
                number to it.attr("data-srcset").extraSrc().substringAfterLast(".")
            }

        return List(pageSize) { index ->
            val number = "${index + 1}"
            Page(index, imageUrl = "$dataPrefix$number.${extensions[number] ?: dataExtension}")
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
    )

    private fun selectInfo(key: String, brief: Element?): String? = brief?.select(":containsOwn($key)")?.select("div.no-select")?.text()

    private fun comicDivToManga(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("abs:href"))
        title = element.select("div.comic-rows-videos-title").text()
        thumbnail_url = element.select("img").attr("data-srcset").extraSrc()
    }

    private fun String.extraSrc(): String = split(",").first()
}
