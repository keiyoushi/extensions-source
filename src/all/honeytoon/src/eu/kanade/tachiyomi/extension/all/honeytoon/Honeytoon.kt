package eu.kanade.tachiyomi.extension.all.honeytoon

import android.content.SharedPreferences
import android.widget.Toast
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
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.lib.i18n.Intl
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Honeytoon(
    private val language: Language,
) : HttpSource(),
    ConfigurableSource {

    override val name: String = "Honeytoon"

    override val baseUrl: String = "https://honeytoon.com"

    override val lang: String = language.lang

    override val supportsLatest: Boolean = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val isAdultContentEnabled: Boolean
        get() = preferences.getBoolean(PREF_ADULT_KEY, false)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3, 1)
        .addInterceptor { chain ->
            val fragment = chain.request().url.fragment
            if (fragment != null && fragment.contains("locked")) {
                throw IOException(intl["chapter_locked_warning"])
            }
            chain.proceed(chain.request())
        }
        .addInterceptor(ScrambledImageInterceptor())
        .addNetworkInterceptor(
            when {
                isAdultContentEnabled -> CookieInterceptor(baseUrl.substringAfter("//"), "eighteen" to "1")
                else -> CookieInterceptor(baseUrl.substringAfter("//"), "eighteen" to "0")
            },
        )
        .build()

    private val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "pt-BR"),
        classLoader = this::class.java.classLoader!!,
    )

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        val langPath = if (lang == "en") "" else "/${language.langPath}"
        return GET("$baseUrl$langPath/ranking", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = mangaParse(response, ".section.popular")

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = mangaParse(response, ".section.new")

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val url = query.toHttpUrlOrNull()
        if (url != null) {
            val url = url.newBuilder()
                .fragment("deeplink")
                .build()
            return Observable.fromCallable {
                MangasPage(listOf(mangaDetailsParse(client.newCall(GET(url, headers)).execute())), false)
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val langPath = if (lang == "en") "" else "/${language.langPath}"
        val form = FormBody.Builder()
            .add("query", query)
            .build()
        return POST("$baseUrl$langPath/api/comic/search", headers, form)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<SearchDto>>().map {
            SManga.create().apply {
                title = Jsoup.parseBodyFragment(it.title).selectFirst("body")!!.ownText()
                thumbnail_url = "https://pic.honeytoon.com/${it.image}"
                url = it.link
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    // Details

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val fragment = response.request.url.fragment
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()

        author = document.select(".comic-book__story-art a")?.joinToString { it.text() }
        description = document.selectFirst(".comic-book__desc")?.text()
        genre = document.select(".comic-book-content a[href*=genre], .comic-tag").joinToString { it.text() }
        status = when {
            document.selectFirst(".comic-book-content .label__item--complete") != null -> SManga.COMPLETED
            document.selectFirst(".comic-book-content .label__item--dayofpublication") != null -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }

        // The manga page has really large cover images,
        // so I'm prioritizing covers from the 'popular', 'latest' and 'search'.
        if (!fragment.isNullOrBlank()) {
            thumbnail_url = document.selectFirst(".comic-book-img img")?.absUrl("src")
            setUrlWithoutDomain(document.location())
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".comic-list-items > a")
            .mapIndexed { index, element ->
                val isLocked = element.selectFirst(".lock-ico, .token-ico") != null
                SChapter.create().apply {
                    name = buildString {
                        append(element.selectFirst(".comic-list__title-desc")!!.text())
                        if (isLocked) {
                            append(" \uD83D\uDD12")
                        }
                    }

                    date_upload = dateFormat.tryParse(element.selectFirst(".comic-list__title-date")?.text())
                    setUrlWithoutDomain(
                        element.absUrl("href")?.takeIf { !isLocked }
                            ?: (document.location() + "/$index#locked"),
                    )
                }
            }.reversed()
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".single__item img, .comic-canvas-scramble").mapIndexed { index, element ->
            when (element.tagName()) {
                "img" -> Page(index, imageUrl = element.imgSrc())
                else -> Page(index, imageUrl = "$baseUrl/api/common/resource/sync?t=${element.attr("data-token")}")
            }
        }
    }
    private fun Element.imgSrc() = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url.split("/").filter(String::isNotBlank)
            .toMutableList()
            .apply {
                removeAt(lastIndex)
            }
            .last()
        return GET("$baseUrl${chapter.url}#slug=$slug", headers)
    }

    override fun imageUrlParse(response: Response): String = ""

    // Settings

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ADULT_KEY
            title = intl["switch_adult_title"]
            summary = intl["switch_adult_summary"]
            setDefaultValue(false)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, intl["switch_adult_toast"], Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    // Utils
    fun mangaParse(response: Response, cssSelector: String): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("$cssSelector .preview-card__link").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".preview-card__title")!!.text()
                thumbnail_url = element.selectFirst(".preview-card__image")?.absUrl("src")
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    private val dateFormat: SimpleDateFormat by lazy {
        val locale = when {
            language.lang.contains("-") -> {
                val (lang, country) = language.lang.split("-")
                Locale(lang, country)
            }

            else -> Locale(language.lang)
        }
        SimpleDateFormat("MMMM dd , yyyy", locale)
    }

    @Serializable
    class SearchDto(
        val title: String,
        val image: String,
        val link: String,
    )

    companion object {
        private const val PREF_ADULT_KEY = "prefAdultKey"
    }
}
