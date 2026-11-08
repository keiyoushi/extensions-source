package eu.kanade.tachiyomi.multisrc.hentaicosplayxxx

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

abstract class HentaiCosplayXxx : KeiSource() {

    private val dateCache = ConcurrentHashMap<String, String>()

    override suspend fun getPopularManga(page: Int): MangasPage = parseListing(client.get("$baseUrl/ranking/page/$page/", Fetch.DOCUMENT))

    private fun parseListing(response: Response): MangasPage {
        val document = response.asJsoup()

        return if (document.selectFirst("div.image-list-item") == null) {
            parseMobileListing(document)
        } else {
            parseDesktopListing(document)
        }
    }

    private fun parseMobileListing(document: Document): MangasPage {
        val entries = document.select("#entry_list > li > a[href*=/image/]")
            .map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    thumbnail_url = element.selectFirst("img")
                        ?.absUrl("src")
                        ?.replace("http://", "https://")
                    title = element.selectFirst("span:not(.posted)")!!.text()
                    element.selectFirst("span.posted")
                        ?.text()?.also { dateCache[url] = it }
                }
            }
        val hasNextPage = document.selectFirst("a.paginator_page[rel=next]") != null

        return MangasPage(entries, hasNextPage)
    }

    private fun parseDesktopListing(document: Document): MangasPage {
        val entries = document.select("div.image-list-item:has(a[href*=/image/])")
            .map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                    thumbnail_url = element.selectFirst("img")
                        ?.absUrl("src")
                        ?.replace("http://", "https://")
                    title = element.select(".image-list-item-title").text()
                    element.selectFirst(".image-list-item-regist-date")
                        ?.text()?.also { dateCache[url] = it }
                }
            }
        val hasNextPage = document.selectFirst("div.wp-pagenavi > a[rel=next]") != null

        return MangasPage(entries, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseListing(client.get("$baseUrl/search/page/$page/", Fetch.DOCUMENT))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotEmpty()) {
            val keyword = query.trim().replace(" ", "+")
            "$baseUrl/search/keyword/$keyword/page/$page/"
        } else {
            val tag = filters.firstInstanceOrNull<TagFilter>()?.selected.orEmpty()
            if (tag.isNotEmpty()) {
                "$baseUrl${tag}page/$page/"
            } else {
                "$baseUrl/search/page/$page/"
            }
        }

        return parseListing(client.get(url, Fetch.DOCUMENT))
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/ranking-tag/", Fetch.DOCUMENT).asJsoup()

        return buildList {
            add(Pair("", ""))
            document.select("#tags a").forEach {
                add(
                    Pair(
                        it.text()
                            .replace(tagNumRegex, "")
                            .trim(),
                        it.attr("href"),
                    ),
                )
            }
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<Pair<String, String>>>() ?: return FilterList()

        return FilterList(
            Filter.Header("Ignored with text search"),
            Filter.Separator(),
            TagFilter("Ranked Tags", tags),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() !in GALLERY_PATHS) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null

        val document = client.get("$baseUrl/image/$slug/", Fetch.DOCUMENT).asJsoup()
        return SManga.create().apply {
            this.url = "/image/$slug/"
            // The page title ends with the site's name
            title = document.selectFirst("meta[property=og:title]")!!.attr("content").substringBeforeLast(" - ")
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (fetchDetails) {
            val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

            manga.apply {
                genre = document.select("#detail_tag a[href*=/tag/]").eachText().joinToString()
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                status = SManga.COMPLETED
            }
        }

        val updatedChapters = if (fetchChapters) {
            SChapter.create().apply {
                name = "Gallery"
                url = manga.url.replace("/image/", "/story/")
                date_upload = dateFormat.tryParseDate(dateCache[manga.url])
            }.let(::listOf)
        } else {
            chapters
        }

        return SMangaUpdate(manga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select("amp-img[src*=upload]:not(.related-thumbnail)")
            .mapIndexed { index, element ->
                Page(
                    index = index,
                    imageUrl = element.attr("src"),
                )
            }
    }

    companion object {
        private val GALLERY_PATHS = setOf("image", "story")
        private val tagNumRegex = Regex("""(\(\d+\))""")
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ENGLISH)
    }
}
