package eu.kanade.tachiyomi.extension.id.comicaso

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.io.IOException
import kotlin.time.Instant

@Source
abstract class Comicaso :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val scope get() = preferences.getString(PREF_SCOPE, SCOPE_GENERAL)!!

    override val supportsFilterFetching = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::altHostInterceptor)
        .rateLimit(4)

    // Retry failed images on the other CDNs the site's reader lists, carried in the url fragment
    private fun altHostInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val altHosts = request.url.fragment?.split(',') ?: return chain.proceed(request)
        val hosts = listOf(request.url.host) + altHosts
        hosts.dropLast(1).forEach { host ->
            val response = try {
                chain.proceed(request.withHost(host))
            } catch (_: IOException) {
                null
            }
            if (response?.isSuccessful == true) return response
            response?.close()
        }
        return chain.proceed(request.withHost(hosts.last()))
    }

    private fun Request.withHost(host: String) = newBuilder().url(url.newBuilder().host(host).fragment(null).build()).build()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("scope", scope)
            .addQueryParameter("period", "all")
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("a.trending-card").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.absUrl("href"))
                title = card.selectFirst("h3")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        val url = "$baseUrl/api/catalog-more".toHttpUrl().newBuilder().apply {
            addQueryParameter("view", if (genre != null) "genre" else "browse")
            genre?.let { addQueryParameter("genre", it) }
            addQueryParameter("scope", scope)
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.firstInstanceOrNull<TypeFilter>()?.selected?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.selected?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<SortFilter>()?.selected?.let { addQueryParameter("sort", it) }
            addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
        }.build()
        val catalog = client.get(url, Fetch.XHR.from("/browse")).parseAs<CatalogMoreDto>()
        val document = catalog.html.asJsoup(baseUrl)
        val mangas = if (genre != null) {
            document.select("article.story-row").map { row ->
                SManga.create().apply {
                    setUrlWithoutDomain(row.selectFirst("a.story-cover")!!.absUrl("href"))
                    title = row.selectFirst("h2")!!.text()
                    thumbnail_url = row.selectFirst("img")?.absUrl("src")
                }
            }
        } else {
            document.select(".home-grid-item > a.card").map { card ->
                SManga.create().apply {
                    setUrlWithoutDomain(card.absUrl("href"))
                    title = card.selectFirst("h3")!!.text()
                    thumbnail_url = card.selectFirst("img")?.absUrl("src")
                }
            }
        }
        return MangasPage(mangas, catalog.hasMore)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.first() !in setOf("manga", "read")) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(document.toSManga(manga.url), document.toChapters())
    }

    private fun Document.toSManga(path: String) = SManga.create().apply {
        url = path
        title = selectFirst("h1.detail-title")!!.text()
        thumbnail_url = selectFirst("img.detail-cover")?.absUrl("src")
        author = fact("Author")
        artist = fact("Artist")
        val (statuses, types) = select(".detail-capsules span").map { it.text() }.partition { it.lowercase() in STATUSES }
        status = STATUSES[statuses.firstOrNull()?.lowercase()] ?: SManga.UNKNOWN
        genre = (types + select(".detail-genres a").map { it.text() })
            .joinToString()
            .ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(selectFirst("p.synopsis"), keepNewlines = true)
            .altTitles(selectFirst(".detail-alternative")?.wholeText()?.lines().orEmpty(), "Judul alternatif")
            .build()
    }

    private fun Document.fact(label: String) = selectFirst(".detail-facts div:has(> dt:matchesOwn(^$label$)) > dd")?.text()

    private fun Document.toChapters() = select("a.detail-chapter-row").map { row ->
        SChapter.create().apply {
            setUrlWithoutDomain(row.absUrl("href"))
            name = row.selectFirst("strong")!!.text()
            date_upload = Instant.tryParse(row.selectFirst("time")?.attr("datetime"))
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select("#reader-images img").mapIndexed { index, image ->
            val url = image.absUrl("src").toHttpUrl()
            val altHosts = image.attrOrNull("data-media-sources")?.parseAs<List<MediaSourceDto>>().orEmpty()
                .map { it.url.toHttpUrl().host }
                .filter { it != url.host }
            val imageUrl = url.newBuilder().fragment(altHosts.joinToString(",").ifEmpty { null }).build()
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$baseUrl/genres".toHttpUrl().newBuilder().addQueryParameter("scope", "all").build()
        return client.get(url, Fetch.DOCUMENT).asJsoup()
            .select("a.genre-chip")
            .map { GenreDto(it.text(), it.absUrl("href").toHttpUrl().queryParameter("genre")!!) }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            TypeFilter(),
            StatusFilter(),
            SortFilter(),
            data?.let { GenreFilter(it.parseAs()) },
        ),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_SCOPE
            title = "Koleksi"
            entries = arrayOf("Umum", "Dewasa", "Semua")
            entryValues = arrayOf(SCOPE_GENERAL, "adult", "all")
            setDefaultValue(SCOPE_GENERAL)
            summary = "%s"
        }.also(screen::addPreference)
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val PREF_SCOPE = "pref_scope"
        private const val SCOPE_GENERAL = "general"

        private val STATUSES = mapOf(
            "berjalan" to SManga.ONGOING,
            "tamat" to SManga.COMPLETED,
            "hiatus" to SManga.ON_HIATUS,
        )
    }
}
