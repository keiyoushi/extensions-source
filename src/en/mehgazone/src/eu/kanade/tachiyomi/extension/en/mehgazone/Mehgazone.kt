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
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.helper.Validate
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser.unescapeEntities
import org.jsoup.select.Collector
import org.jsoup.select.Elements
import org.jsoup.select.QueryParser
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Mehgazone :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(TextInterceptor())
        .addInterceptor(authInterceptor)

    private val uploadDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    private fun String.unescape() = unescapeEntities(this, false)

    private fun String.linkify() = SpannableString(this).apply { Linkify.addLinks(this, Linkify.WEB_URLS) }

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

    override suspend fun getPopularManga(page: Int) = MangasPage(
        client.get(baseUrl).asJsoup()
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

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(
        getPopularManga(0).mangas.filter { m -> m.title.contains(query) },
        false,
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchMangaDetails(manga) } else null
        val chapterList = if (fetchChapters) fetchChapterList(manga.url) else chapters

        SMangaUpdate(details?.await() ?: manga, chapterList)
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val html = client.get(manga.url).asJsoup()

        return manga.apply {
            title = html.head().selectFirst("title")!!.text().unescape()
            author = "Patricia Barton"
            status = SManga.ONGOING
            thumbnail_url =
                html.select("#content img[src*='.png']")
                    .firstOrNull { it.attr("src").matches(thumbnailRegex) }
                    ?.attr("src")
                    ?.replace(thumbnailRegex, "/\$1")
        }
    }

    private fun chapterListUrl(url: String, page: Int) = "$url/wp-json/wp/v2/posts?per_page=100&page=$page&_fields=id,title,date_gmt,excerpt"

    private fun hasNextPage(headers: Headers, responseSize: Int, page: Int): Boolean {
        val pages = headers["X-Wp-Totalpages"]?.toInt()
            ?: return responseSize == 100
        return page < pages
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    private suspend fun fetchChapterList(mangaUrl: String): List<SChapter> {
        val apiResponse = mutableListOf<ChapterListDto>()

        var page = 0
        do {
            page++
            val response = client.get(chapterListUrl(mangaUrl, page))
            val headers = response.headers
            val pageResponse = response.parseAs<List<ChapterListDto>>()

            apiResponse.addAll(pageResponse)
        } while (hasNextPage(headers, pageResponse.size, page))

        return apiResponse
            .filter { !it.excerpt.rendered.contains("Unlock with Patreon") }
            .distinctBy { it.id }
            .sortedBy { it.date }
            .mapIndexed { i, it ->
                SChapter.create().apply {
                    url = "$mangaUrl/?p=${it.id}"
                    name = it.title.rendered.unescape()
                        .ifEmpty { it.date.substringBefore('T') }
                    date_upload = uploadDateFormat.tryParseDateTime(it.date)
                    chapter_number = i.toFloat()
                }
            }.reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = chapter.url.toHttpUrl()
        val pageListUrl = chapterUrl
            .newBuilder("/wp-json/wp/v2/posts?per_page=1&_fields=link,content,excerpt,date,title")!!
            .setQueryParameter("include", chapterUrl.queryParameter("p"))
            .build()

        val apiResponse: PageListDto = client.get(pageListUrl).parseAs<List<PageListDto>>().first()

        val content = Jsoup.parseBodyFragment(apiResponse.content.rendered, apiResponse.link)

        val images = content.select("img")
            .mapIndexed { i, it -> Page(i, imageUrl = it.attr("src")) }
            .toMutableList()

        if (apiResponse.excerpt.rendered.isNotBlank()) {
            images.add(
                Page(
                    images.size,
                    imageUrl = TextInterceptorHelper.createUrl("", Jsoup.parseBodyFragment(apiResponse.excerpt.rendered.unescape()).text()),
                ),
            )
        }

        return images.toList()
    }

    private val preferences: SharedPreferences by getPreferencesLazy()

    companion object {
        private val thumbnailRegex = Regex("/[^/]+-([0-9]+\\.png)\$", RegexOption.IGNORE_CASE)

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
}
