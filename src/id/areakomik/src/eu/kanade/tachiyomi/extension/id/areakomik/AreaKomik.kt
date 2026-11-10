package eu.kanade.tachiyomi.extension.id.areakomik

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class AreaKomik : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(pageUrl(page, "komik-terpopuler").build())

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(pageUrl(page).build(), LATEST_SELECTOR)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected

        if (query.isNotBlank()) {
            // /page/N/ search pages drop the genre and repeat entries, only ?paged=N pages through the same result set
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addQueryParameter("s", query)
                genre?.let { addQueryParameter("genre", it) }
                if (page > 1) addQueryParameter("paged", page.toString())
            }.build()
            return getMangaList(url)
        }

        if (genre != null) {
            return getMangaList(pageUrl(page, "genre", genre).build())
        }

        val url = pageUrl(page).apply {
            filters.firstInstanceOrNull<StatusFilter>()?.selected?.let { addQueryParameter("status", it) }
        }.build()
        return getMangaList(url, LATEST_SELECTOR)
    }

    private fun pageUrl(page: Int, vararg segments: String): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder().apply {
        segments.forEach(::addPathSegment)
        if (page > 1) addPathSegment("page").addPathSegment(page.toString())
        addPathSegment("")
    }

    private suspend fun getMangaList(url: HttpUrl, selector: String = CARD_SELECTOR): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(selector).map { element ->
            SManga.create().apply {
                val link = element.selectFirst("h3.card-title a")!!
                title = link.text()
                setUrlWithoutDomain(link.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a.next.page-numbers") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null

        val mangaPath = when (url.pathSegments[0]) {
            "series" -> "/series/$slug/"
            "chapter" -> client.get(url, Fetch.DOCUMENT).asJsoup()
                .selectFirst(".breadcrumb a[href*=/series/]")
                ?.absUrl("href")
                ?.toHttpUrl()
                ?.encodedPath
                ?: return null
            else -> return null
        }

        val manga = SManga.create().apply { this.url = mangaPath }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(document).apply { url = manga.url },
            chapters = parseChapters(document),
        )
    }

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".series-thumb > img")?.absUrl("src")
        author = document.metaValue("Author")
        genre = buildList {
            document.select(".genre-list a.genre-tag").mapTo(this) { it.text() }
            document.metaValue("Type")?.let(::add)
        }.joinToString()
        status = when (document.metaValue("Status")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = buildString {
            // the site wraps every synopsis in the same generated intro and outro paragraphs
            document.select("#lin-sinopsis p")
                .map { it.text() }
                .filterNot { SYNOPSIS_INTRO in it || it == SYNOPSIS_OUTRO }
                .joinTo(this, "\n\n")
            document.metaValue("Alternatif")?.let {
                if (isNotEmpty()) append("\n\n")
                append("Judul alternatif: ", it)
            }
        }
    }

    private fun Document.metaValue(label: String): String? = selectFirst(".meta-item:has(> strong:containsOwn($label))")
        ?.text()
        ?.substringAfter(":")
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it != "-" }

    private fun parseChapters(document: Document): List<SChapter> = document.select("#chapter-grid-container .chapter-row[data-num]").map { row ->
        SChapter.create().apply {
            setUrlWithoutDomain(row.selectFirst("a.chapter-link")!!.absUrl("href"))
            name = buildString {
                append(row.selectFirst(".chap-num")!!.ownText())
                row.selectFirst(".chap-title")?.textOrNull()?.let { append(" - ", it) }
            }
            date_upload = row.attr("data-date").toLongOrNull()?.times(1000) ?: 0L
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".chapter-content img.chapter-img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$baseUrl/wp-json/wp/v2/genre".toHttpUrl().newBuilder()
            .addQueryParameter("per_page", "100")
            .addQueryParameter("orderby", "count")
            .addQueryParameter("order", "desc")
            .addQueryParameter("_fields", "name,slug")
            .build()
        return client.get(url, Fetch.XHR)
            .parseAs<List<GenreDto>>()
            .sortedBy { it.name.lowercase() }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(Filter.Header("Status diabaikan saat cari judul/genre"))
            add(StatusFilter())
            data?.parseAs<List<GenreDto>>()?.let { add(GenreFilter(it)) }
        },
    )

    companion object {
        private const val CARD_SELECTOR = "article.komik-card:not(.native-ad-slot)"
        private const val LATEST_SELECTOR = "#lin-update-terbaru ~ .komik-grid $CARD_SELECTOR"
        private const val SYNOPSIS_INTRO = "adalah komik yang bisa kamu baca online"
        private const val SYNOPSIS_OUTRO = "Pilih chapter dari daftar di bawah untuk mulai membaca."
    }
}
