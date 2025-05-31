package eu.kanade.tachiyomi.extension.en.mehgazone

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri.encode
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
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.parser.Parser.unescapeEntities
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Mehgazone : ConfigurableSource, HttpSource() {

    override val name = "Mehgazone"

    override val baseUrl = "https://mehgazone.com"

    override val lang = "en"

    override val supportsLatest = false

    // disables "related" in apps that support it
    @Suppress("VIRTUAL_MEMBER_HIDDEN", "unused")
    val supportsRelatedMangas = false

    // authentication doesn't work with default client
    override val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()

        builder.addInterceptor(authInterceptor)

        network.client.interceptors.forEach {
            when (it::class.simpleName!!) {
                "UncaughtExceptionInterceptor",
                "UserAgentInterceptor",
                -> builder.addInterceptor(it)
                else -> {}
            }
        }

        builder.build()
    }

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = true
        prettyPrint = true
    }

    private val fallbackTitleDateFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("dd-MM-yyyy", Locale.US)
    }

    private val textToImageURL = "https://fakeimg.ryd.tools/1500x2126/ffffff/000000/?font=museo&font_size=42"

    private fun String.image() = textToImageURL + "&text=" + encode(this)

    private fun String.unescape() = unescapeEntities(this, false)

    private fun String.linkify() = SpannableString(this).apply { Linkify.addLinks(this, Linkify.WEB_URLS) }

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun getMangaUrl(manga: SManga) = manga.url

    override fun popularMangaParse(response: Response) = MangasPage(
        response.asJsoup()
            .selectFirst("#main aside.primary-sidebar .sidebar-group")!!
            .select("h2")
            .filter { el -> el.text().contains("Latest", true) }
            .map {
                SManga.create().apply {
                    title = it.text().split('"')[1].unescape()
                    url = it.nextElementSibling()!!.nextElementSibling()!!.selectFirst("a")!!.attr("href").substringBefore("/feed")
                    thumbnail_url = it.nextElementSibling()!!.selectFirst("img")!!.attr("src")
                }
            },
        false,
    )

    override fun mangaDetailsRequest(manga: SManga) =
        GET(manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val html = response.asJsoup()
        val thumbnailRegex = Regex("/[^/]+-([0-9]+\\.png)\$", RegexOption.IGNORE_CASE)

        title = html.head().selectFirst("title")!!.text().unescape()
        url = response.request.url.toString()
        author = "Patricia Barton"
        status = SManga.ONGOING
        thumbnail_url =
            html.getElementById("content")!!
                .select("img")
                .firstOrNull { it.attr("src").matches(thumbnailRegex) }
                ?.attr("src")
                ?.replace(thumbnailRegex, "/\$1")
    }

    override fun chapterListRequest(manga: SManga): Request = chapterListRequest(manga.url, 1)

    private fun chapterListRequest(url: String, page: Int): Request =
        GET(
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
        val apiResponse: MutableList<ChapterListDto> =
            json.decodeFromString<List<ChapterListDto>>(response.body.string()).toMutableList()
        val mangaUrl = response.request.url.toString().substringBefore("/wp-json/")

        if (hasNextPage(response.headers, apiResponse.size, 1)) {
            var page = 1
            do {
                page++
                val tempResponse = client.newCall(chapterListRequest(mangaUrl, page)).execute()
                val headers = tempResponse.headers
                val tempApiResponse: List<ChapterListDto> =
                    json.decodeFromString(tempResponse.body.string())

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
                        .ifEmpty { fallbackTitleDateFormat.format(it.date.time) }
                    date_upload = it.date.time.time
                    chapter_number = i.toFloat()
                }
            }.reversed()
    }

    // Adapted from the xkcd source's wordWrap function
    private fun wordWrap(text: String) = buildString {
        var charCount = 0
        text.replace('\n', ' ').split(' ').forEach { w ->
            if (charCount > 25) {
                append("\n")
                charCount = 0
            }
            append(w).append(' ')
            charCount += w.length + 1
        }
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET(
            chapter.url.substringBefore("/?") +
                "/wp-json/wp/v2/posts?per_page=1&_fields=link,content,excerpt,date,title" +
                "&include=" + chapter.url.substringAfter("p="),
            headers,
        )

    override fun pageListParse(response: Response): List<Page> {
        val apiResponse: PageListDto = json.decodeFromString<List<PageListDto>>(response.body.string()).first()

        val content = Jsoup.parse(apiResponse.content.rendered, apiResponse.link)

        val images = content.select("img")
            .mapIndexed { i, it -> Page(i, "", it.attr("src")) }
            .toMutableList()

        if (apiResponse.excerpt.rendered.isNotBlank()) {
            images.add(
                Page(
                    images.size,
                    "",
                    wordWrap(Jsoup.parse(apiResponse.excerpt.rendered.unescape()).text()).image(),
                ),
            )
        }

        return images.toList()
    }

    private class BasicAuthInterceptor(private var user: String?, private var password: String?) : Interceptor {
        fun setUser(user: String?) {
            setAuth(user, password)
        }

        fun setPassword(password: String?) {
            setAuth(user, password)
        }

        fun setAuth(user: String?, password: String?) {
            this.user = user
            this.password = password
            credentials = getCredentials()
        }

        private fun getCredentials(): String? =
            if (!user.isNullOrBlank() && !password.isNullOrBlank()) {
                Credentials.basic(user!!, password!!)
            } else {
                null
            }

        private var credentials: String? = getCredentials()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            if (
                !request.url.encodedPath.contains("/wp-json/wp/v2/") ||
                user.isNullOrBlank() ||
                password.isNullOrBlank() ||
                credentials.isNullOrBlank()
            ) {
                return chain.proceed(request)
            }

            val authenticatedRequest = request.newBuilder()
                .header("Authorization", credentials!!)
                .build()

            return chain.proceed(authenticatedRequest)
        }
    }

    @Serializable
    private data class ChapterListDto(
        val id: Int,
        @Serializable(DateTimeSerializer::class)
        @SerialName("date_gmt")
        val date: Calendar,
        val title: RenderedDto,
        val excerpt: RenderedDto,
    )

    @Serializable
    private data class PageListDto(
        @Serializable(DateTimeSerializer::class)
        val date: Calendar,
        val title: RenderedDto,
        val link: String,
        val content: RenderedDto,
        val excerpt: RenderedDto,
    )

    @Serializable
    private data class RenderedDto(
        val rendered: String,
    )

    private object DateTimeSerializer : KSerializer<Calendar> {
        private val sdf by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US) }

        override val descriptor = PrimitiveSerialDescriptor("DateTime", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: Calendar) = encoder.encodeString(sdf.format(value.time))
        override fun deserialize(decoder: Decoder): Calendar {
            val cal = Calendar.getInstance()
            val date = sdf.parse(decoder.decodeString())
            if (date != null) {
                cal.time = date
            }
            return cal
        }
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

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

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        fetchPopularManga(0).map {
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
