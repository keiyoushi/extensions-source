package eu.kanade.tachiyomi.extension.es.lectortmounoriginal

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class LectorTmoUnoriginal : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrl.toHttpUrl().host }

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int) = getMangaList(page) { add("orderby", "views") }

    override suspend fun getLatestUpdates(page: Int) = getMangaList(page) { add("orderby", "date") }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getMangaList(page) {
        add("search", query.trim())
        add("orderby", filters.firstInstance<SortFilter>().selected)
        add("media_type", filters.firstInstance<TypeFilter>().selected)
        add("genre", filters.firstInstanceOrNull<GenreFilter>()?.selected.orEmpty())
        add("demographic", filters.firstInstance<DemographicFilter>().selected)
        add("status", filters.firstInstance<StatusFilter>().selected)
    }

    private suspend fun getMangaList(page: Int, params: FormBody.Builder.() -> Unit): MangasPage {
        val nonce = archiveNonce()
        val response = try {
            postArchiveFilter(nonce, page, params)
        } catch (e: HttpException) {
            // The site answers 403 once its archive nonce has expired
            if (e.code != 403) throw e
            postArchiveFilter(archiveNonce(failed = nonce), page, params)
        }
        val document = Jsoup.parseBodyFragment(response.parseAs<ArchiveFilterDto>().data.html, baseUrl)

        val mangas = document.select("div.manga-card-wrapper > a.block").map { card ->
            SManga.create().apply {
                url = card.absUrl("href").toHttpUrl().pathSegments[1]
                title = card.selectFirst("h3.manga-title")!!.text()
                thumbnail_url = card.selectFirst(".manga-cover-wrap img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("#manga-pagination a.next") != null)
    }

    private suspend fun postArchiveFilter(nonce: String, page: Int, params: FormBody.Builder.() -> Unit) = client.post(
        "$baseUrl/wp-admin/admin-ajax.php",
        body = FormBody.Builder()
            .add("action", "mn_archive_filter")
            .add("nonce", nonce)
            .add("paged", page.toString())
            .apply(params)
            .build(),
    )

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}/"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return client.get("$baseUrl/manga/$slug/").asJsoup().toSManga().apply {
            this.url = slug
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
        val scanlator = document.selectFirst("a[href^=/grupo/] p.font-bold")?.textOrNull()

        val chapterList = document.select("#mn-chapters-list a.chapter-card").map { row ->
            SChapter.create().apply {
                url = row.absUrl("href").toHttpUrl().pathSegments[1]
                name = row.selectFirst("span.font-bold")!!.text()
                date_upload = dateFormat.tryParseDate(row.selectFirst("> div:last-child > span")?.text())
                this.scanlator = scanlator
            }
        }

        return SMangaUpdate(
            document.toSManga().apply { url = manga.url },
            chapterList.asReversed(),
        )
    }

    private fun Document.toSManga() = SManga.create().apply {
        title = selectFirst("main h1")!!.text()
        thumbnail_url = selectFirst("main section img")?.absUrl("src")

        val badges = select("main div:has(> h1) > div:first-child > span").map { it.text() }
        status = badges.firstNotNullOfOrNull { statuses[it.lowercase(Locale.ROOT)] } ?: SManga.UNKNOWN
        genre = (
            badges.filter { it.lowercase(Locale.ROOT) !in statuses } +
                select("main div:has(> h1) ~ div a[href*=/manga-genre/]").map { it.text() }
            ).joinToString()

        val synopsis = selectFirst("main h2:contains(Sinopsis) + div")
        description = buildString {
            (synopsis?.selectFirst("[data-syn-full]") ?: synopsis)
                ?.select("p")
                ?.joinTo(this, "\n\n") { it.wholeText().trim() }
            selectFirst("main h1 + p")?.textOrNull()
                ?.takeIf { it != title }
                ?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("Título original: ", it)
                }
        }.ifEmpty { null }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga-chapter/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("main#reader-strip img")
        .mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("data-src").ifEmpty { img.absUrl("src") })
        }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/manga/").asJsoup()
        .select("#filter-genre option[value~=.]")
        .map { FilterOption(it.text(), URLDecoder.decode(it.attr("value"), "UTF-8")) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            SortFilter(),
            TypeFilter(),
            data?.parseAs<List<FilterOption>>()?.let(::GenreFilter),
            DemographicFilter(),
            StatusFilter(),
        ),
    )

    private val nonceMutex = Mutex()

    private var cachedNonce: String? = null

    private suspend fun archiveNonce(failed: String? = null): String = nonceMutex.withLock {
        cachedNonce?.takeIf { it != failed }?.let { return it }
        val script = client.get("$baseUrl/manga/", cacheControl = CacheControl.FORCE_NETWORK).asJsoup()
            .selectFirst("script:containsData(MnArchive)")!!.data()
        nonceRegex.find(script)!!.groupValues[1].also { cachedNonce = it }
    }

    companion object {
        private val nonceRegex = """nonce:\s*"(\w+)"""".toRegex()
        private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

        // Status badges, kept out of genre
        private val statuses = mapOf(
            "en emisión" to SManga.ONGOING,
            "finalizado" to SManga.COMPLETED,
            "en pausa" to SManga.ON_HIATUS,
            "descontinuado" to SManga.CANCELLED,
            "no publicado" to SManga.UNKNOWN,
        )
    }
}
