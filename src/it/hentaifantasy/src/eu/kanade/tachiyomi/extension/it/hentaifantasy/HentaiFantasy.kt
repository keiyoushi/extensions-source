package eu.kanade.tachiyomi.extension.it.hentaifantasy

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Source
abstract class HentaiFantasy : KeiSource() {

    companion object {
        private val pagesUrlPattern = Regex(""""url":"(.*?)"""")
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy.M.d", Locale.ROOT)
    }

    // ── Popular ──────────────────────────────────────────────────────────────
    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseUrl/most_downloaded/$page/").asJsoup().parseMangaList()

    private fun Document.parseMangaList(): MangasPage {
        val mangas = select("article.element").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.thumb")!!.absUrl("href"))
                title = element.selectFirst("div.title > a")!!.attr("title")
                thumbnail_url = element.selectFirst("img.cover")?.absUrl("src")
            }
        }
        val hasNextPage = selectFirst("div.next > a.gbutton:contains(»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ── Latest ───────────────────────────────────────────────────────────────
    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get("$baseUrl/latest/$page/").asJsoup().parseMangaList()

    // ── Search ───────────────────────────────────────────────────────────────
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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
        val document = client.post("$baseUrl/$searchPath", form.build()).asJsoup()

        val articleElements = document.select("article.element")
        if (articleElements.isNotEmpty()) {
            return document.parseMangaList()
        }

        val hasNextPage = document.selectFirst("div.next > a.gbutton:contains(»)") != null
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
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(document, manga), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document, oldManga: SManga): SManga {
        val genres = mutableListOf<String>()
        val manga = SManga.create()
        manga.url = oldManga.url
        manga.title = oldManga.title

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
    private fun parseChapterList(document: Document): List<SChapter> = document.select("article.chapter-card").map { element ->
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

    private fun parseChapterDate(date: String): Long = when (date) {
        "Oggi" -> Calendar.getInstance().timeInMillis
        "Ieri" -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        else -> dateFormat.tryParseDate(date)
    }

    // ── Page List ─────────────────────────────────────────────────────────────
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).use {
        val body = it.body.string()
        pagesUrlPattern.findAll(body).mapIndexed { index, match ->
            Page(index, imageUrl = match.groupValues[1].replace("\\/", "/"))
        }.toList()
    }

    // ── Filters ───────────────────────────────────────────────────────────────
    override fun getFilterList(data: JsonElement?) = FilterList(
        TagList("Generi", getTagList()),
    )
}
