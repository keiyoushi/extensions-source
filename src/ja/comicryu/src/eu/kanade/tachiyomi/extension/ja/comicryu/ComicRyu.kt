package eu.kanade.tachiyomi.extension.ja.comicryu

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.boolean
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ComicRyu : KeiSource() {
    private val domain = baseUrl.toHttpUrl().host
    private val subUrl = "https://unicorn.$domain"
    private val subDomain = subUrl.toHttpUrl().host

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select(".m-ranking-list.m-list-sakuhin-list.is-week .m-ranking-list-item").map {
            SManga.create().apply {
                title = it.selectFirst(".sakuhin-article-title")!!.text()
                setUrlWithoutDomain(it.selectFirst("a.m-ranking-link")!!.absUrl("href"))
                thumbnail_url = it.selectFirst(".sakuhin-article-thumbnail")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select(".m-list-recent .m-list-sakuhin-list-item").map {
            SManga.create().apply {
                title = it.selectFirst(".sakuhin-article-title")!!.text()
                setUrlWithoutDomain(it.selectFirst("a.m-list-sakuhin-list-item-link")!!.absUrl("href"))
                thumbnail_url = it.selectFirst(".sakuhin-article-thumbnail")?.absUrl("src")
            }
        }.distinctBy { it.title }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstance<CategoryFilter>()
        val requestUrl = if (category.state == 2) {
            "$subUrl/${category.value}/"
        } else {
            "$baseUrl/${category.value}/"
        }
        val document = client.get(requestUrl).asJsoup()
        val mangas = document.select(".m-series-list .m-list-sakuhin-list-item").map {
            SManga.create().apply {
                title = it.selectFirst(".sakuhin-article-title")!!.text()
                val href = it.selectFirst("a")!!.absUrl("href")
                setUrlWithoutDomain(href)
                thumbnail_url = it.selectFirst(".sakuhin-article-thumbnail")?.absUrl("src")
                if (href.toHttpUrl().host == subDomain) {
                    memo = buildJsonObject {
                        put("unicorn", true)
                    }
                }
            }
        }
        return MangasPage(mangas, false)
    }

    override fun getMangaUrl(manga: SManga): String = if (manga.memo["unicorn"]?.boolean == true) {
        subUrl + manga.url
    } else {
        baseUrl + manga.url
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = getMangaUrl(manga)
        val document = client.get(url).asJsoup()
        val info = document.selectFirst(".m-aside .sakuhin-article")!!
        val mangas = SManga.create().apply {
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
            if (url.toHttpUrl().host == subDomain) {
                memo = buildJsonObject {
                    put("unicorn", true)
                }
            }
        }

        val chapterList = document.select(".m-main a.sakuhin-episode-link").mapNotNull {
            val article = it.selectFirst("article.sakuhin-episode") ?: return@mapNotNull null
            if (article.hasClass("is-episode-publish-end")) return@mapNotNull null
            SChapter.create().apply {
                name = article.selectFirst(".sakuhin-episode-title")!!.text()
                val href = it.absUrl("href")
                setUrlWithoutDomain(href)
                if (href.toHttpUrl().host == subDomain) {
                    memo = buildJsonObject {
                        put("unicorn", true)
                    }
                }
            }
        }

        return SMangaUpdate(
            mangas,
            chapterList.let {
                if (manga.memo["unicorn"]?.boolean == true) it else it.reversed()
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.memo["unicorn"]?.boolean == true) {
        subUrl + chapter.url
    } else {
        baseUrl + chapter.url
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".wp-block-gallery figure.wp-block-image img").mapIndexed { index, page ->
            Page(index, imageUrl = page.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Search query is not available"),
        CategoryFilter(),
    )

    private class CategoryFilter :
        SelectFilter(
            "Category",
            arrayOf(
                "連載中" to "シリーズ一覧-連載中",
                "完結作品" to "完結作品",
                "(ユニコーン) 連載中" to "シリーズ一覧-連載中",
            ),
        )

    private open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val value: String
            get() = vals[state].second
    }
}
