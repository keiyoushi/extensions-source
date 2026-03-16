package eu.kanade.tachiyomi.extension.en.mehgazone

import android.content.SharedPreferences
import android.text.InputType
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.en.mehgazone.interceptors.BasicAuthInterceptor
import eu.kanade.tachiyomi.extension.en.mehgazone.serialization.ChapterListDto
import eu.kanade.tachiyomi.extension.en.mehgazone.serialization.PageListDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.helper.Validate
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser.unescapeEntities
import org.jsoup.select.Collector
import org.jsoup.select.Elements
import org.jsoup.select.QueryParser
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Mehgazone :
    HttpSource(),
    ConfigurableSource {

    override val name = "Mehgazone"

    override val baseUrl = "https://mehgazone.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient by lazy {
        network.cloudflareClient
            .newBuilder()
            .addInterceptor(TextInterceptor())
            .addInterceptor(authInterceptor)
            .build()
    }

    private val uploadDateFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    }

    private fun String.unescape() = unescapeEntities(this, false)

    private fun String.linkify() = SpannableString(this).apply { Linkify.addLinks(this, Linkify.WEB_URLS) }

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun getMangaUrl(manga: SManga) = manga.url

    private fun Elements.selectFirstBackport(cssQuery: String) = selectFirst(cssQuery, this)

    // backport from jsoup 1.19.1
    private fun selectFirst(cssQuery: String, roots: Elements): Element? {
        Validate.notEmpty(cssQuery)
        Validate.notNull(roots)
        val evaluator = QueryParser.parse(cssQuery)

        for (root in roots) {
            val first = Collector.findFirst(evaluator, root)
            if (first != null) return first
        }

        return null
    }

    override fun popularMangaParse(response: Response) = MangasPage(
        response.asJsoup()
            .selectFirst("#main aside.primary-sidebar .sidebar-group")!!
            .select("h2")
            .filter { el -> el.text().contains("Latest", true) }
            .map {
                SManga.create().apply {
                    title = it.text().split('"')[1].unescape()
                    url = it.nextElementSiblings().selectFirstBackport("a[href*='/feed']")!!.attr("href").toHttpUrl().resolve("/").toString()
                    thumbnail_url = it.nextElementSiblings().selectFirstBackport("img")!!.attr("src")
                }
            },
        false,
    )

    override fun mangaDetailsRequest(manga: SManga) = GET(manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val html = response.asJsoup()
        val thumbnailRegex = Regex("/[^/]+-([0-9]+\\.png)\$", RegexOption.IGNORE_CASE)

        title = html.head().selectFirst("title")!!.text().unescape()
        url = response.request.url.toString()
        author = "Patricia Barton"
        status = SManga.ONGOING
        thumbnail_url =
            html.select("#content img[src*='.png']")
                .firstOrNull { it.attr("src").matches(thumbnailRegex) }
                ?.attr("src")
                ?.replace(thumbnailRegex, "/\$1")
    }

    override fun chapterListRequest(manga: SManga): Request = chapterListRequest(manga.url, 1)

    private fun chapterListRequest(url: String, page: Int): Request = GET(
        "$url/wp-json/wp/v2/posts?per_page=100&page=$page&_fields=id,title,date_gmt,excerpt",
        headers,
    )

    private fun hasNextPage(headers: Headers, responseSize: Int, page: Int): Boolean {
        val pages = headers["X-Wp-Totalpages"]?.toInt()
            ?: return responseSize == 100
        return page < pages
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    override fun chapterListParse(response: Response): List<SChapter> {
        val apiResponse = response.parseAs<List<ChapterListDto>>().toMutableList()
        val mangaUrl = response.request.url.toString().substringBefore("/wp-json/")

        if (hasNextPage(response.headers, apiResponse.size, 1)) {
            var page = 1
            do {
                page++
                val tempResponse = client.newCall(chapterListRequest(mangaUrl, page)).execute()
                val headers = tempResponse.headers
                val tempApiResponse = tempResponse.parseAs<List<ChapterListDto>>()

                apiResponse.addAll(tempApiResponse)
                tempResponse.close()
            } while (hasNextPage(headers, tempApiResponse.size, page))
        }

        return apiResponse
            .filter { !it.excerpt.rendered.contains("Unlock with Patreon") }
            .distinctBy { it.id }
            .sortedBy { it.date }
            .mapIndexed { i, it ->
                SChapter.create().apply {
                    url = "$mangaUrl/?p=${it.id}"
                    name = it.title.rendered.unescape()
                        .ifEmpty { it.date.substringBefore('T') }
                    date_upload = uploadDateFormat.tryParse(it.date)
                    chapter_number = i.toFloat()
                }
            }.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url.toHttpUrl()
        val pageListUrl = chapterUrl
            .newBuilder("/wp-json/wp/v2/posts?per_page=1&_fields=link,content,excerpt,date,title")!!
            .setQueryParameter("include", chapterUrl.queryParameter("p"))
            .build()
        return GET(pageListUrl.toString(), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val apiResponse: PageListDto = response.parseAs<List<PageListDto>>().first()

        val content = Jsoup.parseBodyFragment(apiResponse.content.rendered, apiResponse.link)

        val images = content.select("img")
            .mapIndexed { i, it -> Page(i, "", it.attr("src")) }
            .toMutableList()

        if (apiResponse.excerpt.rendered.isNotBlank()) {
            images.add(
                Page(
                    images.size,
                    "",
                    TextInterceptorHelper.createUrl("", Jsoup.parseBodyFragment(apiResponse.excerpt.rendered.unescape()).text()),
                ),
            )
        }

        return images.toList()
    }

    private val preferences: SharedPreferences by getPreferencesLazy()

    companion object {
        private const val WORDPRESS_USERNAME_PREF_KEY = "WORDPRESS_USERNAME"
        private const val WORDPRESS_USERNAME_PREF_TITLE = "WordPress username"
        private const val WORDPRESS_USERNAME_PREF_SUMMARY = "The WordPress username"
        private const val WORDPRESS_USERNAME_PREF_DIALOG = "To see your username:\n\n" +
            "Go to https://bodysuit23.mehgazone.com/wp-admin/profile.php and you should see your username near the top of the page."
        private const val WORDPRESS_USERNAME_PREF_DEFAULT_VALUE = ""

        private const val WORDPRESS_APP_PASSWORD_PREF_KEY = "WORDPRESS_APP_PASSWORD"
        private const val WORDPRESS_APP_PASSWORD_PREF_TITLE = "WordPress app password"
        private const val WORDPRESS_APP_PASSWORD_PREF_SUMMARY = "The WordPress app password (not your account password)"
        private const val WORDPRESS_APP_PASSWORD_PREF_DIALOG = "To setup:\n\n" +
            "Go to https://bodysuit23.mehgazone.com/wp-admin/profile.php and you should be able to create a new app password near the bottom of the page."
        private const val WORDPRESS_APP_PASSWORD_PREF_DEFAULT_VALUE = ""
    }

    private val authInterceptor: BasicAuthInterceptor by lazy {
        BasicAuthInterceptor(
            preferences.getString(WORDPRESS_USERNAME_PREF_KEY, WORDPRESS_USERNAME_PREF_DEFAULT_VALUE),
            preferences.getString(WORDPRESS_APP_PASSWORD_PREF_KEY, WORDPRESS_APP_PASSWORD_PREF_DEFAULT_VALUE),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            val name = preferences.getString(WORDPRESS_USERNAME_PREF_KEY, WORDPRESS_USERNAME_PREF_DEFAULT_VALUE)!!

            key = WORDPRESS_USERNAME_PREF_KEY
            title = WORDPRESS_USERNAME_PREF_TITLE
            dialogMessage = WORDPRESS_USERNAME_PREF_DIALOG.linkify()
            summary = name.ifBlank { WORDPRESS_USERNAME_PREF_SUMMARY }
            setDefaultValue(WORDPRESS_USERNAME_PREF_DEFAULT_VALUE)

            setOnBindEditTextListener {
                getDialogMessageFromEditText(it).let {
                    @Suppress("NestedLambdaShadowedImplicitParameter")
                    if (it == null) {
                        Log.e(name, "Could not find dialog TextView")
                    } else {
                        it.movementMethod = LinkMovementMethod.getInstance()
                    }
                }
            }

            setOnPreferenceChangeListener { preference, newValue ->
                authInterceptor.setUser(newValue as String)
                preference.summary = newValue.ifBlank { WORDPRESS_USERNAME_PREF_SUMMARY }
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            val pwd = preferences.getString(WORDPRESS_APP_PASSWORD_PREF_KEY, WORDPRESS_APP_PASSWORD_PREF_DEFAULT_VALUE)!!

            key = WORDPRESS_APP_PASSWORD_PREF_KEY
            title = WORDPRESS_APP_PASSWORD_PREF_TITLE
            dialogMessage = WORDPRESS_APP_PASSWORD_PREF_DIALOG.linkify()
            summary = if (pwd.isBlank()) WORDPRESS_APP_PASSWORD_PREF_SUMMARY else "●".repeat(pwd.length)
            setDefaultValue(WORDPRESS_APP_PASSWORD_PREF_DEFAULT_VALUE)

            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                getDialogMessageFromEditText(it).let {
                    @Suppress("NestedLambdaShadowedImplicitParameter")
                    if (it == null) {
                        Log.e(name, "Could not find dialog TextView")
                    } else {
                        it.movementMethod = LinkMovementMethod.getInstance()
                    }
                }
            }

            setOnPreferenceChangeListener { preference, newValue ->
                authInterceptor.setPassword(newValue as String)
                preference.summary = if (newValue.isBlank()) WORDPRESS_APP_PASSWORD_PREF_SUMMARY else "●".repeat(newValue.length)
                true
            }
        }.also(screen::addPreference)
    }

    private fun getDialogMessageFromEditText(editText: EditText): TextView? {
        val parent = editText.parent
        if (parent !is ViewGroup || parent.childCount == 0) return null

        for (i in 1..parent.childCount) {
            val child = parent.getChildAt(i - 1)
            if (child is TextView && child !is EditText) return child
        }

        return null
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = fetchPopularManga(0).map {
        MangasPage(
            it.mangas.filter { m -> m.title.contains(query) },
            false,
        )
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
}
