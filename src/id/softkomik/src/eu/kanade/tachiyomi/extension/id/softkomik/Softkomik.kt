package eu.kanade.tachiyomi.extension.id.softkomik

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.injectLazy

class Softkomik : HttpSource() {

    override val name = "Softkomik"
    override val baseUrl = "https://softkomik.com"
    override val lang = "id"
    override val supportsLatest = true

    private val json: Json by injectLazy()
    private val cdnUrl = "https://cdn.softkomik.com"

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val html = response.body.string()
        val nextData = extractNextData(html) ?: return MangasPage(emptyList(), false)

        // Path: props.pageProps.data.newKomik
        val mangaList = nextData["props"]?.jsonObject
            ?.get("pageProps")?.jsonObject
            ?.get("data")?.jsonObject
            ?.get("newKomik")?.jsonArray
            ?: return MangasPage(emptyList(), false)

        val mangas = mangaList.mapNotNull { item ->
            parseMangaFromJson(item.jsonObject)
        }

        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val html = response.body.string()
        val nextData = extractNextData(html) ?: return MangasPage(emptyList(), false)

        // Path: props.pageProps.data.updateNonProject
        val mangaList = nextData["props"]?.jsonObject
            ?.get("pageProps")?.jsonObject
            ?.get("data")?.jsonObject
            ?.get("updateNonProject")?.jsonArray
            ?: return MangasPage(emptyList(), false)

        val mangas = mangaList.mapNotNull { item ->
            parseMangaFromJson(item.jsonObject)
        }

