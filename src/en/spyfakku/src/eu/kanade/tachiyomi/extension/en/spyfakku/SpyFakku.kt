package eu.kanade.tachiyomi.extension.en.spyfakku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SpyFakku : HttpSource() {

    override val name = "SpyFakku"

    override val baseUrl = "https://fakku.cc"

    private val baseImageUrl = "https://cdn.fakku.cc/image"

    override val lang = "en"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("referer", "$baseUrl/")
        .set("origin", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl?sort=released_at", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.group.h-auto.w-auto.space-y-2").map(::popularMangaFromElement)

        val hasNextPage = document.selectFirst("span[class*='sm:block']:containsOwn(Next)")!!.parent()!!.hasAttr("href")

        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst("[title]")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val terms = mutableListOf(query.trim())

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", filter.getValue())
                        addQueryParameter("order", if (filter.state!!.ascending) "asc" else "desc")
                    }

                    is TextFilter -> {
                        if (filter.state.isNotEmpty()) {
                            terms += filter.state.split(",").filter { it.isNotBlank() }.map { tag ->
                                val trimmed = tag.trim().replace(" ", "_")
                                (if (trimmed.startsWith("-")) "-" else "") + filter.type + ":" + trimmed.removePrefix("-")
                            }
                        }
                    }

                    else -> {}
                }
            }
            addQueryParameter("q", terms.joinToString(" "))
            addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url, headers)
    }

    override fun getFilterList() = getFilters()

    private val dateReformat = SimpleDateFormat("EEEE, d MMM yyyy HH:mm (z)", Locale.ENGLISH)
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy, HH:mm", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return SManga.create().apply {
            val doc = response.asJsoup()
            title = doc.selectFirst("p.text-lg.font-semibold.leading-6")!!.ownText()
            url = "/g/${doc.selectFirst("a[href*='/read']")!!.attr("href").substringBefore("/read").substringAfterLast("/")}"
            author = doc.select("a[href*='artist:']").emptyToNull()?.joinToString { it.ownText() }
            artist = doc.select("a[href*='artist:']").emptyToNull()?.joinToString { it.ownText() }
            genre = doc.select("a[href*='tag:']").emptyToNull()?.joinToString { it.ownText() }
            thumbnail_url = doc.selectFirst("img[src*='/cover']")?.absUrl("src")
            description = buildString {
                doc.select("a[href*='circle:']").emptyToNull()?.joinToString { it.ownText() }?.let {
                    append("Circles: ", it, "\n")
                }
                doc.select("a[href*='publisher:']").emptyToNull()?.joinToString { it.ownText() }?.let {
                    append("Publishers: ", it, "\n")
                }
                doc.select("a[href*='magazine:']").emptyToNull()?.joinToString { it.ownText() }?.let {
                    append("Magazines: ", it, "\n")
                }
                doc.select("a[href*='event:']").emptyToNull()?.joinToString { it.ownText() }?.let {
                    append("Events: ", it, "\n")
                }
                doc.select("a[href*='parody:']").emptyToNull()?.joinToString { it.ownText() }?.let {
                    append("Parodies: ", it, "\n\n")
                }
                doc.selectFirst("p:containsOwn(Released)")?.parent()?.selectFirst(".text-sm")?.ownText()?.let {
                    dateFormat.parse(it)?.let {
                        append("Created At: ", dateReformat.format(it.time), "\n")
                    }
                }
                doc.selectFirst("p:containsOwn(pages)")?.ownText()?.let {
                    append("Pages: ", it, "\n")
                }
            }
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            initialized = true
        }
    }
    private fun Elements.emptyToNull(): Elements? {
        return this.ifEmpty { null }
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()

        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                url = "/g/${doc.selectFirst("a[href*='/read']")!!.attr("href").substringBefore("/read").substringAfterLast("/")}"
                date_upload = doc.selectFirst("p:containsOwn(Released)")?.parent()?.selectFirst(".text-sm")?.ownText()?.let {
                    dateFormat.parse(it)?.time
                } ?: 0L
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        val images = doc.select("img[src*='$baseImageUrl']:not([src*=cover])")
        return images.mapIndexed { index, it ->
            Page(index, imageUrl = it.attr("src").removeSuffix("/thumb"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
}
