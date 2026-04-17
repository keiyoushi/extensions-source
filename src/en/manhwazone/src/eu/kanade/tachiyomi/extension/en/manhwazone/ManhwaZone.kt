package eu.kanade.tachiyomi.extension.en.manhwazone

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaZone : HttpSource() {

    override val name = "ManhwaZone"
    override val baseUrl = "https://manhwazone.com"
    override val lang = "en"
    override val supportsLatest = true

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series?sortBy=popularity&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/series?sortBy=latest&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith("https://") && query.contains(baseUrl.toHttpUrl().host)) {
            return GET(query, headers)
        }

        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("keyword", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sortBy", filter.toUriPart())
                is StatusFilter -> {
                    val status = filter.toUriPart()
                    if (status.isNotEmpty()) {
                        url.addQueryParameter("status", status)
                    }
                }
                is GenreFilterGroup -> {
                    val selectedGenres = filter.state
                        .filter { it.state }
                        .map { it.slug }
                    if (selectedGenres.isNotEmpty()) {
                        url.addQueryParameter("genres", selectedGenres.joinToString("_"))
                    }
                }
                else -> {}
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (response.request.url.encodedPath.startsWith("/series/")) {
            val manga = parseMangaDetails(document)
            manga.url = response.request.url.encodedPath
            return MangasPage(listOf(manga), false)
        }
        return parseMangaList(document)
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("article.group").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".min-w-0 > a.font-semibold")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("a[rel=next], nav a:contains(›)") != null || mangas.size >= 24
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga = parseMangaDetails(response.asJsoup())

    private fun parseMangaDetails(document: Document): SManga {
        val manga = SManga.create()

        manga.title = document.selectFirst("h1.page-title")!!.text()
        manga.description = document.selectFirst("p.page-subtitle")?.text()
        manga.thumbnail_url = document.selectFirst("img.aspect-\\[7\\/10\\], figure.relative img")?.attr("abs:src")
        manga.genre = document.select("a.badge-genre").joinToString(", ") { it.text() }

        val statusText = document.selectFirst("span.badge-sm, span:contains(On Going), span:contains(Completed)")?.text()?.trim()
        manga.status = when (statusText?.lowercase()) {
            "on going", "ongoing", "currently publishing" -> SManga.ONGOING
            "completed", "finished" -> SManga.COMPLETED
            "on hiatus" -> SManga.ON_HIATUS
            "discontinued", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        val jsonLd = document.selectFirst("script[type=application/ld+json]")?.data()
        if (jsonLd != null) {
            val authorRegex = """"author":\s*\[\s*\{"@type":"Person","name":"([^"]+)"""".toRegex()
            val authorMatch = authorRegex.find(jsonLd)?.groupValues?.get(1)
            if (authorMatch != null && authorMatch.lowercase() != "unknown") {
                manga.author = authorMatch
            }
        }

        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val wireDiv = document.selectFirst("div[wire:snapshot][wire:id][wire:init=bootLoad]")
            ?: return emptyList()

        val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
        val snapshot = wireDiv.attr("wire:snapshot")

        val payload = buildJsonObject {
            put("_token", csrfToken)
            put(
                "components",
                buildJsonArray {
                    addJsonObject {
                        put("snapshot", snapshot)
                        put("updates", buildJsonObject {})
                        put(
                            "calls",
                            buildJsonArray {
                                addJsonObject {
                                    put("path", "")
                                    put("method", "bootLoad")
                                    put("params", buildJsonArray {})
                                }
                            },
                        )
                    }
                },
            )
        }

        val postHeaders = headersBuilder()
            .add("Accept", "application/json")
            .add("Content-Type", "application/json")
            .build()

        val postRequest = POST(
            "$baseUrl/livewire/update",
            postHeaders,
            payload.toJsonString().toRequestBody("application/json".toMediaType()),
        )

        val postResponse = client.newCall(postRequest).execute()
        if (!postResponse.isSuccessful) return emptyList()

        val updateDto = postResponse.parseAs<LivewireUpdateDto>()
        val snapshotStr = updateDto.components.firstOrNull()?.snapshot ?: return emptyList()
        val snapshotDto = snapshotStr.parseAs<SnapshotDto>()

        val chaptersArray = snapshotDto.data?.chapters?.jsonArray ?: return emptyList()
        val actualChapters = chaptersArray.getOrNull(0)?.jsonArray ?: return emptyList()

        val chapters = mutableListOf<SChapter>()
        for (item in actualChapters) {
            val chapterTuple = item.jsonArray
            val chapterElement = chapterTuple.getOrNull(0) ?: continue
            val chapterDto = chapterElement.parseAs<ChapterDto>()

            val webUrl = chapterDto.webUrl ?: continue
            val name = chapterDto.name ?: "Chapter"
            val dateStr = chapterDto.published

            chapters.add(
                SChapter.create().apply {
                    url = webUrl
                    this.name = name
                    date_upload = dateFormat.tryParse(dateStr)
                },
            )
        }
        return chapters
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val rsConfScript = document.selectFirst("script:containsData(__RS_CONF__)")?.data()

        if (rsConfScript != null) {
            try {
                val jsonStr = Regex("""__RS_CONF__\s*=\s*(\{.*?\})\s*;""").find(rsConfScript)?.groupValues?.get(1)
                if (jsonStr != null) {
                    val rsConf = jsonStr.parseAs<RsConfDto>()

                    if (rsConf.p != null && rsConf.expire != null && rsConf.signature != null && rsConf.tt != null && rsConf.tt > 0) {
                        return (1..rsConf.tt).map { i ->
                            val pageStr = String.format(Locale.ROOT, "%03d", i)
                            val imageUrl = "https://img.mangalaxy.net/_img/${rsConf.p}/$pageStr.webp?e=${rsConf.expire}&s=${rsConf.signature}"
                            Page(i - 1, "", imageUrl)
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback to data-src fetching below if conversion fails or if tt is 0
            }
        }

        // Fallback approach if __RS_CONF__ parsing fails or tt == 0
        return document.select("img.lazy-image[data-src]").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:data-src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ── Filters ───────────────────────────────────────────────────────────────

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilterGroup(getGenreList()),
    )
}
