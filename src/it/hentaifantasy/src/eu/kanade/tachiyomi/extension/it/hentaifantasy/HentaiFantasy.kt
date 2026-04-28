package eu.kanade.tachiyomi.extension.it.hentaifantasy

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HentaiFantasy : HttpSource() {
    override val name = "HentaiFantasy"
    override val baseUrl = "https://hentaifantasy.it"
    override val lang = "it"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        private val pagesUrlPattern = Regex(""""url":"(.*?)"""")
        private val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.ROOT)
    }

    // ── Popular ──────────────────────────────────────────────────────────────
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/most_downloaded/$page/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.element").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.thumb")!!.absUrl("href"))
                title = element.selectFirst("div.title > a")!!.attr("title")
                thumbnail_url = element.selectFirst("img.cover")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("div.next > a.gbutton:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ── Latest ───────────────────────────────────────────────────────────────
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest/$page/", headers)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ── Search ───────────────────────────────────────────────────────────────
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tags = mutableListOf<String>()
        val paths = mutableListOf<String>()
        filters.firstInstanceOrNull<TagList>()?.state
            ?.filter { it.state }
            ?.forEach { tag ->
                paths.add(tag.name.lowercase().replace(" ", "_"))
                tags.add(tag.id.toString())
            }

        val searchTags = tags.isNotEmpty()
        if (!searchTags && query.length < 3) {
            throw Exception("Inserisci almeno tre caratteri")
        }

        val form = FormBody.Builder().apply {
            if (!searchTags) {
                add("search", query)
            } else {
                tags.forEach { add("tag[]", it) }
            }
        }

        val searchPath = when {
            !searchTags -> "search"
            paths.size == 1 -> "tag/${paths[0]}/$page"
            else -> "search_tags"
        }
        return POST("$baseUrl/$searchPath", headers, form.build())
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val hasNextPage = document.selectFirst("div.next > a.gbutton:contains(»)") != null

        val articleElements = document.select("article.element")
        if (articleElements.isNotEmpty()) {
            return MangasPage(
                articleElements.map { element ->
                    SManga.create().apply {
                        setUrlWithoutDomain(element.selectFirst("a.thumb")!!.absUrl("href"))
                        title = element.selectFirst("div.title > a")!!.attr("title")
                        thumbnail_url = element.selectFirst("img.cover")?.absUrl("src")
                    }
                },
                hasNextPage,
            )
        }

        return MangasPage(
            document.select("div.group").map { element ->
                SManga.create().apply {
                    val titleAnchor = element.selectFirst("div.title > a")!!
                    setUrlWithoutDomain(titleAnchor.absUrl("href"))
                    title = titleAnchor.attr("title")
                    thumbnail_url = element.selectFirst("img.preview")?.absUrl("src")
                }
            },
            hasNextPage,
        )
    }

    // ── Manga Details ─────────────────────────────────────────────────────────
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val genres = mutableListOf<String>()
        val manga = SManga.create()

        document.select("div.meta-row").forEach { row ->
            when (row.selectFirst("div.meta-key")?.text()) {
                "Autore" -> manga.author = row.selectFirst("div.meta-val > a")?.text()
                "Genere", "Tipo" -> row.select("div.meta-val > a").forEach { genres.add(it.text()) }
            }
        }

        manga.description = document.selectFirst("div.desc-text")?.text()
        manga.genre = genres.joinToString(", ")
        manga.status = SManga.UNKNOWN
        manga.thumbnail_url = document.selectFirst("section.comic-hero img")?.absUrl("src")
        return manga
    }

    // ── Chapter List ──────────────────────────────────────────────────────────
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("article.chapter-card").map { element ->
            SChapter.create().apply {
                val anchor = element.selectFirst("div.chapter-card__title > a")!!
                setUrlWithoutDomain(anchor.absUrl("href"))
                name = anchor.text()
                date_upload = element.selectFirst("div.chapter-card__meta")?.ownText()
                    ?.substringAfterLast(", ")
                    ?.trim()
                    ?.let { parseChapterDate(it) } ?: 0L
            }
        }
    }

    private fun parseChapterDate(date: String): Long = when (date) {
        "Oggi" -> Calendar.getInstance().timeInMillis
        "Ieri" -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        else -> dateFormat.tryParse(date)
    }

    // ── Page List ─────────────────────────────────────────────────────────────
    override fun pageListParse(response: Response): List<Page> = response.use {
        val body = it.body.string()
        pagesUrlPattern.findAll(body).mapIndexed { index, match ->
            Page(index, imageUrl = match.groupValues[1].replace("\\/", "/"))
        }.toList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ── Filters ───────────────────────────────────────────────────────────────
    override fun getFilterList() = FilterList(
        TagList("Generi", getTagList()),
    )
}
