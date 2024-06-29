package eu.kanade.tachiyomi.multisrc.flixscans

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

abstract class FlixScans(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        return if (page == 1) {
            GET("$baseUrl/webtoons/romance/home", headers)
        } else {
            GET("$baseUrl/webtoons/action/home", headers)
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select(
            """div.tabs div[wire:snapshot*=App\\Models\\Serie], main div:has(h2:matches(Today\'s Hot|الرائج اليوم)) a[wire:snapshot*=App\\Models\\Serie]""",
        ).map { element ->
            Log.d(name, element.html())
            SManga.create().apply {
                setUrlWithoutDomain(
                    if (element.tagName().equals("a")) {
                        element.absUrl("href")
                    } else {
                        element.selectFirst("a")!!.absUrl("href")
                    },
                )
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                title = element.selectFirst("div.text-sm")!!.text()
            }
        }.distinctBy { it.url }

        return MangasPage(entries, response.request.url.pathSegments.getOrNull(1) == "romance")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/latest?serie_type=webtoon&main_genres=romance" +
            if (page > 1) {
                "&page=$page"
            } else {
                ""
            }

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select("div[wire:snapshot*=App\\\\Models\\\\Serie]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                title = element.select("div.flex a[href*=/series/]").last()!!.text()
            }
        }
        val hasNextPage = document.selectFirst("[role=navigation] button[wire:click*=nextPage]") != null

        return MangasPage(entries, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("serie_type", "webtoon")
            addQueryParameter("title", query.trim())
            // TODO filters
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.select("#full_model h3").text()
            thumbnail_url = document.selectFirst("main img[src*=series/webtoon]")?.absUrl("src")
            status = when (document.getQueryParam("status")) {
                "ongoing", "soon" -> SManga.ONGOING
                "completed", "droped" -> SManga.COMPLETED
                "onhold" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            genre = buildList {
                document.getQueryParam("type")
                    ?.capitalize()?.let(::add)
                document.select("#full_model a[href*=search?genre]")
                    .eachText().let(::addAll)
            }.joinToString()
            author = document.select("#full_model [wire:key^=a-]").eachText().joinToString()
            artist = document.select("#full_model [wire:key^=r-]").eachText().joinToString()
            description = buildString {
                append(document.select("#full_model p").text().trim())
                append("\n\nAlternative Names:\n")
                document.select("#full_model [wire:key^=n-]")
                    .joinToString("\n") { "• ${it.text().trim().removeMdEscaped()}" }
                    .let(::append)
            }.trim()
        }
    }

    private fun Document.getQueryParam(queryParam: String): String? {
        return selectFirst("#full_model a[href*=search?$queryParam]")
            ?.absUrl("href")?.toHttpUrlOrNull()?.queryParameter(queryParam)
    }

    private fun String.capitalize(): String {
        val result = StringBuilder(length)
        var capitalize = true
        for (char in this) {
            result.append(
                if (capitalize) {
                    char.uppercase()
                } else {
                    char.lowercase()
                },
            )
            capitalize = char.isWhitespace()
        }
        return result.toString()
    }

    private val mdRegex = Regex("""&amp;#(\d+);""")

    private fun String.removeMdEscaped(): String {
        val char = mdRegex.find(this)?.groupValues?.get(1)?.toIntOrNull()
            ?: return this

        return replaceFirst(mdRegex, Char(char).toString())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("a[href*=/read/]:not([type=button])").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.select("span.font-normal").text()
                // TODO relative date
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("[wire:key^=image] img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
