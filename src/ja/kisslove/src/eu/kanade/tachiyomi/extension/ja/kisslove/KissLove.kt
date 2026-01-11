package eu.kanade.tachiyomi.extension.ja.kisslove

import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class KissLove : HttpSource() {
    override val name = "KissLove"
    override val baseUrl = "https://klz9.com"
    override val lang = "ja"
    override val supportsLatest = true

    private val intl = Intl(
        Locale.getDefault().language,
        setOf("en", "ja", "zh"),
        lang,
        this::class.java.classLoader!!,
    )
    private var cachedGenres: List<CheckBoxFilter>? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/trending-daily")
            .build()
        return GET(url, sigAppend())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<Manga>>()
        val mangas = result.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/${manga.url}.html"
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "36")
            .build()
        return GET(url, sigAppend())
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<PagedManga>()
        val mangas = result.items.map { it.toSManga() }
        val hasNextPage = result.currentPage < result.totalPages
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/manga/list")
            addQueryParameter("search", query)
            addQueryParameter("sort", "Popular")
            addQueryParameter("order", "desc")

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        val included = filter.state.filter { it.state }.joinToString(",") { it.name }
                        addQueryParameter("genre", included)
                    }

                    is StatusFilter -> addQueryParameter("status", filter.toUriPart())
                    else -> {}
                }
            }
        }.build()
        return GET(url, sigAppend())
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/slug")
            .addPathSegment(manga.url)
            .build()
        return GET(url, sigAppend())
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<Manga>()
        return result.toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<Manga>()
        val slug = result.slug
        return result.chapters
            .sortedByDescending { it.chapter }
            .map { it.toSChapter(slug) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterSuffix = chapter.url.substringAfterLast("/")
        return "$baseUrl/$chapterSuffix.html"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringBeforeLast("/")
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/chapter")
            .addPathSegment(id)
            .build()
        return GET(url, sigAppend())
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<Chapter>()
        return result.content
            .lines()
            .filter { it.isNotBlank() && !FILTER_IMG.contains(it) }
            .mapIndexed { i, img ->
                val url = img.toHttpUrl()
                val newHost = IMG_URL_MAPPING[url.host] ?: url.host
                Page(
                    index = i,
                    imageUrl = url.newBuilder().host(newHost).build().toString(),
                )
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun sigAppend(): Headers = headers.newBuilder().apply {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val payload = "$timestamp.$CLIENT_ID"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        val signature = hashBytes.joinToString("") { byte ->
            "%02x".format(byte)
        }
        add("X-Client-Sig", signature)
        add("X-Client-Ts", timestamp)
    }.build()

    override fun getFilterList(): FilterList {
        val filterList = ArrayList<Filter<*>>()

        filterList.add(StatusFilter(intl["status"], getStatusList()))

        val genres = cachedGenres
        if (!genres.isNullOrEmpty()) {
            filterList.add(
                GenreFilter(
                    intl["genre"],
                    genres,
                ),
            )
        } else {
            filterList.add(Filter.Header(intl["genreTip"]))
            launchAsyncGenreLoad()
        }

        return FilterList(filterList)
    }

    private fun launchAsyncGenreLoad() {
        scope.launch {
            try {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegments("api/genres")
                    .build()

                val genres = client.newCall(GET(url, sigAppend()))
                    .await()
                    .parseAs<List<Genre>>()
                    .map {
                        CheckBoxFilter(it.name)
                    }

                cachedGenres = genres
            } catch (_: Exception) {
            }
        }
    }

    private fun getStatusList() = arrayOf(
        intl["all"] to "",
        intl["ongoing"] to "Ongoing",
        intl["completed"] to "Completed",
    )

    private class StatusFilter(name: String, private val status: Array<Pair<String, String>>) :
        Filter.Select<String>(name, status.map { it.first }.toTypedArray()) {
        fun toUriPart() = status[state].second
    }

    private class GenreFilter(name: String, state: List<CheckBoxFilter>) : Filter.Group<CheckBoxFilter>(name, state)

    private class CheckBoxFilter(name: String) : Filter.CheckBox(name)

    companion object {
        private const val CLIENT_ID = "KL9K40zaSyC9K40vOMLLbEcepIFBhUKXwELqxlwTEF"
        val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        }
        private val FILTER_IMG = setOf(
            "https://1.bp.blogspot.com/-ZMyVQcnjYyE/W2cRdXQb15I/AAAAAAACDnk/8X1Hm7wmhz4hLvpIzTNBHQnhuKu05Qb0gCHMYCw/s0/LHScan.png",
            "https://s4.imfaclub.com/images/20190814/Credit_LHScan_5d52edc2409e7.jpg",
            "https://s4.imfaclub.com/images/20200112/5e1ad960d67b2_5e1ad962338c7.jpg",
        )
        private val IMG_URL_MAPPING = mapOf(
            "imfaclub.com" to "j1.jfimv2.xyz",
            "s2.imfaclub.com" to "j2.jfimv2.xyz",
            "s4.imfaclub.com" to "j4.jfimv2.xyz",
            "ihlv1.xyz" to "j1.jfimv2.xyz",
            "s2.ihlv1.xyz" to "j2.jfimv2.xyz",
            "s4.ihlv1.xyz" to "j4.jfimv2.xyz",
            "h1.klimv1.xyz" to "j1.jfimv2.xyz",
            "h2.klimv1.xyz" to "j2.jfimv2.xyz",
            "h4.klimv1.xyz" to "j4.jfimv2.xyz",
        )
    }
}
