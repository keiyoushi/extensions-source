package eu.kanade.tachiyomi.extension.en.hentailoop

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HentaiLoop : HttpSource() {

    override val name = "HentaiLoop"
    override val baseUrl = "https://hentailoop.com"
    override val lang = "en"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun buildSearchPayload(query: String, sort: String, offset: Int): FormBody {
        val reqJson = buildJsonObject {
            put("query", query)
            putJsonArray("filters") {
                val taxs = listOf(
                    "manga-genres", "post_tag", "manga-parodies",
                    "manga-artists", "manga-characters", "manga-circles",
                    "manga-collections", "manga-conventions", "manga-languages",
                )
                taxs.forEach { tax ->
                    addJsonObject {
                        put("name", tax)
                        putJsonArray("filterValues") {}
                        put("operator", "in")
                    }
                    addJsonObject {
                        put("name", tax)
                        putJsonArray("filterValues") {}
                        put("operator", "ex")
                    }
                }
            }
            putJsonArray("specialFilters") {
                addJsonObject {
                    put("name", "yearFilter")
                    put("yearOperator", "in")
                    put("yearValue", "")
                }
                addJsonObject {
                    put("name", "pagesFilter")
                    putJsonObject("values") {
                        put("min", 0)
                        put("max", 2000)
                    }
                }
                addJsonObject {
                    put("name", "checkboxFilter")
                    putJsonObject("values") {
                        put("purpose", "uncensored-filter")
                        put("checked", false)
                    }
                }
                addJsonObject {
                    put("name", "checkboxFilter")
                    putJsonObject("values") {
                        put("purpose", "unread-filter")
                        put("checked", false)
                    }
                }
            }
            put("sorting", sort)
        }.toString()

        val builder = FormBody.Builder()
            .add("action", "advanced_search")
            .add("subAction", "search_query")
            .add("request", reqJson)

        if (offset > 0) {
            builder.add("offset", offset.toString())
        }

        return builder.build()
    }

    override fun popularMangaRequest(page: Int): Request = POST("$baseUrl/wp-admin/admin-ajax.php", headers, buildSearchPayload("", "views", (page - 1) * 10))

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchResponseDto>()

        val mangas = dto.posts.map { html ->
            val doc = Jsoup.parseBodyFragment(html, baseUrl)
            SManga.create().apply {
                val a = doc.selectFirst(".left-side a") ?: throw Exception("Manga link not found")
                setUrlWithoutDomain(a.attr("abs:href"))
                title = doc.selectFirst(".right-side .title")?.text() ?: throw Exception("Title not found")
                thumbnail_url = doc.selectFirst(".thumb img")?.let {
                    it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
                }
            }
        }

        return MangasPage(mangas, dto.hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = POST("$baseUrl/wp-admin/admin-ajax.php", headers, buildSearchPayload("", "date", (page - 1) * 10))

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val sorting = when (sortFilter?.state?.index) {
            1 -> "date"
            2 -> "likes"
            3 -> "dislikes"
            else -> "views"
        }
        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, buildSearchPayload(query, sorting, (page - 1) * 10))
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        return SManga.create().apply {
            title = doc.selectFirst(".manga-title h1")?.text() ?: throw Exception("Title not found")
            author = doc.select(".manga-term-name:contains(Artists) + .manga-term-content a").joinToString { it.text() }
            artist = author
            genre = doc.select(".manga-term-name:contains(Genres) + .manga-term-content a, .manga-term-name:contains(Tags) + .manga-term-content a").joinToString { it.text() }
            description = doc.selectFirst(".desc-itself")?.text()?.takeIf { it != "There is no description yet. Suggest it and help others!" }
            thumbnail_url = doc.selectFirst(".manga-thumb img")?.let {
                it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
            }
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val chapter = SChapter.create().apply {
            name = "Chapter"
            setUrlWithoutDomain(response.request.url.toString())

            // Attempt to extract proper upload date provided by Yoast SEO JSON-LD block
            val jsonLd = doc.select("script[type=application/ld+json].yoast-schema-graph").html()
            if (jsonLd.isNotBlank()) {
                val dateRegex = """"datePublished"\s*:\s*"([^"]+)"""".toRegex()
                val dateString = dateRegex.find(jsonLd)?.groupValues?.get(1)
                if (dateString != null) {
                    date_upload = dateFormat.tryParse(dateString)
                }
            }
        }
        return listOf(chapter)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = baseUrl + chapter.url + "read/"
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()

        return doc.select(".gallery-item img").mapIndexed { index, img ->
            val imageUrl = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

class SortFilter :
    Filter.Sort(
        "Sort by",
        arrayOf("Views", "Date", "Likes", "Dislikes"),
        Filter.Sort.Selection(0, false),
    )
