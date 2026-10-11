package eu.kanade.tachiyomi.extension.es.emperorscan

import android.webkit.WebResourceResponse
import android.widget.Toast
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
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.random.Random

@Source
abstract class EmperorScan :
    KeiSource(),
    ConfigurableSource {

    private val baseHttpUrl get() = baseUrl.toHttpUrl()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseHttpUrl.host }

    override suspend fun getPopularManga(page: Int) = library(page, "vistas", null)

    override suspend fun getLatestUpdates(page: Int) = library(page, "reciente", null)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            return library(page, filters.firstInstance<SortFilter>().value, filters.firstInstanceOrNull<GenreFilter>()?.slug)
        }
        val url = baseHttpUrl.newBuilder()
            .addPathSegment("buscar")
            .addPathSegment("")
            .addQueryParameter("q", query)
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()
        return listing(url)
    }

    private suspend fun library(page: Int, order: String, genre: String?): MangasPage {
        val url = baseHttpUrl.newBuilder().apply {
            if (genre == null) addPathSegment("manga") else addPathSegment("manga-genre").addPathSegment(genre)
            if (page > 1) addPathSegment("page").addPathSegment(page.toString())
            addPathSegment("")
            if (order != "reciente") addQueryParameter("orden", order)
        }.build()
        return listing(url)
    }

    private suspend fun listing(url: HttpUrl): MangasPage {
        val props = client.get(url).asJsoup().inertiaPage<ListingProps>().props
        return MangasPage(props.series.map { it.toSManga() }, props.pagination.next != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseHttpUrl.host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() && it != "page" } ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val props = client.get(getMangaUrl(manga)).asJsoup().inertiaPage<SeriesProps>().props
        props.series.applyTo(manga)
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        // Dates from the series page, the rest from capitulos.json
        val all = if (props.chaptersPagination.last > 1) {
            client.get(baseHttpUrl.resolve(props.series.url)!!.resolve("capitulos.json")!!).parseAs<ChapterListDto>().items
        } else {
            props.chapters
        }
        val dated = props.chapters.associateBy { it.slug }
        return SMangaUpdate(
            manga,
            all.filterNot { hideLocked && it.locked }
                .map { (dated[it.slug] ?: it).toSChapter(props.series.url) },
        )
    }

    // Madara-era search results: a post id, with the path in memo
    override fun getMangaUrl(manga: SManga): String {
        val path = manga.memo["path"]?.stringOrNull?.takeIf { manga.url.all(Char::isDigit) } ?: manga.url
        return baseUrl + path
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val page = document.inertiaPage<JsonElement>()
        if (page.component == "Reader/Locked") return emptyList()

        val props = page.props.parseAs<ReaderProps>()
        // Inline first page, then the sealed rest from the site's reader
        val pages = if (props.dims.isEmpty()) {
            props.pages
        } else {
            val entry = document.selectFirst("script[type=module][src*=/build/assets/]")!!.absUrl("src")
            val sealed = openReader(ReaderInput(entry, props.reader, props.chapter.id, props.series.slug)).associateBy { it.n }
            val inline = props.pages.size
            props.pages + (inline + 1..inline + props.dims.size).map { sealed[it] ?: throw IOException("Page $it missing") }
        }
        return pages.mapIndexed { index, page ->
            val imageUrl = page.imageUrl.toHttpUrl()
            if (imageUrl.host != baseHttpUrl.host) throw IOException("Unexpected image host")
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    // One reader WebView at a time
    private val readerMutex = Mutex()

    private suspend fun openReader(input: ReaderInput): List<PageDto> = readerMutex.withLock {
        val bridge = "r" + Random.nextLong().toULong().toString(36)
        val script = "(${asset("reader.js")})(${input.toJsonString()})" +
            ".then((pages) => $bridge.post(JSON.stringify({ pages })), (e) => $bridge.post(JSON.stringify({ error: String(e) })))"
        val result = try {
            runWebView<ReaderResult> {
                blockImages = true
                interceptRequest { request ->
                    val host = request.url.host
                    if (host == null || host == baseHttpUrl.host) null else WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0)))
                }
                jsBridge(bridge) { resolve(it.parseAs()) }
                onPageFinished { evaluateJs(script) }
                loadData(baseUrl, "")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        result.error?.let { throw IOException(it) }
        result.pages!!
    }

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl).asJsoup().inertiaPage<HomeProps>().props.genres.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<NamedDto>>().orEmpty()
        return FilterList(
            listOfNotNull(
                SortFilter(),
                genres.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
            ),
        )
    }

    private inline fun <reified T> Document.inertiaPage(): InertiaPage<T> = selectFirst("script[data-page=app]")!!.data().parseAs()

    private val preferences by getPreferencesLazy()

    private val hideLocked get() = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_PREMIUM_CHAPTERS
            title = "Filtrar capítulos VIP"
            summary = "Oculta automáticamente los capítulos VIP"
            setDefaultValue(REMOVE_PREMIUM_CHAPTERS_DEFAULT)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Para aplicar los cambios, actualiza la lista de capítulos", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val REMOVE_PREMIUM_CHAPTERS = "removePremiumChapters"
        private const val REMOVE_PREMIUM_CHAPTERS_DEFAULT = true
    }
}
