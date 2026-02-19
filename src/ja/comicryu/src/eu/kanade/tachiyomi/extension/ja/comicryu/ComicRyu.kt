package eu.kanade.tachiyomi.extension.ja.comicryu

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class ComicRyu : HttpSource() {
    override val name = "Comic Ryu"
    private val domain = "comic-ryu.jp"
    override val baseUrl = "https://www.$domain"
    override val lang = "ja"
    override val supportsLatest = true

    private val subDomain = "https://unicorn.$domain"

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".m-ranking-list.m-list-sakuhin-list.is-week .m-ranking-list-item").map {
            SManga.create().apply {
                title = it.selectFirst(".sakuhin-article-title")!!.text()
                setUrlWithoutDomain(it.selectFirst("a.m-ranking-link")!!.absUrl("href"))
                thumbnail_url = it.selectFirst(".sakuhin-article-thumbnail")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".m-list-recent .m-list-sakuhin-list-item").map {
            SManga.create().apply {
                title = it.selectFirst(".sakuhin-article-title")!!.text()
                setUrlWithoutDomain(it.selectFirst("a.m-list-sakuhin-list-item-link")!!.absUrl("href"))
                thumbnail_url = it.selectFirst(".sakuhin-article-thumbnail")?.absUrl("src")
            }
        }.distinctBy { it.title }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filter = filters.firstInstance<StatusFilter>()
        val path = filter.values[filter.state].value
        val url = if (path.startsWith(subDomain)) {
            path.toHttpUrl()
        } else {
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegment(path)
                .build()
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".m-series-list .m-list-sakuhin-list-item").map {
            SManga.create().apply {
                title = it.selectFirst(".sakuhin-article-title")!!.text()
                val href = it.selectFirst("a")!!.absUrl("href")
                if (href.startsWith(baseUrl)) {
                    setUrlWithoutDomain(href)
                } else {
                    url = href
                }
                thumbnail_url = it.selectFirst(".sakuhin-article-thumbnail")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = if (manga.url.startsWith(subDomain)) {
        GET(manga.url, headers)
    } else {
        super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst(".m-aside .sakuhin-article")!!
        return SManga.create().apply {
            title = info.selectFirst(".sakuhin-article-title")!!.text()
            val authorText = info.selectFirst(".sakuhin-article-author")?.text()?.replace("著者", "")
            if (!authorText.isNullOrEmpty() && authorText.contains("原作：") && authorText.contains("漫画：")) {
                val parts = authorText.split("×")
                author = parts.find { it.contains("原作：") }?.substringAfter("原作：")?.trim()
                artist = parts.find { it.contains("漫画：") }?.substringAfter("漫画：")?.trim()
            } else {
                author = authorText
            }
            description = info.selectFirst(".sakuhin-article-description")?.text()
            thumbnail_url = info.selectFirst(".sakuhin-article-thumbnail")?.absUrl("src")
            status = SManga.UNKNOWN
        }
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(".m-main a.sakuhin-episode-link").mapNotNull {
            val article = it.selectFirst("article.sakuhin-episode") ?: return@mapNotNull null
            if (article.hasClass("is-episode-publish-end")) return@mapNotNull null
            SChapter.create().apply {
                name = article.selectFirst(".sakuhin-episode-title")!!.text()
                val href = it.absUrl("href")
                if (href.startsWith(baseUrl)) {
                    setUrlWithoutDomain(href)
                } else {
                    url = href
                }
            }
        }
        return if (response.request.url.host.contains("unicorn")) {
            chapters
        } else {
            chapters.reversed()
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = if (chapter.url.startsWith(subDomain)) {
        GET(chapter.url, headers)
    } else {
        super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".wp-block-gallery figure.wp-block-image img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Search query is not available"),
        StatusFilter(),
    )

    private inner class StatusFilter :
        Filter.Select<FilterOption>(
            "Status",
            arrayOf(
                FilterOption("連載中", "シリーズ一覧-連載中"),
                FilterOption("完結作品", "完結作品"),
                FilterOption("(ユニコーン) 連載中", "$subDomain/シリーズ一覧-連載中"),
            ),
        )

    private class FilterOption(private val name: String, val value: String) {
        override fun toString() = name
    }
}
