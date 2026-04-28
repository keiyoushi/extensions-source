package eu.kanade.tachiyomi.extension.all.akuma

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Akuma(
    override val lang: String,
    private val akumaLang: String,
) : HttpSource(),
    ConfigurableSource {

    override val name = "Akuma"

    override val baseUrl = "https://akuma.moe"

    override val supportsLatest = false

    private var nextHash: String? = null

    private var storedToken: String? = null

    private val ddosGuardIntercept = DDosGuardInterceptor(network.cloudflareClient)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(ddosGuardIntercept)
        .addInterceptor(::tokenInterceptor)
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun tokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.method == "POST" && request.header("X-CSRF-TOKEN") == null) {
            val modifiedRequest = request.newBuilder()
                .addHeader("X-Requested-With", "XMLHttpRequest")

            val token = getToken()
            val response = chain.proceed(
                modifiedRequest
                    .addHeader("X-CSRF-TOKEN", token)
                    .build(),
            )

            if (!response.isSuccessful && response.code == 419) {
                response.close()
                storedToken = null // reset the token
                val newToken = getToken()
                return chain.proceed(
                    modifiedRequest
                        .addHeader("X-CSRF-TOKEN", newToken)
                        .build(),
                )
            }

            return response
        }

        return chain.proceed(request)
    }

    private fun getToken(): String {
        if (storedToken.isNullOrEmpty()) {
            val request = GET(baseUrl, headers)
            val response = client.newCall(request).execute()

            val document = response.asJsoup()
            val token = document.select("head meta[name*=csrf-token]")
                .attr("content")

            if (token.isEmpty()) {
                throw IOException("Unable to find CSRF token")
            }

            storedToken = token
        }

        return storedToken!!
    }

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val displayFullTitle: Boolean get() = preferences.getBoolean(PREF_TITLE, false)

    private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")
    private fun String.shortenTitle() = this.replace(shortenTitleRegex, "").trim()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_TITLE
            title = "Display manga title as full title"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun popularMangaRequest(page: Int): Request {
        val payload = FormBody.Builder()
            .add("view", "3")
            .build()

        val url = baseUrl.toHttpUrlOrNull()!!.newBuilder()

        if (page == 1) {
            nextHash = null
        } else {
            url.addQueryParameter("cursor", nextHash)
        }
        if (lang != "all") {
            // append like `q=language:english$`
            url.addQueryParameter("q", "language:$akumaLang$")
        }

        return POST(url.toString(), headers, payload)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        if (document.text().contains("Max keywords of 3 exceeded.")) {
            throw Exception("Login required for more than 3 filters")
        } else if (document.text().contains("Max keywords of 8 exceeded.")) {
            throw Exception("Only max of 8 filters are allowed")
        }

        val mangas = document.select(".post-loop li").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.select("a").attr("href"))
                title = element.select(".overlay-title").text().replace("\"", "").let {
                    if (displayFullTitle) it else it.shortenTitle()
                }
                thumbnail_url = element.select("img").attr("abs:src")
            }
        }

        val nextUrl = document.select(".page-item a[rel*=next]").first()?.attr("href")

        nextHash = nextUrl?.toHttpUrlOrNull()?.queryParameter("cursor")

        return MangasPage(mangas, !nextHash.isNullOrEmpty())
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = if (query.startsWith(PREFIX_ID)) {
        val url = "/g/${query.substringAfter(PREFIX_ID)}"
        val manga = SManga.create().apply { this.url = url }
        fetchMangaDetails(manga).map {
            MangasPage(listOf(it.apply { this.url = url }), false)
        }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val request = popularMangaRequest(page)

        val finalQuery: MutableList<String> = mutableListOf(query)

        if (lang != "all") {
            finalQuery.add("language:$akumaLang$")
        }
        filters.forEach { filter ->
            when (filter) {
                is TextFilter -> {
                    if (filter.state.isNotEmpty()) {
                        finalQuery.addAll(
                            filter.state.split(",").filter { it.isNotBlank() }.map {
                                (if (it.trim().startsWith("-")) "-" else "") + "${filter.tag}:\"${it.trim().replace("-", "")}\""
                            },
                        )
                    }
                }

                is OptionFilter -> {
                    if (filter.state > 0) finalQuery.add("opt:${filter.getValue()}")
                }

                is CategoryFilter -> {
                    filter.state.forEach {
                        when {
                            it.isIncluded() -> finalQuery.add("category:\"${it.name}\"")
                            it.isExcluded() -> finalQuery.add("-category:\"${it.name}\"")
                        }
                    }
                }

                else -> {}
            }
        }

        val url = request.url.newBuilder()
            .setQueryParameter("q", finalQuery.joinToString(" "))
            .build()

        return request.newBuilder()
            .url(url)
            .build()
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.select(".entry-title").text().replace("\"", "").let {
                if (displayFullTitle) it else it.shortenTitle()
            }
            thumbnail_url = document.select(".img-thumbnail").attr("abs:src")

            author = document.select(".group~.value").eachText().joinToString()
            artist = document.select(".artist~.value").eachText().joinToString()

            val characters = document.select(".character~.value").eachText()
            val parodies = document.select(".parody~.value").eachText()
            val males = document.select(".male~.value")
                .map { "${it.text()} ♂" }
            val females = document.select(".female~.value")
                .map { "${it.text()} ♀" }
            val others = document.select(".other~.value")
                .map { "${it.text()} ◊" }

            genre = (males + females + others).joinToString()
            description = buildString {
                append(
                    "Full English and Japanese title: \n",
                    document.select(".entry-title").text(),
                    "\n",
                    document.select(".entry-title+span").text(),
                    "\n\n",
                )

                append("Language: ", document.select(".language~.value").eachText().joinToString(), "\n")
                append("Pages: ", document.select(".pages .value").text(), "\n")
                append("Upload Date: ", document.select(".date .value>time").text().replace(" ", ", ") + " UTC", "\n")
                append("Categories: ", document.selectFirst(".info-list .value")?.text() ?: "Unknown", "\n\n")

                parodies.takeIf { it.isNotEmpty() }?.let { append("Parodies: ", parodies.joinToString(), "\n") }
                characters.takeIf { it.isNotEmpty() }?.let { append("Characters: ", characters.joinToString(), "\n") }
            }
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.UNKNOWN
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain("${response.request.url}/1")
                name = "Chapter"
                date_upload = dateFormat.tryParse(document.select(".date .value>time").text())
            },
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val totalPages = document.select(".nav-select option").last()
            ?.attr("value")?.toIntOrNull() ?: return emptyList()

        val url = document.location().substringBeforeLast("/")

        return (1..totalPages).map { i ->
            if (i == 1) {
                Page(i, url = "$url/$i", imageUrl = document.select(".entry-content img").attr("abs:src"))
            } else {
                Page(i, url = "$url/$i")
            }
        }
    }

    override fun imageUrlParse(response: Response): String = response.asJsoup().select(".entry-content img").attr("abs:src")

    override fun getFilterList(): FilterList = getFilters()

    companion object {
        const val PREFIX_ID = "id:"
        private const val PREF_TITLE = "pref_title"
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
}
