package eu.kanade.tachiyomi.extension.en.rokaricomics

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class RokariComics : MangaThemesiaAlt() {
    // Popular - Use homepage "Popular Today" section (first page only, no pagination)
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        // Select manga from "Popular Today" section (first listupd on homepage)
        val mangas = document.select(".bixbox:has(h2:contains(Popular)) .bs .bsx").map { element ->
            SManga.create().apply {
                element.select("a").first()?.let {
                    setUrlWithoutDomain(it.attr("href"))
                    title = it.attr("title")
                }
                thumbnail_url = element.select("img").firstOrNull()?.let { img ->
                    img.attr("abs:data-lazy-src").ifEmpty {
                        img.attr("abs:data-src").ifEmpty {
                            img.attr("abs:src")
                        }
                    }
                }
            }
        }
        return MangasPage(mangas, false)
    }

    // Latest - Use homepage pagination which shows latest updates
    override suspend fun getLatestUpdates(page: Int) = latestUpdatesParse(client.get(if (page == 1) baseUrl else "$baseUrl/page/$page/"))

    private fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        // Select manga from "Latest Update" section (second listupd on homepage)
        val mangas = document.select(".bixbox:has(h2:contains(Latest)) .bs .bsx").map { element ->
            SManga.create().apply {
                element.select("a").first()?.let {
                    setUrlWithoutDomain(it.attr("href"))
                    title = it.attr("title")
                }
                thumbnail_url = element.select("img").firstOrNull()?.let { img ->
                    img.attr("abs:data-lazy-src").ifEmpty {
                        img.attr("abs:data-src").ifEmpty {
                            img.attr("abs:src")
                        }
                    }
                }
            }
        }
        val hasNextPage = document.selectFirst("div.hpage .r, div.pagination .next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Site changed from /manga/ directory to using search page /?s=
    override fun searchMangaUrl(page: Int, query: String) = baseUrl.toHttpUrl().newBuilder()
        .addQueryParameter("s", query)
        .addQueryParameter("page", page.toString())

    // Filter out chapters that have the coin cost indicator (paywalled chapters)
    // These chapters have a span with "text-gold" class containing the coin price
    override fun chapterListSelector() = "#chapterlist li:has(div.chbox):has(div.eph-num):has(a[href]):not(:has(.text-gold))"

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = super.getFilterList(data).filterNot { it is AuthorFilter || it is YearFilter }
        return FilterList(filters)
    }
}
