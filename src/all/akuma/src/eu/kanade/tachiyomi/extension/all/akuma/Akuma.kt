package eu.kanade.tachiyomi.extension.all.akuma

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Akuma :
    KeiSource(),
    ConfigurableSource {

    private val akumaLang: String
        get() = when (lang) {
            "all" -> "all"
            "en" -> "english"
            "id" -> "indonesian"
            "jv" -> "javanese"
            "ca" -> "catalan"
            "ceb" -> "cebuano"
            "cs" -> "czech"
            "da" -> "danish"
            "de" -> "german"
            "et" -> "estonian"
            "es" -> "spanish"
            "eo" -> "esperanto"
            "fr" -> "french"
            "it" -> "italian"
            "hi" -> "hindi"
            "hu" -> "hungarian"
            "nl" -> "dutch"
            "pl" -> "polish"
            "pt" -> "portuguese"
            "vi" -> "vietnamese"
            "tr" -> "turkish"
            "ru" -> "russian"
            "uk" -> "ukrainian"
            "ar" -> "arabic"
            "ko" -> "korean"
            "zh" -> "chinese"
            "ja" -> "japanese"
            else -> lang
        }

    override val supportsLatest = false

    private var nextHash: String? = null

    private var storedToken: String? = null

    private val ddosGuardIntercept = DDosGuardInterceptor(network.client)

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ddosGuardIntercept)
        addInterceptor(::tokenInterceptor)
        rateLimit(2)
    }

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

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1) ?: return null
        return fetchMangaUpdate(
            SManga.create().apply {
                this.url = "/g/$id"
            },
            emptyList(),
            true,
            false,
        ).manga
    }
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page == 1) {
                nextHash = null
            } else {
                addQueryParameter("cursor", nextHash)
            }

            setQueryParameter("q", finalQuery.joinToString(" "))
        }

        val payload = FormBody.Builder()
            .add("view", "3")
            .build()

        return parseSearch(client.post(url.build(), payload))
    }

    private fun parseSearch(response: Response): MangasPage {
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

        nextHash = nextUrl?.toHttpUrl()?.queryParameter("cursor")

        return MangasPage(mangas, !nextHash.isNullOrEmpty())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(doc),
            chapters = chapterListParse(doc),
        )
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        setUrlWithoutDomain(document.location())
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

    private fun chapterListParse(document: Document): List<SChapter> = listOf(
        SChapter.create().apply {
            setUrlWithoutDomain("${document.location()}/1")
            name = "Chapter"
            date_upload = dateFormat.tryParseDate(document.select(".date .value>time").text())
        },
    )

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val totalPages = document.select(".nav-select option").last()
            ?.attr("value")?.toIntOrNull() ?: return emptyList()

        val url = document.location().substringBeforeLast("/")

        return (1..totalPages).map { i ->
            if (i == 1) {
                Page(i, imageUrl = document.select(".entry-content img").attr("abs:src"))
            } else {
                Page(i, url = "$url/$i")
            }
        }
    }

    override suspend fun getImageUrl(page: Page): String = client.get(page.url).asJsoup().select(".entry-content img").attr("abs:src")

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    companion object {
        private const val PREF_TITLE = "pref_title"
    }
}
