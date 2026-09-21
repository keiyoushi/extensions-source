package eu.kanade.tachiyomi.multisrc.initmanga

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

abstract class InitManga :
    KeiSource(),
    ConfigurableSource {

    protected val preferences: SharedPreferences by getPreferencesLazy()

    protected open val mangaUrlDirectory: String = "seri"

    protected open val chapterPagePathSegment: String = "bolum"

    protected open val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)

    protected open val popularUrlSlug: String = mangaUrlDirectory

    protected open val latestUrlSlug: String = "son-guncellemeler"

    protected open fun Element.imgAttr(): String? {
        fun getUrl(attr: String): String? = absUrl(attr).takeIf { it.isNotBlank() && !it.startsWith("data:") }
        return getUrl("data-original-src")
            ?: getUrl("data-src")
            ?: getUrl("data-lazy-src")
            ?: getUrl("data-cfsrc")
            ?: getUrl("data-original")
            ?: getUrl("src")
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val path = if (page == 1) "" else "page/$page/"
        val response = client.get("$baseUrl/$popularUrlSlug/$path")
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector().let { document.selectFirst(it) != null }
        return MangasPage(mangas, hasNextPage)
    }

    open fun popularMangaSelector() = "div.manga-card, " +
        "div.manga-item-grid > div.uk-panel.uk-position-relative, " +
        "div.manga-item-grid > div.uk-panel:not(.manga-item-ranking):not(.user-item-info), " +
        "div.uk-panel.uk-position-relative, " +
        "div.uk-panel:not(.manga-item-ranking):not(.user-item-info)"

    open fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("h2 a, h3 a, div.uk-overflow-hidden a, a.manga-card-title, div.manga-card-image-wrapper a")
            ?: element.selectFirst("a")

        title = element.selectFirst("h2 a, h3 a, div.manga-overlay-title, h2, h3")?.text()
            ?: element.selectFirst("a")?.ownText()?.takeIf { it.isNotBlank() }
            ?: element.selectFirst("a")?.text().orEmpty()
        setUrlWithoutDomain(linkElement!!.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.imgAttr()
    }

    open fun popularMangaNextPageSelector() = "head link[rel=next], link[rel=next], " +
        "ul.uk-pagination li:not(.uk-disabled) a[aria-label=\"Sonraki sayfa\"], " +
        "ul.uk-pagination li:not(.uk-disabled) a[aria-label=\"Next page\"], " +
        "ul.uk-pagination li:not(.uk-disabled) a:has([uk-pagination-next]), " +
        "ul.uk-pagination li#next-link:not(.uk-disabled) a, " +
        "a:contains(Sonraki sayfa), a:contains(Next page), a.next"

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val path = if (page == 1) "" else "page/$page/"
        val response = client.get("$baseUrl/$latestUrlSlug/$path")
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector().let { document.selectFirst(it) != null }
        return MangasPage(mangas, hasNextPage)
    }

    open fun latestUpdatesSelector() = popularMangaSelector()

    open fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    open fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val urlBuilder = "$baseUrl/wp-json/initlise/v1/search".toHttpUrl().newBuilder()
            urlBuilder.addQueryParameter("term", query)
            urlBuilder.addQueryParameter("page", page.toString())

            val response = client.get(urlBuilder.build())
            val peek = response.peekBody(1024).string().trimStart()
            if (peek.isEmpty()) throw IOException("Empty response body")

            if (peek.startsWith("<")) {
                val document = response.asJsoup()
                val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
                val hasNextPage = document.selectFirst("ul.uk-pagination li:not(#prev-link) a:not(:matchesOwn(\\S))[href^=http]") != null
                return MangasPage(mangas, hasNextPage)
            }

            val list = response.parseAs<List<Dto>>()
            val mangas = list.map { it.toSManga() }

            return MangasPage(mangas, false)
        }

        val genreFilter = filters.firstInstanceOrNull<GenreListFilter>()
        val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()

        val selectedGenres = genreFilter?.state?.filter { it.state }.orEmpty()
        val selectedGenre = selectedGenres.firstOrNull()

        if (selectedGenre != null && selectedGenre.url.startsWith("http")) {
            val genreUrl = selectedGenre.url

            val finalUrl = if (page > 1) {
                val cleanUrl = genreUrl.trimEnd('/')
                "$cleanUrl/page/$page/"
            } else {
                genreUrl
            }

            val response = client.get(finalUrl)
            val document = response.asJsoup()
            val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
            val hasNextPage = document.selectFirst("ul.uk-pagination li:not(#prev-link) a:not(:matchesOwn(\\S))[href^=http]") != null
            return MangasPage(mangas, hasNextPage)
        }

        val pagePath = if (page > 1) "page/$page/" else ""
        val urlBuilder = "$baseUrl/$mangaUrlDirectory/$pagePath".toHttpUrl().newBuilder()

        selectedGenres.forEach { genre ->
            urlBuilder.addQueryParameter("genre[]", genre.url)
        }
        typeFilter?.state?.let { if (it > 0 && it < typeValues.size) urlBuilder.addQueryParameter("type", typeValues[it]) }
        statusFilter?.state?.let { if (it > 0 && it < statusValues.size) urlBuilder.addQueryParameter("status", statusValues[it]) }
        sortFilter?.state?.let { if (it >= 0 && it < sortValues.size) urlBuilder.addQueryParameter("sort", sortValues[it]) }

        val response = client.get(urlBuilder.build())
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector().let { document.selectFirst(it) != null }
        return MangasPage(mangas, hasNextPage)
    }

    open fun searchMangaSelector() = popularMangaSelector()

    open fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    // ============================== Details & Chapters ====================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val pathSegments = url.pathSegments.filter { it.isNotEmpty() }
        if (pathSegments.size < 2) return null
        val dir = pathSegments[0]
        if (dir != mangaUrlDirectory && dir != "seri" && dir != "manga") return null
        val slug = pathSegments[1]
        val manga = SManga.create().apply {
            this.url = "/$mangaUrlDirectory/$slug/"
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga.apply {
            this.url = manga.url
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val updatedManga = parseMangaDetails(document).apply {
            url = manga.url
        }
        val updatedChapters = if (fetchChapters) {
            parseChapterList(document, getMangaUrl(manga).toHttpUrl())
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    protected open fun parseMangaDetails(document: Document) = SManga.create().apply {
        description = document.select("div#manga-description").clone()
            .apply {
                select("a, span").remove()
            }
            .text()

        val altName = document.selectFirst("span#comic-othername")?.text()
        if (!altName.isNullOrBlank()) {
            description += "\n\nAlternatif Başlık: $altName"
        }

        genre = document.select("div.uk-flex.uk-flex-nowrap.uk-flex-left.uk-grid-small.uk-grid span.uk-label-contest").joinToString { it.text().removePrefix("#").trim() }
        if (genre.isNullOrEmpty()) {
            genre = document.select("div#genre-tags a").joinToString { it.text() }
        }

        author = document.select("div.manga-info-details:contains(Yazar) a").text()
        if (author.isNullOrEmpty()) {
            author = document.select("div.manga-info-details:contains(Yazar)").text().substringAfter("Yazar:").substringBefore("Çizer:").trim()
        }

        artist = document.select("div.manga-info-details:contains(Çizer) a").text()
        if (artist.isNullOrEmpty()) {
            artist = document.select("div.manga-info-details:contains(Çizer)").text().substringAfter("Çizer:").substringBefore("Durum:").trim()
        }

        val statusText = (
            document.selectFirst("span#manga-status, div.manga-status-ribbons span.manga-status-ribbon__text")?.text()
                ?: document.select("div.manga-info-details:contains(Durum)").text().substringAfter("Durum:")
            ).lowercase()

        status = when {
            statusText.contains("güncel") || statusText.contains("devam") || statusText.contains("ongoing") -> SManga.ONGOING
            statusText.contains("tamamland") || statusText.contains("bitti") || statusText.contains("completed") || (statusText.contains("final") && !statusText.contains("sezon")) -> SManga.COMPLETED
            statusText.contains("ara ver") || statusText.contains("sezon") || statusText.contains("hiatus") -> SManga.ON_HIATUS
            statusText.contains("bırakıldı") || statusText.contains("iptal") || statusText.contains("dropped") || statusText.contains("cancel") -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        thumbnail_url = document.selectFirst("div.story-cover-wrap img, div.single-thumb img, a.story-cover img")?.imgAttr()

        val siteTitle = document.selectFirst("h1")?.text()
        val mangaTitle = document.selectFirst("h2.uk-h3")?.text()
        title = if (!siteTitle.isNullOrBlank()) siteTitle else mangaTitle!!
    }

    protected open suspend fun parseChapterList(initialDocument: Document, mangaUrl: HttpUrl): List<SChapter> {
        val hideLocked = preferences.getBoolean(PREF_HIDE_LOCKED_KEY, PREF_HIDE_LOCKED_DEFAULT)
        val chapters = mutableListOf<SChapter>()
        var document = initialDocument
        var page = 2

        do {
            val items = document.select(chapterListSelector())
            if (items.isEmpty()) break

            chapters.addAll(
                items.mapNotNull { element ->
                    if (hideLocked && isLocked(element)) return@mapNotNull null
                    chapterFromElement(element).takeUnless { hideLocked && it.name.startsWith("🔒") }
                },
            )

            val nextUrl = mangaUrl.newBuilder()
                .addPathSegment(chapterPagePathSegment)
                .addPathSegment("page")
                .addPathSegment(page.toString())
                .build()

            page++

            val response = client.get(nextUrl)
            if (!response.isSuccessful) break
            document = response.asJsoup()

            val hasNextPage = document.selectFirst("ul.uk-pagination a:not(:matchesOwn(\\S))[href^=http]") != null
        } while (hasNextPage)

        return chapters
    }

    open fun chapterListSelector() = "div.chapter-item"

    protected open fun isLocked(element: Element): Boolean = element.selectFirst("[uk-icon*=lock], span.uk-text-danger, span.chapter-lock, div.lock-card, i.fa-lock") != null ||
        element.html().contains("icon: lock")

    open fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))

        val rawName = element.select("h3").text()

        var parsedName = rawName.substringAfterLast("–").substringAfterLast("-").trim()
        if (parsedName.isBlank()) {
            parsedName = rawName
        }

        val isLocked = isLocked(element)
        name = if (isLocked && !parsedName.startsWith("🔒")) "🔒 $parsedName" else parsedName

        val dateStr = element.select("time").attr("datetime")
        date_upload = dateFormat.tryParseDateTime(dateStr)
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return pageListParse(document)
    }

    open fun pageListParse(document: Document): List<Page> {
        if (document.selectFirst("div#chapter-content div.lock-card") != null) {
            throw Exception("Kilitli bölüm, okumak için siteye giriş yapmanız gerekiyor")
        }

        val encryptedJson = document.select("script[src*=base64]").firstNotNullOfOrNull { script ->
            val src = script.attr("src")
            val b64 = src.substringAfter("base64,").substringBeforeLast("\"").trimEnd('\'', '"')
            runCatching {
                val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                ENCRYPTED_CHAPTER_REGEX.find(decoded)?.groupValues?.get(1)
                    ?: decoded.substringAfter("InitMangaEncryptedChapter=").substringBeforeLast(";").takeIf { it.isNotBlank() }
            }.getOrNull()
        } ?: AesDecrypt.REGEX_ENCRYPTED_DATA.find(document.html())?.groupValues?.get(1)
            ?: return fallbackPages(document)

        val payload = runCatching { encryptedJson.parseAs<EncryptedPayloadDto>() }.getOrNull()
            ?: return fallbackPages(document)

        val decryptedContent = AesDecrypt.decryptLayered(document, payload.ciphertext, payload.iv, payload.salt)
            ?: return fallbackPages(document)

        return parseDecryptedPages(decryptedContent)
    }

    private fun parseDecryptedPages(content: String): List<Page> {
        val trimmed = content.trim()

        return if (trimmed.startsWith("<")) {
            val doc = Jsoup.parseBodyFragment(trimmed, baseUrl)
            doc.select("img").mapIndexedNotNull { i, img ->
                val finalSrc = img.imgAttr() ?: return@mapIndexedNotNull null
                Page(i, imageUrl = finalSrc)
            }
        } else {
            runCatching {
                trimmed.parseAs<JsonArray>().mapIndexed { i, el ->
                    val src = el.jsonPrimitive.content
                    val finalSrc = when {
                        src.startsWith("//") -> "https:$src"

                        src.startsWith("/") -> baseUrl.toHttpUrlOrNull()?.resolve(src)?.toString()
                            ?: (baseUrl.trimEnd('/') + src)

                        else -> src
                    }
                    Page(i, imageUrl = finalSrc)
                }
            }.getOrElse { emptyList() }
        }
    }

    private fun fallbackPages(document: Document): List<Page> = document.select("div#chapter-content img, div.chapter-content img, div.reader-area img, div.entry-content img, div#readerarea img").mapIndexedNotNull { i, img ->
        val src = img.imgAttr() ?: return@mapIndexedNotNull null
        Page(i, imageUrl = src)
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/$mangaUrlDirectory").asJsoup()
        return parseGenres(document).toJsonElement()
    }

    protected open fun parseGenres(document: Document): List<GenreData> = document.selectFirst("ul.uk-list.uk-text-small, div#uk-tab-3, form.sidebar-manga-filter select[name='genre[]']")
        ?.select("li a, a, option")
        ?.mapNotNull { element ->
            val name = element.text().trim()
            val url = element.absUrl("href").ifEmpty { element.attr("value") }.trim()
            if (url.isBlank() || name.startsWith("Türleri", ignoreCase = true) || name.equals("Tüm", ignoreCase = true)) {
                null
            } else {
                GenreData(name = name, url = url)
            }
        }
        .orEmpty()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()

        val genres = data?.parseAs<List<GenreData>>().orEmpty()
        if (genres.isNotEmpty()) {
            filters.add(
                GenreListFilter("Kategoriler", genres.map { Genre(it.name, it.url) }),
            )
        }

        if (typeFilterOptions.isNotEmpty()) filters.add(TypeFilter())
        if (statusFilterOptions.isNotEmpty()) filters.add(StatusFilter())
        if (sortFilterOptions.isNotEmpty()) filters.add(SortFilter())

        return FilterList(filters)
    }

    // ============================== Preferences ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED_KEY
            title = "Hide locked chapters"
            summary = "Hide chapters that require coins to read"
            setDefaultValue(PREF_HIDE_LOCKED_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_HIDE_LOCKED_KEY = "pref_hide_locked_chapters"
        private const val PREF_HIDE_LOCKED_DEFAULT = false
        private val ENCRYPTED_CHAPTER_REGEX = Regex("""InitMangaEncryptedChapter\s*=\s*(\{.*?\})""", RegexOption.DOT_MATCHES_ALL)
    }
}
