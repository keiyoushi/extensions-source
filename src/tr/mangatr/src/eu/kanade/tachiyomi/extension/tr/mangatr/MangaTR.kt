package eu.kanade.tachiyomi.extension.tr.mangatr

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale

class MangaTR : FMReader("Manga-TR", "https://manga-tr.com", "tr") {
    override fun headersBuilder() = super.headersBuilder()
        .add("Accept-Language", "en-US,en;q=0.5")

    override val client by lazy {
        super.client.newBuilder()
            .addInterceptor(DDoSGuardInterceptor(super.client))
            .rateLimit(2)
            .build()
    }

    override val requestPath = "manga-list-sayfala.html"

    // Popular

    override fun popularMangaNextPageSelector() = "div.pagination-wrap a.pagination-link"

    override fun popularMangaSelector() = "div.lp-results div.media-card"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleLink = element.selectFirst("a.media-card__title")!!
        setUrlWithoutDomain(titleLink.absUrl("href"))
        title = titleLink.text().trim()
        thumbnail_url = element.selectFirst("img.media-card__cover")?.absUrl("src")
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

    private var cachedGenres: List<Genre> = emptyList()

    override fun getFilterList(): FilterList {
        val baseFilters = mutableListOf<Filter<*>>(
            SortFilter(),
            SortDirectionFilter(),
            PublicationStatusFilter(),
            TranslateStatusFilter(),
            AgeRestrictionFilter(),
            ContentTypeFilter(),
            SpecialTypeFilter(),
        )
        if (cachedGenres.isNotEmpty()) {
            baseFilters += GenreList(cachedGenres)
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

        val sortParam = when (filters.firstInstanceOrNull<SortFilter>()?.state ?: 0) {
            1 -> "views"
            2 -> "name"
            else -> "last_update"
        }
        val sortTypeParam = when (filters.firstInstanceOrNull<SortDirectionFilter>()?.state ?: 0) {
            1 -> "ASC"
            else -> "DESC"
        }
        val url = "$baseUrl/$requestPath".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sortParam)
            .addQueryParameter("sort_type", sortTypeParam)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("listType", "pagination")

        // PHP keeps the last `tur` in the query string; `tur` is shared with genre + special-type filters.
        val genreFilter = filters.firstInstanceOrNull<GenreList>()
        val includedGenres = genreFilter?.state?.filter { it.isIncluded() }.orEmpty()
        val specialTur = filters.firstInstanceOrNull<SpecialTypeFilter>()?.let { f ->
            arrayOf("", "2")[f.state].takeIf { it.isNotEmpty() }
        }
        when {
            includedGenres.isNotEmpty() -> {
                includedGenres.forEach { url.addQueryParameter("tur", it.id) }
            }
            specialTur != null -> {
                url.addQueryParameter("tur", specialTur)
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

    /** Detail page appends (YYYY) to the title; strip it to match list browse titles. */
    private val trailingYearInTitle = Regex("""\s*\((?:19|20)\d{2}\)\s*$""")

    override fun getMangaUrl(manga: SManga): String = captchaUrl?.also { captchaUrl = null } ?: super.getMangaUrl(manga)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
                .replace(trailingYearInTitle, "")
                .trim()

            thumbnail_url = document.selectFirst("img[src*='image.mangatr.site'], img.poster-card__image")?.absUrl("src")
                ?: document.selectFirst("img[title]")?.absUrl("src")

            val descBlock = document.selectFirst("div.detail-copy")?.text()?.trim().orEmpty()
            val altNames = document.selectFirst("div.detail-hero__sub")?.text()?.trim().orEmpty()
            description = buildString {
                if (descBlock.isNotEmpty()) append(descBlock)
                if (altNames.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative Names: ")
                    append(altNames)
                }
            }.ifBlank { null }

            author = document.detailMetaLinks("Yazar", "author")
                .joinToString { it.text().trim() }
                .takeUnless { it.isBlank() }

            artist = document.detailMetaLinks("Sanatçı", "artist")
                .joinToString { it.text().trim() }
                .takeUnless { it.isBlank() }

            genre = document.detailMetaLinks("Tür", "tur=").joinToString { it.text() }

            val translateLabel = document.detailMetaLink("Çeviri", "ceviri").orEmpty()
            status = parseTranslateStatusBadge(translateLabel)
        }
    }

    private fun Document.detailMetaLinks(labelSubstring: String, hrefSubstring: String): List<Element> {
        val row = selectFirst("span.detail-meta-row__label:contains($labelSubstring)")?.closest(".detail-meta-row")
            ?: return emptyList()
        return row.select(".detail-meta-row__value a[href*='$hrefSubstring']")
    }

    private fun Document.detailMetaLink(labelSubstring: String, hrefSubstring: String): String? {
        val row = selectFirst("span.detail-meta-row__label:contains($labelSubstring)")?.closest(".detail-meta-row")
            ?: return null
        return row.selectFirst(".detail-meta-row__value a[href*='$hrefSubstring']")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun parseTranslateStatusBadge(label: String): Int {
        if (label.isEmpty()) return SManga.UNKNOWN
        val t = label.lowercase(Locale.ROOT)
        return when {
            t.contains("tamamlan") -> SManga.COMPLETED
            t.contains("bırak") || t.contains("birak") -> SManga.CANCELLED
            t.contains("askı") || t.contains("askida") -> SManga.ON_HIATUS
            t.contains("devam") -> SManga.ONGOING
            t.contains("olmayan") -> SManga.UNKNOWN
            else -> SManga.UNKNOWN
        }
    }

    // Chapters — API HTML: `cek/fetch_pages_manga.php` returns `article.chapter-card` (not `div.chapter-item`).

    override fun chapterListSelector() = "article.chapter-card"

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
        val link = element.selectFirst("a.chapter-card__title")!!
        setUrlWithoutDomain(link.attr("href"))
        val chapterNum = link.selectFirst("span:last-child")?.text()?.trim()
            ?: link.text().trim()
        val sub = element.selectFirst("p.chapter-card__subtitle")?.text()?.trim()
        name = when {
            sub.isNullOrEmpty() -> chapterNum
            sub.contains("Bölüm") -> sub
            else -> "$chapterNum: $sub"
        }
        date_upload = parseRelativeDate(
            element.selectFirst("div.chapter-card__meta span")?.text()?.trim() ?: "",
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        if (document.selectFirst("canvas#sliderCanvas, div.box h2:contains(Güvenlik Doğrulaması)") != null) {
            captchaUrl = document.location()
            throw IOException("Lütfen WebView'da Bot Korumasını geçin.")
        }

        val chapterPages = document.select("div.chapter-page")
        if (chapterPages.isNotEmpty()) {
            val pages = mutableListOf<Page>()
            val sortedChapterPages = chapterPages
                .filter { it.hasAttr("data-parts") && it.hasAttr("data-order") }
                .sortedBy { it.attr("data-page-index").toIntOrNull() ?: Int.MAX_VALUE }

            for (page in sortedChapterPages) {
                val partsJson = page.attr("data-parts")
                val orderAttr = page.attr("data-order")

                val urls: List<String> = try {
                    partsJson.parseAs<JsonArray>().map { it.jsonPrimitive.content }
                } catch (e: Exception) {
                    continue
                }

                if (urls.isEmpty()) continue

                // chapter.js: part index -> vertical canvas order (matches site reader).
                val mapping = decodePartOrderMapping(orderAttr)
                if (mapping.isNullOrEmpty()) {
                    pages.add(Page(pages.size, imageUrl = urls.first()))
                    continue
                }

                val sortedUrls = mapping
                    .sortedBy { it.second }
                    .mapNotNull { (partIdx, _) -> urls.getOrNull(partIdx) }
                if (sortedUrls.isEmpty()) {
                    pages.add(Page(pages.size, imageUrl = urls.first()))
                    continue
                }

                for (url in sortedUrls) {
                    pages.add(Page(pages.size, imageUrl = url))
                }
            }
            return pages
        }

        val directImages = document.select("img[src*='img_part.php'], img[data-src*='img_part.php']")
        if (directImages.isNotEmpty()) {
            return directImages.mapIndexed { index, img ->
                val src = img.attr("src").ifEmpty { img.attr("data-src") }
                Page(index, imageUrl = src)
            }
        }

        val html = document.html()
        val imgUrlRegex = Regex("""https?://[^"'\s]*img_part\.php[^"'\s]*""")
        val seenKeys = mutableSetOf<String>()
        val imgUrls = imgUrlRegex.findAll(html)
            .map { it.value.replace("&amp;", "&") }
            .filterNot { it.contains("logo") }
            .filter { url ->
                val keyMatch = Regex("""key=([^&]+)""").find(url)
                val key = keyMatch?.groupValues?.get(1) ?: return@filter false
                if (seenKeys.contains(key)) {
                    false
                } else {
                    seenKeys.add(key)
                    true
                }
            }
            .toList()

        if (imgUrls.isNotEmpty()) {
            return imgUrls.mapIndexed { idx, url -> Page(idx, imageUrl = url) }
        }

        return emptyList()
    }

    /** Decodes `data-order`: Base64, XOR 0x5A, then JSON mapping part index to vertical order. */
    private fun decodePartOrderMapping(encoded: String): List<Pair<Int, Int>>? {
        return try {
            val raw = Base64.decode(encoded, Base64.DEFAULT)
            val decoded = raw.map { (it.toInt() and 0xFF) xor 0x5A }
            val jsonStr = String(decoded.map { it.toByte() }.toByteArray(), StandardCharsets.UTF_8)
            when (val element = jsonStr.parseAs<JsonElement>()) {
                is JsonArray -> {
                    element.mapIndexedNotNull { idx, el ->
                        val pos = el.jsonPrimitive.content.toIntOrNull() ?: return@mapIndexedNotNull null
                        idx to pos
                    }
                }
                is JsonObject -> {
                    element.mapNotNull { (k, v) ->
                        val partIdx = k.toIntOrNull() ?: return@mapNotNull null
                        val pos = v.jsonPrimitive.content.toIntOrNull() ?: return@mapNotNull null
                        partIdx to pos
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Genres from `select[name=tur]` on the list page (cached after first parse). */
    private fun cacheGenresFromListPage(document: Document) {
        if (cachedGenres.isNotEmpty()) return
        val parsed = document.select("select[name=tur] option[value]").mapNotNull { opt ->
            val value = opt.attr("value").ifBlank { return@mapNotNull null }
            Genre(opt.text().trim(), value)
        }
        if (parsed.isNotEmpty()) {
            cachedGenres = parsed
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        cacheGenresFromListPage(document)

        val mangas = document.select(popularMangaSelector())
            .filterNot { card ->
                val badge = card.selectFirst(".media-card__badge")?.text()?.lowercase(Locale.ROOT).orEmpty()
                badge.contains("novel") || badge.contains("anime")
            }
            .map { popularMangaFromElement(it) }

        // Last page: "›" still points at the same page number (compare to current).
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val nextPageFromArrow = document.select(popularMangaNextPageSelector())
            .firstOrNull { it.text().trim() == "›" }
            ?.attr("href")
            ?.let { href -> Regex("""[?&]page=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull() }
        val hasNextPage = nextPageFromArrow != null && nextPageFromArrow > currentPage

        return MangasPage(mangas, hasNextPage)
    }

    private class SortFilter :
        Filter.Select<String>(
            "Sıralama",
            arrayOf("Ada göre", "Popülerlik", "Son güncelleme"),
        )

    private class SortDirectionFilter :
        Filter.Select<String>(
            "Sıralama yönü",
            arrayOf("Alfabetik sıra", "Tersten başa"),
        )

    private class PublicationStatusFilter :
        Filter.Select<String>(
            "Yayın Durumu",
            arrayOf("Tümü", "Tamamlandı", "Devam Ediyor"),
        )

    private class TranslateStatusFilter :
        Filter.Select<String>(
            "Çeviri Durumu",
            arrayOf("Tümü", "Tamamlanan", "Devam Eden", "Bırakılan", "Olmayan"),
        )

    private class AgeRestrictionFilter :
        Filter.Select<String>(
            "Yaş Sınırlaması",
            arrayOf("Tümü", "+16", "+18"),
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
