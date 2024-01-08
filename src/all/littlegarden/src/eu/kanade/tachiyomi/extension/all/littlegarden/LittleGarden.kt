package eu.kanade.tachiyomi.extension.all.littlegarden

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class LittleGarden : ParsedHttpSource() {
    override val name = "Little Garden"
    override val baseUrl = "https://littlexgarden.com/"
    override val lang = "all"
    override val supportsLatest = true

    companion object {
        private const val cdnUrl = "https://littlexgarden.com/static/images/webp/"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
        private val slugRegex = Regex("\\\\\"slug\\\\\":\\\\\"(.*?(?=\\\\\"))")
        private val oricolPageRegex = Regex("\\{colored:(?<colored>.*?(?=,)),original:(?<original>.*?(?=,))")
        private val oriPageRegex = Regex("""original:"(.*?(?="))""")
    }

    // Popular
    override fun popularMangaRequest(page: Int) = GET(baseUrl)
    override fun popularMangaSelector() = "div.listing div .col-md-6.col-lg-6.col-xl-4.col-12"
    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select(".item-title .name").text().trim()
        setUrlWithoutDomain(element.select("a").attr("href"))
        thumbnail_url = element.select(".thumb").attr("style").substringAfter("(").substringBefore(")")
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl)
    override fun latestUpdatesSelector() = ".d-sm-block.col-sm-6.col-lg-6.col-xl-3.col-12"
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h3")!!.text().trim()
        setUrlWithoutDomain(element.select("a").attr("href").substringBeforeLast("/"))
        thumbnail_url = element.select(".img.image-item").attr("style").substringAfter("(").substringBefore(")")
    }
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }.distinctBy { it.title }
        return MangasPage(mangas, false)
    }

    // Search
    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        var mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val query = response.request.headers["query"]
        if (query != null) {
            mangas = mangas.filter { it.title.contains(query, true) }
        }
        return MangasPage(mangas, false)
    }
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val headers = headersBuilder()
            .add("query", query)
            .build()
        return GET(baseUrl, headers)
    }

    // Manga details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create()

    // Chapter list
    override fun chapterListSelector() = throw Exception("Not used")
    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val slug = slugRegex.find(document.toString())?.groupValues?.get(1)
        fun buildQuery(queryAction: () -> String) = queryAction().replace("%", "$")
        val query = buildQuery {
            """
                query chapters(
                	%slug: String,
                	%limit: Float,
                	%skip: Float,
                	%order: Float!,
                	%isAdmin: Boolean!
                ) {
                	chapters(
                		limit: %limit,
                		skip: %skip,
                		where: {
                			deleted: false,
                			published: %isAdmin,
                			manga: {
                                slug: %slug,
                                published: %isAdmin,
                                deleted: false
                            }
                		},
                        order: [{ field: "number", order: %order }]
                	) {
                		published
                        likes
                        id
                        number
                        thumb
                        manga {
                          id
                          name
                          slug
                          __typename
                        }
                        __typename
                	}
                }
            """.trimIndent()
        }
        val payload = buildJsonObject {
            put("operationName", "chapters")
            put("query", query)
            putJsonObject("variables") {
                put("slug", slug)
                put("order", -1)
                put("limit", 2000)
                put("skip", 0)
                put("isAdmin", true)
            }
        }
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val newHeaders = headersBuilder()
            .add("Content-Length", body.contentLength().toString())
            .add("Content-Type", body.contentType().toString())
            .build()
        val request = Request.Builder()
            .method("POST", body)
            .url("https://littlexgarden.com/graphql") // Request directly their data rather than scraping a page as chapters are dynamically loaded
            .headers(newHeaders)
            .build()
        val resp = client.newCall(request).execute()
        val chapters = Json.parseToJsonElement(resp.body.string()).jsonObject["data"]?.jsonObject?.get("chapters")?.jsonArray
        if (chapters != null) {
            return chapters.map {
                SChapter.create().apply {
                    val chap = it.jsonObject["number"].toString()
                    val manga = it.jsonObject["manga"]?.jsonObject?.get("name").toString().replace("\"", "")
                    setUrlWithoutDomain("/$slug/$chap")
                    name = "$manga - $chap"
                    chapter_number = chap.toFloat()
                    date_upload = 0L
                }
            }
        }
        return mutableListOf()
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val chapNb = document.selectFirst("div.chapter-number")!!.text().trim().toInt()
        val engChaps: IntArray = intArrayOf(970, 987, 992)
        if (document.selectFirst("div.manga-name")!!.text().trim() == "One Piece" && (engChaps.contains(chapNb) || chapNb > 1004)) { // Permits to get French pages rather than English pages for some chapters
            oricolPageRegex.findAll(document.select("script:containsData(pages)").toString()).asIterable().mapIndexed { i, it ->
                if (it.groups["colored"]?.value?.contains("\"") == true) { // Their JS dict has " " around the link only when available. Also uses colored pages rather than B&W as it's the main strength of this site
                    pages.add(Page(i, "", cdnUrl + it.groups["colored"]?.value?.replace("\"", "") + ".webp"))
                } else {
                    pages.add(Page(i, "", cdnUrl + it.groups["original"]?.value?.replace("\"", "") + ".webp"))
                }
            }
        } else {
            oriPageRegex.findAll(document.toString()).asIterable().mapIndexed { i, it ->
                pages.add(Page(i, "", cdnUrl + it.groupValues[1] + ".webp"))
            }
        }
        return pages
    }
    override fun imageUrlParse(document: Document): String = throw Exception("Not used")
}
