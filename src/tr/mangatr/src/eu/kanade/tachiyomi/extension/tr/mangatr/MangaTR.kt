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
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class MangaTR : FMReader("Manga-TR", "https://manga-tr.com", "tr") {
    override fun headersBuilder() = super.headersBuilder()
        .add("Accept-Language", "en-US,en;q=0.5")

    override val client by lazy {
        super.client.newBuilder()
            .addInterceptor(DDoSGuardInterceptor(super.client))
            .build()
    }

    override val requestPath = "manga-list-sayfala.html"

    // Popular

    override fun popularMangaNextPageSelector() = "div.btn-group:not(div.btn-block) button.btn-info"

    override fun popularMangaSelector() = "div.col-md-12 > span.thumbnail"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a.pull-left")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = element.selectFirst("h3.media-heading a")!!.text()
        thumbnail_url = link.selectFirst("img.media-object")?.absUrl("src")
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/$requestPath".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "views")
            .addQueryParameter("sort_type", "DESC")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("listType", "pagination")
            .build()
        return GET(url, headers)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/$requestPath".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "last_update")
            .addQueryParameter("sort_type", "DESC")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("listType", "pagination")
            .build()
        return GET(url, headers)
    }

    // Search

    private var captchaUrl: String? = null
    private var cachedGenres: List<FMReader.Genre> = emptyList()
    private val srcUrlRegex = Regex(""""src"\s*:\s*"(https?://[^"]+)"""")
    private val k2Regex = Regex("""\bk2\b\s*:\s*"([^"]+)"""")
    private val chunkRegex = Regex(""""([^"]+)"""")

    override fun getFilterList(): FilterList {
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

        val genreFilter = filters.firstInstanceOrNull<FMReader.GenreList>()
        if (genreFilter != null && genreFilter.state.isNotEmpty()) {
            val included = genreFilter.state.filter { it.isIncluded() }
            if (included.isNotEmpty()) {
                included.forEach { url.addQueryParameter("genre[]", it.id) }
            }
        }

        filters.firstInstanceOrNull<PublicationStatusFilter>()?.let { f ->
            val value = arrayOf("", "1", "2")[f.state]
            if (value.isNotEmpty()) url.addQueryParameter("durum", value)
        }
        filters.firstInstanceOrNull<TranslateStatusFilter>()?.let { f ->
            val value = arrayOf("", "1", "2", "3", "4")[f.state]
            if (value.isNotEmpty()) url.addQueryParameter("ceviri", value)
        }
        filters.firstInstanceOrNull<AgeRestrictionFilter>()?.let { f ->
            val value = arrayOf("", "16", "18")[f.state]
            if (value.isNotEmpty()) url.addQueryParameter("yas", value)
        }
        filters.firstInstanceOrNull<ContentTypeFilter>()?.let { f ->
            val value = arrayOf("", "1", "3", "5")[f.state]
            if (value.isNotEmpty()) url.addQueryParameter("icerik", value)
        }
        filters.firstInstanceOrNull<SpecialTypeFilter>()?.let { f ->
            val value = arrayOf("", "2")[f.state]
            if (value.isNotEmpty()) url.addQueryParameter("tur", value)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val path = response.request.url.encodedPath
        return if (path.contains("/arama.html")) {
            val mangas = response.asJsoup()
                .select("div.row a[data-toggle]")
                .filterNot {
                    it.parent()?.selectFirst("a.anime-r, a.novel-r") != null
                }
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

    // Details

    override fun getMangaUrl(manga: SManga): String = captchaUrl?.also { captchaUrl = null } ?: super.getMangaUrl(manga)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()

        thumbnail_url = document.selectFirst("img[src*='image.mangatr.site']")?.absUrl("src")
            ?: document.selectFirst("img[title]")?.absUrl("src")

        description = document.selectFirst("div.info-desc, div#tab1 p, div.summary")
            ?.text()

        author = document.selectFirst("div.manga-meta-item:contains(Yazar) a[href*='?author='], div.manga-meta-value a[href*='?author=']")?.text()

        artist = document.select("div.manga-meta-item:contains(Sanatçı) a[href*='?artist='], div.manga-meta-value a[href*='?artist=']")
            .joinToString { it.text() }
            .ifBlank { null }

        genre = document.select("div.manga-meta-item:contains(Tür) a[href*='?tur='], div.manga-meta-value a[href*='?tur=']")
            .joinToString { it.text() }

        val durumHref = document.selectFirst("a[href*='?durum=']")?.attr("href") ?: ""
        status = when {
            durumHref.contains("durum=2") -> SManga.ONGOING
            durumHref.contains("durum=1") -> SManga.COMPLETED
            document.body().text().contains("Devam Ediyor") -> SManga.ONGOING
            document.body().text().contains("Tamamlandı") -> SManga.COMPLETED
            document.body().text().contains("Askıda") -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // Chapters

    override fun chapterListSelector() = "div.chapter-item"

    override val chapterUrlSelector = "div.chapter-title a"

    override val chapterTimeSelector = "div.stats"

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

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val link = element.selectFirst("div.chapter-title a")!!
        setUrlWithoutDomain(link.attr("href"))
        val chapterNum = link.selectFirst("span:last-child")?.text()
            ?: link.text()
        val sub = element.selectFirst("div.chapter-sub")?.text()
        name = when {
            sub.isNullOrEmpty() -> chapterNum
            sub.contains("Bölüm") -> sub // zaten bölüm numarası içeriyor
            else -> "$chapterNum: $sub" // sadece başlık, numarayı başa ekle
        }
        date_upload = parseRelativeDate(element.selectFirst("div.stats")?.ownText() ?: "")
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url}"

    override val pageListImageSelector = "div#chapter-images img.chapter-img"

    override fun getImgAttr(element: Element?): String? = null

    // Pages
    // Şifreleme: key = k1 + k2, xorDecrypt = atob(chunks.join('')) XOR key

    override fun pageListParse(document: Document): List<Page> {
        if (document.selectFirst("canvas#sliderCanvas, div.box h2:contains(Güvenlik Doğrulaması)") != null) {
            captchaUrl = document.location()
            throw Exception("Lütfen WebView'da Bot Korumasını geçin.")
        }

        for (script in document.select("script:not([src])")) {
            val js = script.html()
            if (!js.contains("imageQueueData")) continue

            // --- Yeni Format: Doğrudan src URL'leri (sig parametreli) ---
            val srcUrls = srcUrlRegex.findAll(js)
                .map { it.groupValues[1] }
                .filterNot { it.contains("logo") }
                .toList()
            if (srcUrls.isNotEmpty()) {
                return srcUrls.mapIndexed { idx, url -> Page(idx, imageUrl = url) }
            }

            // --- Eski Format: k1 + k2 XOR şifreli ---
            val k1 = document.selectFirst("div#chapter-images")?.attr("data-k1") ?: continue
            val k2 = k2Regex.find(js)?.groupValues?.get(1) ?: continue

            // queue array'ini al — "queue" ile "logo" arasındaki her şey
            val queueStart = js.indexOf("queue")
            val logoIndex = js.indexOf("logo", queueStart)
            if (queueStart < 0 || logoIndex < 0) continue
            val queueSection = js.substring(queueStart, logoIndex)

            val key = k1 + k2
            val pages = mutableListOf<Page>()
            var idx = 0

            // Her [ ... ] bloğunu bul
            var pos = 0
            while (pos < queueSection.length) {
                val open = queueSection.indexOf('[', pos)
                if (open < 0) break
                val close = queueSection.indexOf(']', open)
                if (close < 0) break
                pos = close + 1

                val block = queueSection.substring(open + 1, close)
                // Chunk string'lerini topla
                val chunks = chunkRegex.findAll(block)
                    .map { it.groupValues[1] }
                    .toList()
                if (chunks.isEmpty()) continue

                val combined = chunks.joinToString("")
                if (combined.contains("FAKE")) continue

                val url = xorDecrypt(combined, key) ?: continue
                pages.add(Page(idx++, imageUrl = url))
            }

            if (pages.isNotEmpty()) return pages
        }

        return emptyList()
    }

    @Suppress("SwallowedException")
    private fun xorDecrypt(base64Str: String, key: String): String? {
        if (key.isEmpty()) return null
        return try {
            val pad = (4 - base64Str.length % 4) % 4
            val bytes = Base64.decode(base64Str + "=".repeat(pad), Base64.DEFAULT)
            val result = buildString {
                bytes.forEachIndexed { i, b ->
                    append(((b.toInt() and 0xFF) xor key[i % key.length].code).toChar())
                }
            }
            if (result.startsWith("http")) result else null
        } catch (e: Exception) {
            null
        }
    }

    // List Parse

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        if (cachedGenres.isEmpty()) {
            val options = document.select("#genreSelect option")
            if (options.isNotEmpty()) {
                cachedGenres = options.mapNotNull { opt ->
                    val value = opt.attr("value")
                    val text = opt.text()
                    if (text.isEmpty() || value.isEmpty()) null else FMReader.Genre(text, value)
                }
            } else {
                cachedGenres = document.select("a[href*='?tur=']").mapNotNull { a ->
                    val tur = a.attr("href").substringAfter("?tur=", "").substringBefore("&")
                    val text = a.text()
                    if (text.isEmpty() || tur.isEmpty()) null else FMReader.Genre(text, tur)
                }.distinctBy { it.id }
            }
        }

        val mangas = document.select(popularMangaSelector())
            .filterNot {
                it.selectFirst("a.anime-r, a.novel-r") != null
            }
            .map { popularMangaFromElement(it) }

        val hasNextPage = (document.select(popularMangaNextPageSelector()).first()?.text() ?: "").let {
            if (it.contains(Regex("""\w*\s\d*\s\w*\s\d*"""))) {
                it.split(" ").let { p -> p[1] != p[3] }
            } else {
                it.isNotEmpty()
            }
        }
        return MangasPage(mangas, hasNextPage)
    }

    // Filters

    private class PublicationStatusFilter :
        Filter.Select<String>(
            "Yayın Durumu",
            arrayOf("Tümü", "Tamamlandı", "Devam Ediyor"),
        )

    private class TranslateStatusFilter :
        Filter.Select<String>(
            "Çeviri Durumu",
            arrayOf("Tümü", "Devam Ediyor", "Tamamlandı", "Bırakılmış", "Yok"),
        )

    private class AgeRestrictionFilter :
        Filter.Select<String>(
            "Yaş Sınırlaması",
            arrayOf("Tümü", "16+", "18+"),
        )

    private class ContentTypeFilter :
        Filter.Select<String>(
            "İçerik Türü",
            arrayOf("Tümü", "Manga", "Webtoon", "Çizgi Roman"),
        )

    private class SpecialTypeFilter :
        Filter.Select<String>(
            "Özel Tür",
            arrayOf("Tümü", "Yetişkin"),
        )
}
