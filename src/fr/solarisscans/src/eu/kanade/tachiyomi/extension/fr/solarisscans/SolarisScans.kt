package eu.kanade.tachiyomi.extension.fr.solarisscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class SolarisScans :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val hidePremium: Boolean
        get() = preferences.getBoolean(PREF_HIDE_PREMIUM, true)

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_PREMIUM
            title = "Masquer les chapitres premium"
            summary = "Masquer les chapitres verrouillés en accès anticipé payant"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = catalog(page, query = "", sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = catalog(page, query = "", sort = "recent")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "popular"
        val statuses = filters.firstInstanceOrNull<StatusFilter>()?.toUriParts().orEmpty()
        val origins = filters.firstInstanceOrNull<TypeFilter>()?.toUriParts().orEmpty()
        return catalog(page, query.trim(), sort, statuses, origins)
    }

    private suspend fun catalog(
        page: Int,
        query: String,
        sort: String,
        statuses: List<String> = emptyList(),
        origins: List<String> = emptyList(),
    ): MangasPage {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("catalog_q", query)
            }
            addQueryParameter("catalog_sort", sort)
            statuses.forEach { addQueryParameter("catalog_status[]", it) }
            origins.forEach { addQueryParameter("catalog_origin[]", it) }
            if (page > 1) {
                addQueryParameter("catalog_page", page.toString())
            }
        }.build()

        val document = client.get(url).asJsoup()

        val mangas = document.select("article.solaris-catalog-card").mapNotNull { element ->
            val titleElement = element.selectFirst("h2 a") ?: return@mapNotNull null
            val title = titleElement.text().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val href = titleElement.absUrl("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SManga.create().apply {
                this.title = title
                setUrlWithoutDomain(href)
                thumbnail_url = imageUrl(element.selectFirst(".solaris-catalog-card__cover img"))
            }
        }

        val nextPage = (page + 1).toString()
        val hasNextPage = document.select("a[href]").any {
            it.attr("abs:href").toHttpUrlOrNull()?.queryParameter("catalog_page") == nextPage
        }
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }
        if (url.pathSegments.getOrNull(0)?.lowercase() != "manga") {
            return null
        }
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val mangaUrl = "/manga/$slug/"
        val manga = SManga.create().apply { this.url = mangaUrl }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { this.url = mangaUrl }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val updatedManga = if (fetchDetails) parseDetails(document, manga.url) else manga
        // Extra chapter pages live behind ?chapters_page=N, so only fetch them when asked.
        val updatedChapters = if (fetchChapters) {
            require(document.selectFirst("#swChapterGrid") != null) { "Chapter list not found." }
            fetchAllChapters(document).ifEmpty { chapters }
        } else {
            chapters
        }
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun parseDetails(document: Document, preserveUrl: String): SManga = SManga.create().apply {
        setUrlWithoutDomain(document.location())
        if (url.isBlank()) {
            setUrlWithoutDomain(preserveUrl)
        }
        title = document.selectFirst("section.sz-ri-info-card h1")?.text()
            ?: error("Manga title not found: ${document.location()}")
        thumbnail_url = imageUrl(document.selectFirst(".sz-ri-cover img"))
        description = document.selectFirst("[data-hero-synopsis-full]")?.textOrNull()
            ?: document.selectFirst("[data-hero-synopsis-short]")?.textOrNull()

        val byline = document.select(".sz-ri-byline strong")
        author = byline.getOrNull(0)?.textOrNull()
        artist = byline.getOrNull(1)?.textOrNull()

        val sideValue = { label: String ->
            document.select(".sz-ri-side-info div").firstOrNull {
                it.selectFirst("span")?.text()?.equals(label, ignoreCase = true) == true
            }?.selectFirst("strong")?.textOrNull()
        }
        status = sideValue("Statut").toStatus()
        genre = buildList {
            addAll(document.select(".sz-ri-main-genres span").eachText())
            sideValue("Type")?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinctBy { it.lowercase() }.joinToString().ifBlank { null }
    }

    private fun String?.toStatus(): Int {
        val value = this?.lowercase().orEmpty()
        return when {
            "en cours" in value -> SManga.ONGOING
            "termin" in value || "achev" in value -> SManga.COMPLETED
            "pause" in value -> SManga.ON_HIATUS
            "abandon" in value || "annul" in value -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private suspend fun fetchAllChapters(firstPage: Document): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val seenUrls = LinkedHashSet<String>()
        fun accumulate(entries: List<Pair<SChapter, Boolean>>): Boolean {
            val fresh = entries.filterNot { (chapter, _) -> chapter.url in seenUrls }
            fresh.forEach { (chapter, isPremium) ->
                seenUrls += chapter.url
                if (!isPremium || !hidePremium) {
                    chapters += chapter
                }
            }
            return fresh.isNotEmpty()
        }

        accumulate(parseChapters(firstPage))
        val pageMeta = firstPage.selectFirst(".sz-ri-chapter-page-meta")?.text().orEmpty()
        val metaPages = CHAPTER_PAGE_COUNT_REGEX.find(pageMeta)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val linkPages = firstPage.select("a[href*=chapters_page]").mapNotNull {
            it.attr("abs:href").toHttpUrlOrNull()?.queryParameter("chapters_page")?.toIntOrNull()
        }.maxOrNull() ?: 1
        val totalPages = maxOf(metaPages, linkPages)
        val base = firstPage.location().toHttpUrlOrNull() ?: baseUrl.toHttpUrl()
        for (page in 2..totalPages.coerceAtMost(MAX_CHAPTER_PAGES)) {
            val url = base.newBuilder().setQueryParameter("chapters_page", page.toString()).build()
            val document = try {
                client.get(url).asJsoup()
            } catch (e: HttpException) {
                if (e.code == 404) {
                    break
                }
                throw e
            }
            if (!accumulate(parseChapters(document))) {
                break
            }
        }
        return chapters
    }

    private fun parseChapters(document: Document): List<Pair<SChapter, Boolean>> {
        return document.select("#swChapterGrid a.sz-chapter-card").mapNotNull { element ->
            val href = element.absUrl("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val number = element.attr("data-number")
            val baseName = element.selectFirst(".sz-ri-chapter-body strong")?.textOrNull()
                ?: element.selectFirst("strong")?.textOrNull()
                ?: number.takeIf { it.isNotBlank() }?.let { "Chapitre $it" }
                ?: return@mapNotNull null
            val isPremium = element.hasClass("is-premium") ||
                element.selectFirst(".sz-ri-chapter-premium, .sz-ri-chapter-access") != null
            val chapter = SChapter.create().apply {
                setUrlWithoutDomain(href)
                name = buildString {
                    if (isPremium) {
                        append("🔒 ")
                    }
                    append(baseName)
                }
                number.toFloatOrNull()?.let { chapter_number = it }
                date_upload = element.attr("data-recent").toLongOrNull()?.times(1000L) ?: 0L
            }
            chapter to isPremium
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl).asJsoup()

        val script = document.selectFirst("script#chapter_preloaded_images")?.data().orEmpty()
        val imageUrls = PRELOADED_IMAGE_REGEX.findAll(script).mapNotNull { match ->
            match.groupValues.getOrNull(1)
                ?.replace("\\/", "/")
                ?.replace("&amp;", "&")
                ?.takeIf { it.isNotBlank() }
                ?.let { src ->
                    when {
                        src.startsWith("http") -> src
                        src.startsWith("//") -> "https:$src"
                        src.startsWith("/") -> baseUrl + src
                        else -> "$baseUrl/$src"
                    }.replaceFirst("http://", "https://")
                }
        }.toList()

        if (imageUrls.isNotEmpty()) {
            return imageUrls.mapIndexed { index, imageUrl ->
                Page(index, url = chapterUrl, imageUrl = imageUrl)
            }
        }

        val fallback = document.select(".reading-content img.wp-manga-chapter-img").mapNotNull { img ->
            imageUrl(img)?.takeIf { it.startsWith("http") }?.replaceFirst("http://", "https://")
        }

        if (fallback.isNotEmpty()) {
            return fallback.mapIndexed { index, imageUrl ->
                Page(index, url = chapterUrl, imageUrl = imageUrl)
            }
        }

        return emptyList()
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Referer", page.url)
        .build()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(SortFilter(), StatusFilter(), TypeFilter())

    private fun imageUrl(element: Element?): String? {
        if (element == null) {
            return null
        }
        return element.absUrl("data-src")
            .ifBlank { element.absUrl("data-lazy-src") }
            .ifBlank { element.absUrl("data-cfsrc") }
            .ifBlank { element.absUrl("data-manga-src") }
            .ifBlank { element.absUrl("src") }
            .takeIf { it.isNotBlank() }
    }

    companion object {
        private const val PREF_HIDE_PREMIUM = "pref_hide_premium"
        private const val MAX_CHAPTER_PAGES = 500
        private val CHAPTER_PAGE_COUNT_REGEX = Regex("Page\\s+\\d+\\s+sur\\s+(\\d+)", RegexOption.IGNORE_CASE)
        private val PRELOADED_IMAGE_REGEX = Regex("\"([^\"]*solaris_chapter_image[^\"]*)\"")
    }
}
