package eu.kanade.tachiyomi.extension.ja.rawkuma

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okhttp3.internal.closeQuietly
import okio.IOException
import org.jsoup.Jsoup
import rx.Observable
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale

class Rawkuma : HttpSource() {
    override val name = "Rawkuma"
    override val lang = "ja"
    override val baseUrl = "https://rawkuma.net"
    override val supportsLatest = true
    override val versionId = 2

    override val client = network.cloudflareClient.newBuilder()
        // fix disk cache
        .apply {
            val index = networkInterceptors().indexOfFirst { it is BrotliInterceptor }
            if (index >= 0) interceptors().add(networkInterceptors().removeAt(index))
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) =
        searchMangaRequest(page, "", SortFilter.popular)

    override fun popularMangaParse(response: Response) =
        searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) =
        searchMangaRequest(page, "", SortFilter.latest)

    override fun latestUpdatesParse(response: Response) =
        searchMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith("https://")) {
            deepLink(query)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/wp-admin/admin-ajax.php?action=advanced_search"
        val body = MultipartBody.Builder().apply {
            setType(MultipartBody.FORM)
            addFormDataPart("nonce", getNonce())
            filters.firstInstanceOrNull<GenreInclusion>()?.selected.also {
                addFormDataPart("inclusion", it ?: "OR")
            }
            filters.firstInstanceOrNull<GenreExclusion>()?.selected.also {
                addFormDataPart("exclusion", it ?: "OR")
            }
            addFormDataPart("page", page.toString())
            val genres = filters.firstInstanceOrNull<GenreFilter>()
            genres?.included.orEmpty().also {
                addFormDataPart("genre", it.toJsonString())
            }
            genres?.excluded.orEmpty().also {
                addFormDataPart("genre_exclude", it.toJsonString())
            }
            addFormDataPart("author", "[]")
            addFormDataPart("artist", "[]")
            addFormDataPart("project", "0")
            filters.firstInstanceOrNull<TypeFilter>()?.checked.orEmpty().also {
                addFormDataPart("type", it.toJsonString())
            }
            val sort = filters.firstInstance<SortFilter>()
            addFormDataPart("order", if (sort.isAscending) "asc" else "desc")
            addFormDataPart("orderby", sort.sort)
            addFormDataPart("query", query.trim())
        }.build()

        return POST(url, headers, body)
    }

    private var nonce: String? = null

    @Synchronized
    private fun getNonce(): String {
        if (nonce == null) {
            val url = "$baseUrl/wp-admin/admin-ajax.php?type=search_form&action=get_nonce"
            val response = client.newCall(GET(url, headers)).execute()

            Jsoup.parseBodyFragment(response.body.string())
                .selectFirst("input[name=search_nonce]")
                ?.attr("value")
                ?.takeIf { it.isNotBlank() }
                ?.also {
                    nonce = it
                }
        }

        return nonce ?: throw Exception("Unable to get nonce")
    }

    private val metadataClient = client.newBuilder()
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).newBuilder()
                .header("Cache-Control", "max-age=${24 * 60 * 60}")
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .build()
        }.build()

    override fun getFilterList() = runBlocking(Dispatchers.IO) {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
        )

        val url = "$baseUrl/wp-json/wp/v2/genre?per_page=100&page=1&orderby=count&order=desc"
        val response = metadataClient.newCall(
            GET(url, headers, CacheControl.FORCE_CACHE),
        ).await()

        if (!response.isSuccessful) {
            metadataClient.newCall(
                GET(url, headers, CacheControl.FORCE_NETWORK),
            ).enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.closeQuietly()
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(name, "Failed to fetch genre filter", e)
                    }
                },
            )

            filters.addAll(
                listOf(
                    Filter.Separator(),
                    Filter.Header("Press 'reset' to load genre filter"),
                ),
            )

            return@runBlocking FilterList(filters)
        }

        val data = try {
            response.parseAs<List<Term>>()
        } catch (e: Throwable) {
            Log.e(name, "Failed to parse genre filters", e)

            filters.addAll(
                listOf(
                    Filter.Separator(),
                    Filter.Header("Failed to parse genre filter"),
                ),
            )

            return@runBlocking FilterList(filters)
        }

        filters.addAll(
            listOf(
                GenreFilter(
                    data.map { it.name to it.slug },
                ),
                GenreInclusion(),
                GenreInclusion(),
            ),
        )

        FilterList(filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = Jsoup.parseBodyFragment(response.body.string(), baseUrl)
        val slugs = document.select("div > a[href*=/manga/]:has(> img)").map {
            it.absUrl("href").toHttpUrl().pathSegments[1]
        }.ifEmpty {
            return MangasPage(emptyList(), false)
        }

        val url = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder().apply {
            slugs.forEach { slug ->
                addQueryParameter("slug[]", slug)
            }
            addQueryParameter("per_page", "${slugs.size + 1}")
            addQueryParameter("_embed", null)
        }.build()

        val details = client.newCall(GET(url, headers)).execute()
            .parseAs<List<Manga>>()
            .filterNot { manga ->
                manga.embedded.getTerms("type").contains("Novel")
            }
            .associateBy { it.slug }

        val mangas = slugs.mapNotNull { slug ->
            details[slug]?.toSManga()
        }

        val hasNextPage = document.selectFirst("button > svg") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun deepLink(url: String): Observable<MangasPage> {
        val httpUrl = url.toHttpUrl()
        if (
            httpUrl.host == baseUrl.toHttpUrl().host &&
            httpUrl.pathSegments.size >= 2 &&
            httpUrl.pathSegments[0] == "manga"
        ) {
            val slug = httpUrl.pathSegments[1]
            val url = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder()
                .addQueryParameter("slug[]", slug)
                .addQueryParameter("_embed", null)
                .build()

            return client.newCall(GET(url, headers))
                .asObservableSuccess()
                .map { response ->
                    val manga = response.parseAs<List<Manga>>()[0]

                    if (manga.embedded.getTerms("type").contains("Novel")) {
                        throw Exception("Novels are not supported")
                    }

                    MangasPage(listOf(manga.toSManga()), false)
                }
        }

        return Observable.error(Exception("Unsupported url"))
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.parseAs<MangaUrl>().id

        return GET("$baseUrl/wp-json/wp/v2/manga/$id?_embed", headers)
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.parseAs<MangaUrl>().slug

        return "$baseUrl/manga/$slug/"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<Manga>().toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.parseAs<MangaUrl>().id
        val url = "$baseUrl/wp-admin/admin-ajax.php".toHttpUrl().newBuilder()
            .addQueryParameter("manga_id", id.toString())
            .addQueryParameter("page", "1")
            .addQueryParameter("action", "chapter_list")
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = Jsoup.parseBodyFragment(response.body.string(), baseUrl)

        return document.select("#chapter-list > div[data-chapter-number] > a").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                name = it.selectFirst("div > span")!!.ownText()
                date_upload = dateFormat.tryParse(
                    it.selectFirst("time")?.attr("datetime"),
                )
            }
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("main section img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }
}
