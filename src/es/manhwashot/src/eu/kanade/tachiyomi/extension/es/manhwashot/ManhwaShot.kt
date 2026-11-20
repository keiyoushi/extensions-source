package eu.kanade.tachiyomi.extension.es.manhwashot

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class ManhwaShot :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/ranking/", Fetch.DOCUMENT).asJsoup()
        // The page holds the week, month and all time rankings, in this order
        val mangas = document.select("div[role=tabpanel]").last()!!.select("a.trx-row").map { row ->
            SManga.create().apply {
                setUrlWithoutDomain(row.absUrl("href"))
                title = row.selectFirst(".trx-row-name")!!.text()
                thumbnail_url = row.selectFirst("img")?.absUrl("src")?.toFullSizeCover()
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int) = getMangaList("$baseUrl/ultimas-actualizaciones/".toHttpUrl(), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val type = filters.firstInstanceOrNull<TypeFilter>()?.selected
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.checked
        val url = "$baseUrl/explorar/".toHttpUrl().newBuilder().apply {
            type?.let(::addPathSegment)
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            genres?.let { addQueryParameter("genero", it.joinToString(",")) }
            status?.let { addQueryParameter("estado", it) }
            // Without a type, text or filter, the catalogue only shows part of the series
            if (type == null && query.isBlank() && genres == null && status == null) addQueryParameter("ver", "todos")
        }.build()
        return getMangaList(url, page)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!url.isSite()) return MangasPage(emptyList(), false)
        return when (url.pathSegments[0]) {
            "genero" -> getMangaList(url, page)
            // The novel tab lists text novels
            "explorar" -> if ("novela" in url.pathSegments) MangasPage(emptyList(), false) else getMangaList(url, page)
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.getOrNull(1)
        if (!url.isSite() || url.pathSegments[0] != "manga" || slug.isNullOrEmpty()) return null
        val manga = SManga.create().apply { this.url = "/manga/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        if (!fetchChapters) return SMangaUpdate(document.toSManga(manga.url), chapters)
        return SMangaUpdate(document.toSManga(manga.url), document.toChapters())
    }

    private fun Document.toSManga(path: String) = SManga.create().apply {
        url = path
        title = selectFirst("h1.series-title")!!.text()
        thumbnail_url = selectFirst(".series-cover img")?.absUrl("src")?.toFullSizeCover()
        genre = select(".series-genres a.genre-tag").joinToString { it.text() }.ifEmpty { null }
        status = when (selectFirst(".series-badges a[href*=estado=]")?.absUrl("href")?.toHttpUrl()?.queryParameter("estado")) {
            "on-going" -> SManga.ONGOING
            "end" -> SManga.COMPLETED
            "on-hold" -> SManga.ON_HIATUS
            "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        val votes = selectFirst(".rate-meta")?.ownText()?.filter(Char::isDigit)?.toIntOrNull() ?: 0
        description = MarkdownDescription.Builder()
            .rating(selectFirst(".rate-meta b")?.text()?.toDoubleOrNull()?.takeIf { votes > 0 }, max = 5, votes = votes)
            // The other paragraphs are the same text for every series
            .description(selectFirst(".series-desc:not([style])"))
            .altTitles(selectFirst(".series-alt-titles")?.ownText()?.split(" · ").orEmpty(), "Títulos alternativos")
            .build()
    }

    private fun Document.toChapters(): List<SChapter> {
        val rows = select(".chapters-grid a.ch-row")
        if (rows.isEmpty()) return emptyList()
        // The page shows relative dates only: the chapter list's data has the exact ones, and the lock levels
        val data = extractNextJs<ChapterListDto>()!!
        val showLocked = preferences.getBoolean(PREF_SHOW_LOCKED, false)
        return rows.mapNotNull { row ->
            val href = row.absUrl("href")
            val number = href.trimEnd('/').substringAfterLast("capitulo-")
            val level = data.levelOf(number)?.takeIf { it in LOCK_LEVELS }
            if (level != null && !showLocked) return@mapNotNull null
            val title = row.selectFirst(".ch-num")!!.ownText()
            SChapter.create().apply {
                setUrlWithoutDomain(href)
                name = when (level) {
                    null -> title
                    "vip" -> "🔒 $title (VIP)"
                    "pago" -> "🔒 $title (VIP de pago)"
                    else -> "🔒 $title"
                }
                chapter_number = number.toFloatOrNull() ?: -1f
                date_upload = data.dateOf(number)
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val reader = client.get(getChapterUrl(chapter), rscHeaders, Fetch.XHR).extractNextJs<ReaderDto>()!!
        if (reader.locked) return emptyList()
        return reader.toPages()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = "Mostrar capítulos bloqueados"
            summary = "Muestra con 🔒 los capítulos para usuarios registrados o VIP. Se abren tras iniciar sesión en WebView con una cuenta que pueda leerlos."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/explorar/", Fetch.DOCUMENT).asJsoup()
        .select("a.exp-genre[href*=genero=]")
        .map { GenreDto(it.text(), it.absUrl("href").toHttpUrl().queryParameter("genero")!!) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            TypeFilter(),
            StatusFilter(),
            data?.let { GenreFilter(it.parseAs()) },
        ),
    )

    private suspend fun getMangaList(url: HttpUrl, page: Int): MangasPage {
        val pageUrl = baseUrl.toHttpUrl().newBuilder().apply {
            url.pathSegments.takeWhile { it != "page" }.filter { it.isNotEmpty() }.forEach(::addPathSegment)
            if (page > 1) addPathSegments("page/$page")
            addPathSegment("")
            encodedQuery(url.encodedQuery)
        }.build()
        val document = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".series-grid .s-card").map { card ->
            SManga.create().apply {
                // A card is either the link itself or holds it in its title
                setUrlWithoutDomain((card.selectFirst("a.s-card-title") ?: card).absUrl("href"))
                title = card.selectFirst(".s-card-title")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")?.toFullSizeCover()
            }
        }
        return MangasPage(mangas, document.selectFirst(".pager a.pager-btn:contains(Siguiente)") != null)
    }

    private val rscHeaders get() = headersBuilder().set("RSC", "1").build()

    private fun HttpUrl.isSite() = host.removePrefix("www.") == baseUrl.toHttpUrl().host

    // Lists ask for resized covers: the full size in webp, as on the series page (some originals are huge animated GIFs)
    private fun String.toFullSizeCover() = toHttpUrl().newBuilder()
        .removeAllQueryParameters("w")
        .setQueryParameter("f", "webp")
        .build()
        .toString()

    private companion object {
        const val PREF_SHOW_LOCKED = "pref_show_locked"

        // Lock levels the site's chapter list shows a badge for
        val LOCK_LEVELS = setOf("login", "vip", "pago")
    }
}
