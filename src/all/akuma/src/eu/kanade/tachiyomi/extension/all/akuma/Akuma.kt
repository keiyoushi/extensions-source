package eu.kanade.tachiyomi.extension.all.akuma

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

class Akuma(
    override val lang: String,
    private val akumaLang: String,
) : ConfigurableSource, ParsedHttpSource() {

    override val name = "Akuma"

    override val baseUrl = "https://akuma.moe"

    override val supportsLatest = false

    private var nextHash: String? = null

    private var storedToken: String? = null

    private val ddosGuardIntercept = DDosGuardInterceptor(network.client)

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private var iconified = preferences.getBoolean(PREF_TAG_GENDER_ICON, false)
    private var rateLimit = preferences.getString(RATE_LIMIT, "2")?.toIntOrNull() ?: 2

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(ddosGuardIntercept)
        .addInterceptor(::tokenInterceptor)
        .rateLimit(rateLimit)
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

    override fun popularMangaSelector() = ".post-loop li"
    override fun popularMangaNextPageSelector() = ".page-item a[rel*=next]"

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val nextUrl = document.select(popularMangaNextPageSelector()).first()?.attr("href")

        nextHash = nextUrl?.toHttpUrlOrNull()?.queryParameter("cursor")

        return MangasPage(mangas, !nextHash.isNullOrEmpty())
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("a").attr("href"))
            title = element.select(".overlay-title").text()
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID)) {
            val url = "/g/${query.substringAfter(PREFIX_ID)}"
            val manga = SManga.create().apply { this.url = url }
            fetchMangaDetails(manga).map {
                MangasPage(listOf(it.apply { this.url = url }), false)
            }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val request = popularMangaRequest(page)

        val finalQuery = buildString {
            append(query)
            if (lang != "all") {
                append(" language:", akumaLang, "$")
            }
            filters.filterIsInstance<SyntaxFilter>().firstOrNull()?.let {
                if (it.state.isNotBlank()) {
                    append(it.state)
                    return@buildString
                }
            }
            filters.filterIsInstance<TextFilter>().forEach { filter ->
                if (filter.state.isBlank()) return@forEach
                filter.state.split(",").forEach {
                    // append like `a:"eye-covering bang"$`
                    if (it.startsWith("-")) {
                        append(" -", filter.identifier, ":", it.trim().substring(1))
                    } else {
                        append(" ", filter.identifier, ":", it.trim())
                    }
                }
            }
            filters.filterIsInstance<OptionFilter>().firstOrNull()?.let {
                val filter = options[it.state].second
                if (filter.isNotBlank()) {
                    append(" opt:", filter)
                }
            }
            filters.filterIsInstance<CategoryFilter>().firstOrNull()?.state?.forEach {
                if (it.isIncluded()) {
                    append(" category:\"", it.name, "\"$")
                } else if (it.isExcluded()) {
                    append(" -category:\"", it.name, "\"$")
                }
            }
        }

        val url = request.url.newBuilder()
            .setQueryParameter("q", finalQuery)
            .build()

        return request.newBuilder()
            .url(url)
            .build()
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaParse(response: Response) = popularMangaParse(response)
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document) = with(document) {
        SManga.create().apply {
            title = select(".entry-title").text()
            thumbnail_url = select(".img-thumbnail").attr("abs:src")

            author = select(".group~.value").eachText().joinToString()
            artist = select(".artist~.value").eachText().joinToString()

            val characters = select(".character~.value").eachText()
            val parodies = select(".parody~.value").eachText()
            val males = select(".male~.value")
                .map { it.text() + if (iconified) " ♂" else " (male)" }
            val females = select(".female~.value")
                .map { it.text() + if (iconified) " ♀" else " (female)" }
            // show all in tags for quickly searching

            genre = (characters + parodies + males + females).joinToString()
            description = buildString {
                append(
                    "Full English and Japanese title: \n",
                    select(".entry-title").text(),
                    "\n",
                    select(".entry-title+span").text(),
                    "\n\n",
                )

                // translated should show up in the description
                append("Language: ", select(".language~.value").text(), "\n")
                append("Pages: ", select(".pages .value").text(), "\n")
                append("Upload Date: ", select(".date .value>time").text(), "\n")
                append("Categories: ", selectFirst(".info-list .value")?.text() ?: "Unknown", "\n\n")

                // show followings for easy to reference
                append("Parodies: ", parodies.joinToString(), "\n")
                append("Characters: ", characters.joinToString(), "\n")
            }
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.UNKNOWN
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    url = "${manga.url}/1"
                    name = "Chapter"
                },
            ),
        )
    }

    override fun pageListParse(document: Document): List<Page> {
        val totalPages = document.select(".nav-select option").last()
            ?.attr("value")?.toIntOrNull() ?: return emptyList()

        val url = document.location().substringBeforeLast("/")

        val pageList = mutableListOf<Page>()

        for (i in 1..totalPages) {
            pageList.add(Page(i, "$url/$i"))
        }

        return pageList
    }

    override fun imageUrlParse(document: Document): String {
        return document.select(".entry-content img").attr("abs:src")
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TextFilter("Female Tags", "female"),
        TextFilter("Male Tags", "male"),
        CategoryFilter(),
        TextFilter("Groups", "group"),
        TextFilter("Artists", "artist"),
        TextFilter("Parody", "parody"),
        TextFilter("Characters", "character"),
        Filter.Header("Filter by pages, for example: (>20)"),
        TextFilter("Pages", "pages"),
        Filter.Header("Search in favorites, read, or commented"),
        OptionFilter(),

        Filter.Separator(),
        Filter.Header("Use syntax to search. If not blank, other filters will be ignored"),
        SyntaxFilter(),
    )

    private class CategoryFilter : Filter.Group<CategoryFilter.TagTriState>("Categories", values()) {
        class TagTriState(name: String) : TriState(name)
        private companion object {
            fun values() = listOf(
                TagTriState("doujinshi"),
                TagTriState("manga"),
                TagTriState("artist cg"),
                TagTriState("game cg"),
                TagTriState("west"),
                TagTriState("non-h"),
                TagTriState("gallery"),
                TagTriState("cosplay"),
                TagTriState("asian pron"),
                TagTriState("misc"),
            )
        }
    }
    private class TextFilter(placeholder: String, val identifier: String) : Filter.Text(placeholder)
    private class SyntaxFilter : Filter.Text("Syntax")
    private class OptionFilter :
        Filter.Select<String>("Options", options.map { it.first }.toTypedArray())

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = RATE_LIMIT
            title = "Max requests per minute"
            summary = "Requires restart. Value should be integer. Current value: $rateLimit"
            setDefaultValue(rateLimit.toString())
            setOnPreferenceChangeListener { _, newValue ->
                null != newValue.toString().toIntOrNull()?.also {
                    rateLimit = it
                    summary = "Requires restart. Value should be integer. Current value: $it"
                }
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_TAG_GENDER_ICON
            title = "Show gender as text or icon in tags (requires refresh)"
            summaryOff = "Show gender as text"
            summaryOn = "Show gender as icon"

            setOnPreferenceChangeListener { _, newValue ->
                iconified = newValue == true
                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        const val PREFIX_ID = "id:"
        private const val PREF_TAG_GENDER_ICON = "pref_tag_gender_icon"
        private const val RATE_LIMIT = "rate_limit"
        private val options = listOf(
            "None" to "",
            "Favorited only" to "favorited",
            "Read only" to "read",
            "Commented only" to "commented",
        )
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()
    override fun chapterListSelector() = throw UnsupportedOperationException()
}
