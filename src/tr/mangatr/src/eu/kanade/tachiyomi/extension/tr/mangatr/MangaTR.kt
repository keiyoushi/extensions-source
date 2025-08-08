package eu.kanade.tachiyomi.extension.tr.mangatr

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.nio.charset.StandardCharsets

class MangaTR : FMReader("Manga-TR", "https://manga-tr.com", "tr") {
    override fun headersBuilder() = super.headersBuilder()
        .add("Accept-Language", "en-US,en;q=0.5")

    override val client by lazy {
        super.client.newBuilder()
            .addInterceptor(DDoSGuardInterceptor(super.client))
            .build()
    }

    override val requestPath = "manga-list-sayfala.html"

    // ============================== Popular ===============================
    override fun popularMangaNextPageSelector() = "div.btn-group:not(div.btn-block) button.btn-info"

    override fun popularMangaSelector() = "div.row a[data-toggle]"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.text()
    }

    // =============================== Search ===============================
    // Dynamic genre list cache
    private var cachedGenres: List<FMReader.Genre> = emptyList()

    // Filters UI: site-specific filters + dynamically fetched genres
    override fun getFilterList(): FilterList {
        ensureGenresLoaded()
        val baseFilters = mutableListOf<Filter<*>>(
            Filter.Header("Metin araması ile filtreler birlikte kullanılmaz"),
            PublicationStatusFilter(),
            TranslateStatusFilter(),
            AgeRestrictionFilter(),
            ContentTypeFilter(),
            SpecialTypeFilter(),
        )

        if (cachedGenres.isNotEmpty()) {
            baseFilters += FMReader.GenreList(cachedGenres)
        } else {
            baseFilters += Filter.Header("Türleri yüklemek için 'Sıfırla' düğmesine basın")
            baseFilters += FMReader.GenreList(emptyList())
        }

        return FilterList(baseFilters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/arama.html".toHttpUrl().newBuilder()
                .addQueryParameter("icerik", query)
                .build()
            return GET(url, headers)
        }

        val listEndpoint = if (page <= 1) "$baseUrl/manga-list.html" else "$baseUrl/$requestPath"
        val url = listEndpoint.toHttpUrl().newBuilder()
        if (page > 1) {
            url.addQueryParameter("listType", "pagination")
            url.addQueryParameter("page", page.toString())
        }

        val genreFilter = (if (filters.isEmpty()) getFilterList() else filters)
            .filterIsInstance<FMReader.GenreList>()
            .firstOrNull()

        if (genreFilter != null && genreFilter.state.isNotEmpty()) {
            val included = genreFilter.state.filter { it.isIncluded() }
            if (included.isNotEmpty()) {
                included.forEach { url.addQueryParameter("genre[]", it.id) }
            }
        }

        // Diğer filtreler
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        filterList.filterIsInstance<PublicationStatusFilter>().firstOrNull()?.let { f ->
            val value = arrayOf("", "1", "2")[f.state]
            if (value.isNotEmpty()) url.addQueryParameter("durum", value)
        }

        filterList.filterIsInstance<TranslateStatusFilter>().firstOrNull()?.let { f ->
            val value = arrayOf("", "1", "2", "3", "4")[f.state]
            if (value.isNotEmpty()) url.addQueryParameter("ceviri", value)
        }

        filterList.filterIsInstance<AgeRestrictionFilter>().firstOrNull()?.let { f ->
            val value = arrayOf("", "16", "18")[f.state]
            if (value.isNotEmpty()) url.addQueryParameter("yas", value)
        }

        filterList.filterIsInstance<ContentTypeFilter>().firstOrNull()?.let { f ->
            val value = arrayOf("", "1", "2", "3", "4")[f.state]
            if (value.isNotEmpty()) url.addQueryParameter("icerik", value)
        }

        filterList.filterIsInstance<SpecialTypeFilter>().firstOrNull()?.let { f ->
            val value = arrayOf("", "2")[f.state]
            if (value.isNotEmpty()) url.addQueryParameter("tur", value)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val path = response.request.url.encodedPath
        return if (path.contains("/arama.html")) {
            val mangas = response.use { it.asJsoup() }
                .select("div.row a[data-toggle]")
                .filterNot { it.siblingElements().text().contains("Novel") }
                .map(::searchMangaFromElement)
            MangasPage(mangas, false)
        } else {
            super.searchMangaParse(response)
        }
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.text()
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val infoElement = document.selectFirst("div#tab1")!!
        infoElement.selectFirst("table + table tr + tr")?.run {
            author = selectFirst("td:nth-child(1) a")?.text()
            artist = selectFirst("td:nth-child(2) a")?.text()
            genre = selectFirst("td:nth-child(3)")?.text()
        }
        description = infoElement.selectFirst("div.well")?.ownText()?.trim()
        thumbnail_url = document.selectFirst("img.thumbnail")?.absUrl("src")

        status = infoElement.selectFirst("tr:contains(Çeviri Durumu) + tr > td:nth-child(2)")
            .let { parseStatus(it?.text()) }
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "tr.table-bordered"

    override val chapterUrlSelector = "td[align=left] > a"

    override val chapterTimeSelector = "td[align=right]"

    private val chapterListHeaders by lazy {
        headersBuilder().add("X-Requested-With", "XMLHttpRequest").build()
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val id = manga.url.substringAfter("manga-").substringBefore(".")
        val requestUrl = "$baseUrl/cek/fetch_pages_manga.php?manga_cek=$id"
        return client.newCall(GET(requestUrl, chapterListHeaders))
            .asObservableSuccess()
            .map(::chapterListParse)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        // chapters are paginated
        val chapters = buildList {
            val requestUrl = response.request.url.toString()
            var nextPage = 2
            do {
                val doc = when {
                    isEmpty() -> response
                    else -> {
                        val body = FormBody.Builder()
                            .add("page", nextPage.toString())
                            .build()
                        nextPage++
                        client.newCall(POST(requestUrl, chapterListHeaders, body)).execute()
                    }
                }.use { it.asJsoup() }

                addAll(doc.select(chapterListSelector()).map(::chapterFromElement))
            } while (doc.selectFirst("a[data-page=$nextPage]") != null)
        }
        return chapters
    }

    override fun pageListRequest(chapter: SChapter) =
        GET("$baseUrl/${chapter.url.substringAfter("cek/")}", headers)

    override val pageListImageSelector = "div.chapter-content img.chapter-img"

    // Manga-TR: image URL resolution with Base64-decoded data-src
    override fun getImgAttr(element: Element?): String? {
        return when {
            element == null -> null
            element.hasAttr("data-src") -> {
                try {
                    val encodedUrl = element.attr("data-src")
                    val decodedBytes = Base64.decode(encodedUrl, Base64.DEFAULT)
                    String(decodedBytes, StandardCharsets.UTF_8)
                } catch (e: Exception) {
                    element.attr("abs:src")
                }
            }
            element.hasAttr("src") -> element.attr("abs:src")
            element.hasAttr("data-original") -> element.attr("abs:data-original")
            else -> null
        }
    }

    // Simple pageListParse - relies on the selector above
    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListImageSelector).mapIndexed { i, img ->
            Page(i, document.location(), getImgAttr(img))
        }
    }

    // =========================== List Parse ===========================
    override fun popularMangaParse(response: Response): MangasPage {
        ensureGenresLoaded()
        return super.popularMangaParse(response)
    }

    private fun ensureGenresLoaded() {
        if (cachedGenres.isNotEmpty()) return
        runCatching {
            val doc = client.newCall(GET("$baseUrl/manga-list.html", headers)).execute().asJsoup()

            val options = doc.select("#genreSelect option")
            if (options.isNotEmpty()) {
                cachedGenres = options.mapNotNull { opt ->
                    val value = opt.attr("value").trim()
                    val text = opt.text().trim()
                    if (text.isEmpty() || value.isEmpty()) null else FMReader.Genre(text, value)
                }
                return
            }

            val container = doc.selectFirst("*:matchesOwn(Tür Seçiniz)")?.parent()
                ?: doc.selectFirst("div:has(:matchesOwn(Tür Seçiniz))")
            val anchors = when {
                container != null -> container.select("a")
                else -> doc.select("a[href*=manga-list], a[href*=genre], a[href*=tur]")
            }
            val items = anchors.map { it.text().trim() }.filter { it.length > 1 }.distinct()
            if (items.isNotEmpty()) {
                cachedGenres = items.map { name -> FMReader.Genre(name, name.replace(' ', '+')) }
            }
        }.onFailure { }
    }

    // =========================== Filters (UI) ===========================
    private class PublicationStatusFilter : Filter.Select<String>(
        "Yayın Durumu",
        arrayOf("Tümü", "Tamamlandı", "Devam Ediyor"),
    )

    private class TranslateStatusFilter : Filter.Select<String>(
        "Çeviri Durumu",
        arrayOf("Tümü", "Devam Ediyor", "Tamamlandı", "Bırakılmış", "Yok"),
    )

    private class AgeRestrictionFilter : Filter.Select<String>(
        "Yaş Sınırlaması",
        arrayOf("Tümü", "16+", "18+"),
    )

    private class ContentTypeFilter : Filter.Select<String>(
        "İçerik Türü",
        arrayOf("Tümü", "Manga", "Novel", "Webtoon", "Anime"),
    )

    private class SpecialTypeFilter : Filter.Select<String>(
        "Özel Tür",
        arrayOf("Tümü", "Yetişkin"),
    )
}
