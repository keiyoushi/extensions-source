package eu.kanade.tachiyomi.extension.en.rokaricomics

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class RokariComics :
    MangaThemesia(
        "RokariComics",
        "https://rokaricomics.com",
        "en",
    ) {
    // Popular - Use homepage "Popular Today" section (first page only, no pagination)
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
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
    override fun latestUpdatesRequest(page: Int): Request = if (page == 1) {
        GET(baseUrl, headers)
    } else {
        GET("$baseUrl/page/$page/", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
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
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> {
                    url.addQueryParameter("status", filter.selectedValue())
                }

                is TypeFilter -> {
                    url.addQueryParameter("type", filter.selectedValue())
                }

                is OrderByFilter -> {
                    url.addQueryParameter("order", filter.selectedValue())
                }

                is GenreListFilter -> {
                    filter.state
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .forEach {
                            val value = if (it.state == Filter.TriState.STATE_EXCLUDE) "-${it.value}" else it.value
                            url.addQueryParameter("genre[]", value)
                        }
                }

                else -> { /* Do Nothing */ }
            }
        }
        return GET(url.build(), headers)
    }

    // Filter out chapters that have the coin cost indicator (paywalled chapters)
    // These chapters have a span with "text-gold" class containing the coin price
    override fun chapterListSelector() = "#chapterlist li:has(div.chbox):has(div.eph-num):has(a[href]):not(:has(.text-gold))"

    override fun getFilterList(): FilterList {
        val filters = super.getFilterList().filterNot { it is AuthorFilter || it is YearFilter }
        return FilterList(filters)
    }
}