        return MangasPage(mangas, false)
    }

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/cari?keyword=$query", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val html = response.body.string()
        val nextData = extractNextData(html) ?: return parseSearchFromHtml(html)

        // Coba berbagai key yang mungkin
        val pageProps = nextData["props"]?.jsonObject?.get("pageProps")?.jsonObject
        val data = pageProps?.get("data")?.jsonObject

        val mangaList = data?.get("result")?.jsonArray
            ?: data?.get("listKomik")?.jsonArray
            ?: data?.get("komik")?.jsonArray
            ?: pageProps?.get("result")?.jsonArray
            ?: return parseSearchFromHtml(html)

        val mangas = mangaList.mapNotNull { item ->
            parseMangaFromJson(item.jsonObject)
        }

        return MangasPage(mangas, false)
    }

    private fun parseSearchFromHtml(html: String): MangasPage {
        val document = Jsoup.parse(html)
        val mangas = document.select(".item-komik").mapNotNull { el ->
            SManga.create().apply {
                val linkEl = el.selectFirst(".item-title a, a") ?: return@mapNotNull null
                url = "/" + linkEl.attr("href").removePrefix(baseUrl).removePrefix("/")
                title = linkEl.text().trim().ifEmpty { return@mapNotNull null }
                thumbnail_url = el.selectFirst("noscript img")?.attr("src")
                    ?: el.selectFirst("img")?.attr("src")
            }
        }
        return MangasPage(mangas, false)
    }

    // ============================== Details ===============================
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val html = response.body.string()
        val nextData = extractNextData(html)

        val pageProps = nextData?.get("props")?.jsonObject?.get("pageProps")?.jsonObject
        val data = pageProps?.get("data")?.jsonObject
        val komikData = data?.get("komikData")?.jsonObject
            ?: data?.get("komik")?.jsonObject
            ?: data

        if (komikData != null) {
            return SManga.create().apply {
                title = komikData["title"]?.jsonPrimitive?.content ?: ""
                description = komikData["sinopsis"]?.jsonPrimitive?.content
                    ?: komikData["description"]?.jsonPrimitive?.content
                thumbnail_url = fixImageUrl(komikData["gambar"]?.jsonPrimitive?.content)
                author = komikData["author"]?.jsonPrimitive?.content
                artist = komikData["artist"]?.jsonPrimitive?.content
                status = parseStatus(komikData["status"]?.jsonPrimitive?.content)
                genre = try {
                    komikData["genre"]?.jsonArray?.joinToString { it.jsonPrimitive.content }
                } catch (e: Exception) {
                    komikData["genre"]?.jsonPrimitive?.content
                }
            }
        }

        // Fallback HTML
        val document = Jsoup.parse(html)
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text() ?: ""
            description = document.selectFirst(".sinopsis, .synopsis")?.text()
            thumbnail_url = document.selectFirst("noscript img")?.attr("src")
        }
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed", "tamat" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body.string()
        val nextData = extractNextData(html)
        val mangaSlug = response.request.url.pathSegments.lastOrNull() ?: ""

        val pageProps = nextData?.get("props")?.jsonObject?.get("pageProps")?.jsonObject
        val data = pageProps?.get("data")?.jsonObject

        val chaptersArray = data?.get("chapters")?.jsonArray
            ?: data?.get("listChapter")?.jsonArray
            ?: data?.get("komikData")?.jsonObject?.get("chapters")?.jsonArray

        if (chaptersArray != null && chaptersArray.isNotEmpty()) {
            return chaptersArray.mapNotNull { ch ->
                val obj = ch.jsonObject
                SChapter.create().apply {
                    val chSlug = obj["chapter_slug"]?.jsonPrimitive?.content
                        ?: obj["slug"]?.jsonPrimitive?.content
                        ?: return@mapNotNull null

                    val chNum = obj["chapter"]?.jsonPrimitive?.content
                        ?: obj["chapter_number"]?.jsonPrimitive?.content
                        ?: chSlug

                    name = "Chapter $chNum"
                    url = "/$mangaSlug/$chSlug"
                    date_upload = 0L
                }
            }.reversed()
        }

        // Fallback HTML
        val document = Jsoup.parse(html)
        return document.select(".list-chapter a, .chapter-list a").map { el ->
            SChapter.create().apply {
                url = "/" + el.attr("href").removePrefix(baseUrl).removePrefix("/")
                name = el.text().trim()
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val nextData = extractNextData(html)

        val pageProps = nextData?.get("props")?.jsonObject?.get("pageProps")?.jsonObject
        val data = pageProps?.get("data")?.jsonObject

        // Coba berbagai key untuk images
        val images = data?.get("chapter_image")?.jsonArray
            ?: data?.get("images")?.jsonArray
            ?: data?.get("chapterData")?.jsonObject?.get("images")?.jsonArray
            ?: data?.get("chapter")?.jsonObject?.get("images")?.jsonArray

        if (images != null && images.isNotEmpty()) {
            return images.mapIndexed { index, img ->
                val imageUrl = fixImageUrl(img.jsonPrimitive.content) ?: ""
                Page(index, imageUrl = imageUrl)
            }
        }

        // Fallback HTML
        val document = Jsoup.parse(html)
        return document.select("#readerarea img, .reader-area img, .chapter-image img").mapIndexed { index, el ->
            val imgUrl = el.attr("src").ifEmpty { el.attr("data-src") }
            Page(index, imageUrl = fixImageUrl(imgUrl) ?: "")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // ============================== Helpers ===============================
    private fun parseMangaFromJson(obj: JsonObject): SManga? {
        val slug = obj["title_slug"]?.jsonPrimitive?.content
            ?: obj["slug"]?.jsonPrimitive?.content
            ?: return null

        val title = obj["title"]?.jsonPrimitive?.content ?: return null

        return SManga.create().apply {
            url = "/$slug"
            this.title = title
            thumbnail_url = fixImageUrl(obj["gambar"]?.jsonPrimitive?.content)
        }
    }

    private fun extractNextData(html: String): JsonObject? {
        val regex = """<script id="__NEXT_DATA__" type="application/json">(.+?)</script>""".toRegex()
        val match = regex.find(html) ?: return null
        return try {
            json.parseToJsonElement(match.groupValues[1]).jsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun fixImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("http") -> url
            url.startsWith("/") -> "$cdnUrl$url"
            else -> "$cdnUrl/$url"
        }
    }
}
