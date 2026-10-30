package eu.kanade.tachiyomi.extension.es.tmohentaiapp

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class TMOHentaiApp :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds) { it.host == baseUrl.toHttpUrl().host }

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun getFilterList(data: JsonElement?): FilterList = buildTmohFilterList()

    // --- Popular and latest ---

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(
            if (page == 1) "$baseUrl/" else "$baseUrl/biblioteca?order_item=likes_count&order_dir=desc&page=$page",
        ).asJsoup()

        return parseMangaPage(document, isHomepage = page == 1)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/biblioteca?order_item=creation_date&order_dir=desc&page=$page").asJsoup()

        return parseMangaPage(document, isHomepage = false)
    }

    private fun parseMangaPage(document: Document, isHomepage: Boolean): MangasPage {
        val homepageCards: List<Element> = if (isHomepage) {
            document.select(
                "#top_weekly .manga-card, #top_monthly .manga-card, #top_today .manga-card, " +
                    ".tab-pane#top_weekly .manga-card-col a, .tab-pane#top_monthly .manga-card-col a, .tab-pane#top_today .manga-card-col a",
            )
        } else {
            emptyList()
        }

        val cards: List<Element> = if (homepageCards.isNotEmpty()) {
            homepageCards
        } else {
            document.select(".manga-card, a.manga-card, .manga-card-col a").ifEmpty {
                document.select("div.element, div.thumbnail, .card, div.col-6, div.col-md-3, div.col-lg-2, div.col-xl-2, .content-item, div.book-thumbnail")
            }
        }

        val mangas = cards.mapNotNull { element ->
            val link: Element? = if (element.tagName() == "a") {
                element
            } else {
                element.selectFirst("a.manga-card, a[href*=/library/], a[href*=/contents/]") ?: element.selectFirst("a")
            }
            val href = link?.attr("href")?.trim() ?: return@mapNotNull null

            if (href.isBlank() || href == "#" || href == "/" || href.endsWith("/library") || href.endsWith("/biblioteca") || href.contains("/login")) {
                return@mapNotNull null
            }

            val title = link.selectFirst(".manga-card__title, h4.text-truncate, h3.text-truncate, h4, h3, h5, .thumbnail-title, .title, a.title, .name")?.text()?.trim()
                ?: link.attr("title").trim().ifEmpty { link.text().trim() }.orEmpty()

            val cover = element.selectFirst("img.manga-card__cover, img.manga-card__cover-wrap img")?.attr("src")?.trim()
                ?: element.selectFirst("img")?.let { img ->
                    img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }.ifEmpty { img.attr("data-original") }.ifEmpty { img.attr("src") }
                }?.trim()
                ?: ""

            val author = element.selectFirst(".manga-card__meta-author span, .book-author")?.text()?.trim() ?: ""

            SManga.create().apply {
                this.title = title
                setUrlWithoutDomain(href)
                thumbnail_url = cover
                artist = author
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }

        val hasNextPage = if (isHomepage) {
            mangas.isNotEmpty()
        } else {
            document.selectFirst("a[rel=next]") != null
        }

        return MangasPage(mangas.distinctBy { it.url }, hasNextPage)
    }

    // --- Search and filters ---

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val group = filters.filterIsInstance<GroupFilter>().firstOrNull()?.state?.trim()
        if (query.isBlank() && group != null && group.isNotBlank()) {
            val groupDocument = client.get("$baseUrl/groups".toHttpUrl().newBuilder().addQueryParameter("name", group).build()).asJsoup()
            val href = groupDocument.selectFirst("a[href*=/groups/]")?.attr("abs:href")?.trim().orEmpty()
            if (href.isBlank()) {
                return MangasPage(emptyList(), false)
            }
            return parseMangaPage(client.get(href).asJsoup(), isHomepage = false)
        }

        val url = "$baseUrl/biblioteca".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            val title = when {
                query.isNotBlank() -> query.trim()
                else -> filters.filterIsInstance<TitleFilter>().firstOrNull()?.state?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: filters.filterIsInstance<ArtistFilter>().firstOrNull()?.state?.trim()
                        ?.takeIf { it.isNotBlank() }
            }
            if (title != null) {
                addQueryParameter("title", title)
            }

            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        val (item, dir) = filter.toUriPart()
                        addQueryParameter("order_item", item)
                        addQueryParameter("order_dir", dir)
                    }
                    is ContentFilter -> {
                        val value = filter.toUriPart()
                        if (value.isNotBlank()) {
                            addQueryParameter("content", value)
                        }
                    }
                    is TagFilter -> {
                        filter.state.forEach { tag ->
                            when (tag.state) {
                                Filter.TriState.STATE_INCLUDE -> addQueryParameter("tags[]", tag.value)
                                Filter.TriState.STATE_EXCLUDE -> addQueryParameter("exclude_tags[]", tag.value)
                            }
                        }
                    }
                    else -> {
                    }
                }
            }
        }.build()

        val document = client.get(url).asJsoup()

        return parseMangaPage(document, isHomepage = false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }
        val id = url.pathSegments.getOrNull(1) ?: return null

        val document = client.get(url).asJsoup()
        val manga = mangaDetailsParse(document).apply { this.url = "/contents/$id" }

        return manga
    }

    // --- Details and chapters ---

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        val updatedManga = mangaDetailsParse(document).apply { url = manga.url }
        val chapterList = if (fetchChapters) {
            listOf(createChapter(manga.url, document))
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun createChapter(mangaUrl: String, document: Document): SChapter {
        val readerButton = document.selectFirst(
            "a.md-preview-read-btn, a[href*=/view_uploads/], a[href*=/reader/], a[href*=/viewer/], a[href*=/view/], a[title*=lector]",
        )

        val chapterUrl = readerButton?.attr("href")?.takeIf { it.isNotBlank() && it != "#" }
            ?: readerButton?.attr("data-url")?.takeIf { it.isNotBlank() }
            ?: mangaUrl
        val finalChapterUrl = resolveReaderUrl(chapterUrl)

        val chapterName = document.selectFirst("#md-title, .element-title, h1")?.text()?.ifEmpty { "Capítulo Completo" } ?: "Capítulo Completo"

        return SChapter.create().apply {
            name = chapterName.trim()
            setUrlWithoutDomain(finalChapterUrl)
        }
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("#md-title, h1.element-title, h1, .entry-title, .title")?.text()?.trim() ?: ""

        val synopsis = document.selectFirst(".md-info-row--synopsis .md-info-row__value, p.element-description, .element-description, .description, .synopsis, #description, p.text-justify")?.text()?.trim() ?: ""
        val typeValue = document.selectFirst(".md-badge--type")?.text()?.trim()
            ?: document.selectFirst("#md-type-banner, .content-type")?.let { banner ->
                val classes = banner.attr("class").split(Regex("\\s+")).filter { it.isNotBlank() }
                val idx = classes.indexOf("content-type")
                if (idx in 0 until classes.lastIndex) classes[idx + 1] else null
            }
        val pagesValue = document.selectFirst("#md-preview-grid")?.attr("data-total")?.trim()
        val meta = listOfNotNull(
            typeValue?.takeIf { it.isNotBlank() }?.let { "Type: $it" },
            pagesValue?.takeIf { it.isNotBlank() }?.let { "Pages: $it" },
        )
        description = when {
            meta.isNotEmpty() && synopsis.isNotBlank() -> meta.joinToString("\n") + "\n\n$synopsis"
            meta.isNotEmpty() -> meta.joinToString("\n")
            else -> synopsis
        }

        author = document.selectFirst(".md-badge--author")?.text()?.trim()?.removePrefix("\uF007")?.trim()
            ?: document.select("a[href*=/author/], a[href*=/authors/], a[href*=/artist/], a[href*=/uploader/], a[href*=/user/], a[href*=/scan/]")
                .map { it.text().trim() }
                .firstOrNull { it.isNotBlank() && !it.contains("AUTOR", true) }
            ?: ""

        artist = document.selectFirst(".md-badge--uploader")?.text()?.trim()?.removePrefix("\uF0E4")?.trim()
            ?: document.select("a[href*=/uploader/], a[href*=/user/], a[href*=/scan/], a[href*=/author/], a[href*=/authors/], a[href*=/artist/]")
                .map { it.text().trim() }
                .firstOrNull { it.isNotBlank() && !it.contains("UPLOADED BY", true) }
            ?: ""

        val tagElements = document.select(
            "#md-tags-list .label-info, a[href*=/genre/], a[href*=/tag/], a[href*=/genero/], a[href*=/etiqueta/], " +
                "a[href*=/gender/], a[href*=/tags/], a[href*=/genders/], a[href*=/genres/], .badge",
        )
        val ignoredTags = listOf("en emisión", "finalizado", "pausado", "cancelado", "volver", "leer")

        genre = tagElements.map { it.text().trim() }
            .filter { tag -> tag.isNotBlank() && tag.length in 2..35 && !ignoredTags.any { tag.lowercase() == it } }
            .distinct()
            .joinToString(", ")

        thumbnail_url = document.selectFirst("#md-cover, .md-cover-card__image-wrap img")?.attr("src")?.trim()
            ?: document.selectFirst(".element-image div[style*=background-image], .cover div[style*=background-image]")?.attr("style")
                ?.let { style ->
                    style.substringAfter("url('", "").substringBefore("')", "").takeIf { it.isNotBlank() }
                        ?: style.substringAfter("url(\"", "").substringBefore("\")", "").takeIf { it.isNotBlank() }
                }
            ?: document.select(".cover img, img.entry-cover, img.thumbnail, .element-image img").first()?.let { img ->
                img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }.ifEmpty { img.attr("src") }
            }

        val statusText = document.selectFirst(".md-cover-card__status .md-badge, .md-badge--ongoing, .md-badge--completed, .md-badge--hiatus, .md-badge--cancelled")?.text()?.trim()?.lowercase() ?: ""
        status = when {
            statusText.contains("ongoing") || statusText.contains("en emisión") -> SManga.ONGOING
            statusText.contains("completed") || statusText.contains("finalizado") -> SManga.COMPLETED
            statusText.contains("hiatus") || statusText.contains("pausado") -> SManga.ON_HIATUS
            statusText.contains("cancelled") || statusText.contains("cancelado") -> SManga.CANCELLED
            else -> SManga.COMPLETED
        }

        initialized = true
    }

    // --- Pages ---

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val document = response.asJsoup()
        var targetUrl = response.request.url.toString()

        if (!targetUrl.contains("/view_uploads/") && !targetUrl.contains("/reader/") && !targetUrl.contains("/viewer/") && !targetUrl.contains("/view/")) {
            val readerButton = document.selectFirst(
                "a.md-preview-read-btn, a[href*=/view_uploads/], a[href*=/reader/], a[href*=/viewer/], a[href*=/view/], a[title*=lector]",
            )
            val viewerHref = readerButton?.attr("href")?.takeIf { it.isNotBlank() && it != "#" }
                ?: readerButton?.attr("data-url")?.takeIf { it.isNotBlank() }

            if (viewerHref != null) {
                targetUrl = if (viewerHref.startsWith("http")) viewerHref else "$baseUrl$viewerHref"
            }
        }

        val finalUrl = resolveReaderUrl(targetUrl)
        val pageDocument = if (finalUrl == response.request.url.toString()) {
            document
        } else {
            client.get(finalUrl).asJsoup()
        }

        return parsePages(pageDocument, finalUrl)
    }

    private fun parsePages(document: Document, finalUrl: String): List<Page> {
        val allImages = mutableListOf<String>()

        val imageElements = document.select(
            ".reader-img-wrap img, #viewer-container img, .viewer-container img, .img-container img, #main-container img.viewer-img, .viewer-page img, center img",
        )

        for (element in imageElements) {
            val src = element.attr("data-src").ifEmpty { element.attr("abs:src") }.ifEmpty { element.attr("data-original") }.ifEmpty { element.attr("data-lazy-src") }.ifEmpty { element.attr("src") }
            val clean = when {
                src.startsWith("//") -> "https:$src"
                src.startsWith("/") -> "$baseUrl$src"
                else -> src
            }
            if (isValidMangaPage(clean)) {
                allImages.add(clean)
            }
        }

        if (allImages.isEmpty()) {
            val html = document.html()
            val regex = Regex("""https?://[^\s"'<>]+?\.(?:jpg|jpeg|png|webp|avif)(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
            regex.findAll(html).forEach { match ->
                val clean = match.value
                if (isValidMangaPage(clean)) {
                    allImages.add(clean)
                }
            }
        }

        if (allImages.isEmpty()) {
            document.select("img").forEach { element ->
                val src = element.attr("abs:data-src").ifEmpty { element.attr("abs:src") }
                if (src.isNotBlank() && isValidMangaPage(src)) {
                    allImages.add(src)
                }
            }
        }

        if (allImages.isEmpty()) {
            throw Exception("No se encontraron páginas en este capítulo (URL usada: $finalUrl)")
        }

        return allImages.distinct().mapIndexed { index, url -> Page(index, finalUrl, url) }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
            .build()
        return Request.Builder()
            .url(page.imageUrl!!)
            .headers(imageHeaders)
            .get()
            .build()
    }

    private fun resolveReaderUrl(url: String): String {
        val pageMethod = preferences.getString(PAGE_METHOD_PREF, PAGE_METHOD_PREF_DEFAULT_VALUE)
        return if (url.contains("/paginated") && pageMethod != PAGE_METHOD_PAGINATED_VALUE) {
            url.replace("/paginated", "/cascade")
        } else if (url.contains("/paginated")) {
            url
        } else if (url.contains("/view_uploads/") || url.endsWith("/cascade")) {
            url
        } else if (url.contains("/reader/") || url.contains("/viewer/") || url.contains("/view/")) {
            url.trimEnd('/') + "/cascade"
        } else {
            url
        }
    }

    private fun isValidMangaPage(url: String): Boolean {
        if (url.isBlank() || !url.startsWith("http")) {
            return false
        }
        val lower = url.lowercase()
        val badKeywords = listOf(
            "avatar", "profile", "logo", "banner", "icon", "placeholder",
            "default", "user", "navbar", "ad.", "ads.", "analytics",
            ".svg", "favicon", "button",
        )
        return badKeywords.none { lower.contains(it) }
    }

    // --- Preferences ---

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val pageMethodPreference = ListPreference(screen.context).apply {
            key = PAGE_METHOD_PREF
            title = PAGE_METHOD_PREF_TITLE
            entries = arrayOf("Cascada", "Páginado")
            entryValues = arrayOf("cascade", "paginated")
            summary = PAGE_METHOD_PREF_SUMMARY
            setDefaultValue(PAGE_METHOD_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(PAGE_METHOD_PREF, newValue as String).apply()
                true
            }
        }
        screen.addPreference(pageMethodPreference)
    }

    companion object {
        private const val PAGE_METHOD_PREF = "pageMethodPref"
        private const val PAGE_METHOD_PREF_TITLE = "Método de descarga de imágenes"
        private const val PAGE_METHOD_PREF_SUMMARY = "Puede corregir errores al cargar las imágenes.\nConfiguración actual: %s"
        private const val PAGE_METHOD_PREF_DEFAULT_VALUE = "cascade"
        private const val PAGE_METHOD_PAGINATED_VALUE = "paginated"
    }
}
