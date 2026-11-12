package keiyoushi.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Issue
import keiyoushi.lint.detectors.ContentDetector
import keiyoushi.lint.detectors.DateTimeDetector
import keiyoushi.lint.detectors.FlowDetector
import keiyoushi.lint.detectors.JsonDetector
import keiyoushi.lint.detectors.JsoupDetector
import keiyoushi.lint.detectors.KotlinIdiomDetector
import keiyoushi.lint.detectors.NetworkDetector
import keiyoushi.lint.detectors.SourceDetector
import org.junit.Test

class DetectorsTest {

    private fun check(issue: Issue, code: String): TestLintResult = lint()
        .files(*Stubs.all, kotlin("src/test/pkg/Test.kt", code).indented())
        .issues(issue)
        .allowMissingSdk()
        // Sources are ktlint-formatted, so the whitespace and parenthesis variants don't occur
        .skipTestModes(
            TestMode.SUPPRESSIBLE,
            TestMode.TYPE_ALIAS,
            TestMode.IMPORT_ALIAS,
            TestMode.FULLY_QUALIFIED,
            TestMode.WHITESPACE,
            TestMode.PARENTHESIZED,
            TestMode.BODY_REMOVAL,
            TestMode.IF_TO_WHEN,
        )
        .run()

    private fun TestLintResult.count(n: Int) = expectWarningCount(n)

    // region Network

    @Test
    fun newCall() {
        check(
            NetworkDetector.NEW_CALL,
            """
            package test.pkg
            import okhttp3.*
            suspend fun f(client: OkHttpClient) = client.newCall(Request.Builder().build()).execute()
            fun blocking(client: OkHttpClient) = client.newCall(Request.Builder().build()).execute()
            suspend fun fireAndForget(client: OkHttpClient) = client.newCall(Request.Builder().build()).enqueue(Any())
            """,
        ).count(1)
    }

    @Test
    fun toast() {
        check(
            NetworkDetector.TOAST,
            """
            package test.pkg
            import android.content.Context
            import android.widget.Toast
            class Pref { fun setOnPreferenceChangeListener(l: () -> Boolean) {} }
            fun parse(context: Context) { Toast.makeText(context, "x", Toast.LENGTH_SHORT).show() }
            fun prefs(context: Context, p: Pref) = p.setOnPreferenceChangeListener {
                Toast.makeText(context, "Restart the app", Toast.LENGTH_SHORT).show()
                true
            }
            """,
        ).count(1)
    }

    @Test
    fun errorsForHardRules() {
        lint()
            .files(
                *Stubs.all,
                kotlin(
                    "src/test/pkg/Test.kt",
                    """
                    package test.pkg
                    import android.content.Context
                    import kotlinx.serialization.json.Json
                    import uy.kohesive.injekt.injectLazy
                    class A(context: Context) {
                        private val json: Json by injectLazy()
                        private val prefs = context.getSharedPreferences("source_1", 0)
                        private val key = "show_nsfw_source"
                    }
                    """,
                ).indented(),
            )
            .issues(JsonDetector.LOCAL_JSON, KotlinIdiomDetector.SHARED_PREFERENCES, ContentDetector.APP_PREFERENCE)
            .allowMissingSdk()
            .skipTestModes(TestMode.SUPPRESSIBLE, TestMode.TYPE_ALIAS, TestMode.IMPORT_ALIAS, TestMode.FULLY_QUALIFIED, TestMode.WHITESPACE, TestMode.PARENTHESIZED)
            .run()
            .expectErrorCount(3)
    }

    @Test
    fun manualReferer() {
        check(
            NetworkDetector.MANUAL_REFERER,
            """
            package test.pkg
            import okhttp3.*
            fun f() = Request.Builder().header("Referer", "https://a/").header("Accept", "x").build()
            """,
        ).count(1)
    }

    @Test
    fun defaultRefererInConfigureHeaders() {
        check(
            NetworkDetector.DEFAULT_REFERER,
            """
            package test.pkg
            import okhttp3.*
            fun Headers.Builder.configureHeaders(baseUrl: String) = set("Referer", "${'$'}baseUrl/").set("X-Api", "1")
            """,
        ).count(1)
    }

    @Test
    fun headerAdd() {
        check(
            NetworkDetector.HEADER_ADD,
            """
            package test.pkg
            import okhttp3.*
            fun f(b: Headers.Builder) = b.add("Accept", "application/json").add("X-Custom", "1").set("Authorization", "x")
            """,
        ).count(1)
    }

    @Test
    fun browserManagedHeader() {
        check(
            NetworkDetector.BROWSER_HEADER,
            """
            package test.pkg
            import okhttp3.*
            fun f(b: Headers.Builder) = b.set("Sec-Fetch-Mode", "cors").set("Accept-Language", "en").removeAll("Cache-Control").set("Accept", "*/*")
            """,
        ).count(3)
    }

    @Test
    fun headersNewBuilder() {
        check(
            NetworkDetector.HEADERS_NEW_BUILDER,
            """
            package test.pkg
            import eu.kanade.tachiyomi.source.online.HttpSource
            abstract class S : HttpSource() {
                private val api get() = headers.newBuilder().build()
                private val ok get() = headersBuilder().build()
            }
            """,
        ).count(1)
    }

    @Test
    fun bodyStringOutsideInterceptor() {
        check(
            NetworkDetector.BODY_STRING,
            """
            package test.pkg
            import okhttp3.*
            fun parse(response: Response) = response.body.string()
            class I : Interceptor {
                override fun intercept(chain: Interceptor.Chain): Response {
                    val r = chain.proceed(chain.request())
                    r.body.string()
                    return r
                }
            }
            fun OkHttpClientBuilderLike.x() = addInterceptor { chain -> chain.proceed(chain.request()).also { it.body.string() } }
            interface OkHttpClientBuilderLike { fun addInterceptor(i: Interceptor): OkHttpClientBuilderLike }
            """,
        ).count(1)
    }

    @Test
    fun httpUrlToString() {
        check(
            NetworkDetector.HTTP_URL_TO_STRING,
            """
            package test.pkg
            import okhttp3.*
            import keiyoushi.network.get
            suspend fun f(client: OkHttpClient, url: HttpUrl) {
                client.get(url.toString())
                client.get(url)
            }
            """,
        ).count(1)
    }

    @Test
    fun hardcodedUserAgent() {
        check(
            NetworkDetector.HARDCODED_USER_AGENT,
            """
            package test.pkg
            const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            """,
        ).count(1)
    }

    // endregion

    // region JSON

    @Test
    fun dataClassDto() {
        check(
            JsonDetector.DATA_CLASS_DTO,
            """
            package test.pkg
            import kotlinx.serialization.Serializable
            @Serializable data class A(val id: Int)
            @Serializable class B(val id: Int)
            data class C(val id: Int)
            """,
        ).count(1)
    }

    @Test
    fun redundantSerialName() {
        check(
            JsonDetector.REDUNDANT_SERIAL_NAME,
            """
            package test.pkg
            import kotlinx.serialization.*
            @Serializable class A(@SerialName("title") val title: String, @SerialName("cover_img") val coverImg: String)
            """,
        ).count(1)
    }

    @Test
    fun snakeCaseField() {
        check(
            JsonDetector.SNAKE_CASE_FIELD,
            """
            package test.pkg
            import kotlinx.serialization.*
            @Serializable class A(val manga_id: Int, @SerialName("_count") val count: Int, val title: String)
            """,
        ).count(1)
    }

    @Test
    fun nullDefault() {
        check(
            JsonDetector.NULL_DEFAULT,
            """
            package test.pkg
            import kotlinx.serialization.*
            @Serializable class A(val a: String? = null, val b: String?, val c: Int = 0)
            class NotDto(val a: String? = null)
            """,
        ).count(1)
    }

    @Test
    fun manualJsonTraversal() {
        check(
            JsonDetector.MANUAL_JSON_TRAVERSAL,
            """
            package test.pkg
            import kotlinx.serialization.json.*
            fun f(e: JsonElement) = e.jsonObject["a"]!!.jsonPrimitive.content
            """,
        ).count(2)
    }

    @Test
    fun decodeFromString() {
        check(
            JsonDetector.DECODE_FROM_STRING,
            """
            package test.pkg
            import kotlinx.serialization.json.Json
            fun f(s: String): Int = Json.decodeFromString(s)
            """,
        ).count(1)
    }

    // endregion

    // region Jsoup

    @Test
    fun selectFirst() {
        check(
            JsoupDetector.SELECT_FIRST,
            """
            package test.pkg
            import org.jsoup.nodes.Element
            fun f(e: Element) = e.select("a").first()
            fun g(e: Element) = e.selectFirst("a")
            """,
        ).count(1)
    }

    @Test
    fun textTrim() {
        check(
            JsoupDetector.TEXT_TRIM,
            """
            package test.pkg
            import org.jsoup.nodes.Element
            fun f(e: Element) = e.text().trim()
            fun g(s: String) = s.trim()
            """,
        ).count(1)
    }

    @Test
    fun relativeHref() {
        check(
            JsoupDetector.RELATIVE_HREF,
            """
            package test.pkg
            import org.jsoup.nodes.Element
            fun setUrlWithoutDomain(url: String) {}
            fun f(e: Element) {
                setUrlWithoutDomain(e.attr("href"))
                setUrlWithoutDomain(e.attr("abs:href"))
            }
            """,
        ).count(1)
    }

    @Test
    fun volatileSelector() {
        check(
            JsoupDetector.VOLATILE_SELECTOR,
            """
            package test.pkg
            import org.jsoup.nodes.Element
            fun f(e: Element) {
                e.select("div.styles_Card__jN8og a")
                e.select("div.css-1x2y3z4")
                e.select("div.card > a.title")
            }
            """,
        ).count(2)
    }

    // endregion

    // region Dates

    @Test
    fun currentTimeMillis() {
        check(
            DateTimeDetector.CURRENT_TIME_MILLIS,
            """
            package test.pkg
            fun f() = System.currentTimeMillis() - 1000
            """,
        ).count(1)
    }

    @Test
    fun simpleDateFormat() {
        check(
            DateTimeDetector.SIMPLE_DATE_FORMAT,
            """
            package test.pkg
            val f = java.text.SimpleDateFormat("yyyy-MM-dd")
            """,
        ).count(1)
    }

    @Test
    fun datePatternLocale() {
        check(
            DateTimeDetector.PATTERN_LOCALE,
            """
            package test.pkg
            import java.time.format.DateTimeFormatter
            import java.util.Locale
            val a = DateTimeFormatter.ofPattern("dd MMM yyyy")
            val b = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
            val c = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val d = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            """,
        ).count(1)
    }

    // endregion

    // region Kotlin idioms

    @Test
    fun regexInFunction() {
        check(
            KotlinIdiomDetector.REGEX_IN_FUNCTION,
            """
            package test.pkg
            private val TOP = Regex("a+")
            class A {
                private val field = Regex("b+")
                private val lazyOne by lazy { Regex("c+") }
                fun f(s: String) = Regex(${"\"\"\""}\d+${"\"\"\""}).find(s)
                fun g(s: String) = "x+".toRegex().find(s)
                fun h(s: String, v: String) = Regex(v).find(s)
                companion object { val C = Regex("d") }
            }
            """,
        ).count(2)
    }

    @Test
    fun regexEscaping() {
        check(
            KotlinIdiomDetector.REGEX_ESCAPING,
            """
            package test.pkg
            val a = Regex("\\d+")
            val b = Regex(${"\"\"\""}\d+${"\"\"\""})
            """,
        ).count(1)
    }

    @Test
    fun filterIsInstanceFirst() {
        check(
            KotlinIdiomDetector.FILTER_IS_INSTANCE,
            """
            package test.pkg
            fun f(l: List<Any>) = l.filterIsInstance<String>().firstOrNull()
            """,
        ).count(1)
    }

    @Test
    fun joinToStringSeparator() {
        check(
            KotlinIdiomDetector.JOIN_TO_STRING_SEPARATOR,
            """
            package test.pkg
            fun f(l: List<String>) = l.joinToString(", ") + l.joinToString(" - ") + l.joinToString()
            """,
        ).count(1)
    }

    @Test
    fun stdlibEncoding() {
        check(
            KotlinIdiomDetector.STDLIB_ENCODING,
            """
            package test.pkg
            fun a(s: String) = s.toByteArray(Charsets.UTF_8)
            fun b(b: ByteArray) = String(b, Charsets.UTF_8)
            fun c(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
            fun d(s: String) = s.encodeToByteArray()
            """,
        ).count(3)
    }

    @Test
    fun manualCrypto() {
        check(
            KotlinIdiomDetector.MANUAL_CRYPTO,
            """
            package test.pkg
            fun f() = java.security.MessageDigest.getInstance("MD5")
            """,
        ).count(1)
    }

    @Test
    fun swallowedError() {
        check(
            KotlinIdiomDetector.RUN_CATCHING,
            """
            package test.pkg
            fun f(s: String) = runCatching { s.toInt() }.getOrNull()
            fun h(s: String) = s.toIntOrNull()
            """,
        ).count(1)
        check(
            KotlinIdiomDetector.RUN_CATCHING,
            """
            package test.pkg
            fun g(s: String) {
                try {
                    s.toInt()
                } catch (e: Exception) {
                }
            }
            """,
        ).count(1)
    }

    @Test
    fun pageEmptyUrl() {
        check(
            KotlinIdiomDetector.PAGE_EMPTY_URL,
            """
            package test.pkg
            import eu.kanade.tachiyomi.source.model.Page
            val a = Page(0, "", "https://img")
            val b = Page(0, imageUrl = "https://img")
            """,
        ).count(1)
    }

    @Test
    fun markdownBullet() {
        check(
            KotlinIdiomDetector.MARKDOWN_BULLET,
            """
            package test.pkg
            fun f(l: List<String>) = l.joinToString("\n") { "• ${'$'}it" }
            """,
        ).count(1)
    }

    @Test
    fun httpException() {
        check(
            KotlinIdiomDetector.HTTP_EXCEPTION,
            """
            package test.pkg
            fun f(code: Int): Nothing = throw Exception("HTTP ${'$'}code")
            fun g(): Nothing = throw Exception("Chapter not found")
            """,
        ).count(1)
    }

    @Test
    fun customException() {
        check(
            KotlinIdiomDetector.CUSTOM_EXCEPTION,
            """
            package test.pkg
            class LoginRequired : Exception("Log in")
            class SiteCheck(val siteKey: String) : java.io.IOException("Check failed")
            class NotAnException
            """,
        ).count(1)
    }

    @Test
    fun debugLog() {
        check(
            KotlinIdiomDetector.DEBUG_LOG,
            """
            package test.pkg
            fun f() = android.util.Log.d("T", "x")
            """,
        ).count(1)
    }

    // endregion

    // region Source

    @Test
    fun supportsLatestDefault() {
        check(
            SourceDetector.DEFAULT_OVERRIDE,
            """
            package test.pkg
            import keiyoushi.source.KeiSource
            abstract class A : KeiSource() { override val supportsLatest = true }
            abstract class Theme : KeiSource() { override val supportsLatest = false }
            abstract class B : Theme() { override val supportsLatest = true }
            """,
        ).count(1)
    }

    @Test
    fun storedBaseUrl() {
        check(
            SourceDetector.STORED_BASE_URL,
            """
            package test.pkg
            import keiyoushi.source.KeiSource
            abstract class A : KeiSource() {
                private val apiUrl = "${'$'}baseUrl/api"
                private val apiHeaders by lazy { headers }
                private val ok get() = "${'$'}baseUrl/api"
                private val okHeaders get() = headersBuilder().build()
                private val constant = "https://cdn.example.com"
                companion object { const val X = "y" }
            }
            """,
        ).count(2)
    }

    @Test
    fun baseUrlConcat() {
        check(
            SourceDetector.BASE_URL_CONCAT_ISSUE,
            """
            package test.pkg
            import eu.kanade.tachiyomi.source.model.*
            fun a(baseUrl: String, manga: SManga) = baseUrl + manga.url
            fun b(baseUrl: String, chapter: SChapter) = "${'$'}baseUrl${'$'}{chapter.url}"
            fun c(baseUrl: String, manga: SManga) = "${'$'}baseUrl/manga/${'$'}{manga.url}"
            """,
        ).count(2)
    }

    @Test
    fun mandatoryFallback() {
        check(
            SourceDetector.MANDATORY_FALLBACK,
            """
            package test.pkg
            import eu.kanade.tachiyomi.source.model.*
            fun f(t: String?, n: String?) {
                SManga.create().apply {
                    title = t ?: ""
                    url = t.orEmpty()
                    thumbnail_url = t ?: ""
                }
                SChapter.create().apply {
                    name = n ?: "Unknown"
                    url = n!!
                }
            }
            """,
        ).count(3)
    }

    @Test
    fun impossibleFlagsAndEmptyChapters() {
        check(
            SourceDetector.IMPOSSIBLE_FLAGS,
            """
            package test.pkg
            fun fetchMangaUpdate(fetchDetails: Boolean, fetchChapters: Boolean): Int {
                if (!fetchDetails && !fetchChapters) return 0
                return 1
            }
            """,
        ).count(1)
        check(
            SourceDetector.EMPTY_CHAPTERS,
            """
            package test.pkg
            fun fetchMangaUpdate(fetchChapters: Boolean, chapters: List<String>): List<String> {
                val a = if (fetchChapters) listOf("x") else emptyList()
                val b = if (fetchChapters) listOf("x") else chapters
                return a + b
            }
            """,
        ).count(1)
    }

    @Test
    fun chapterSort() {
        check(
            SourceDetector.CHAPTER_SORT,
            """
            package test.pkg
            import eu.kanade.tachiyomi.source.model.SChapter
            fun f(l: List<SChapter>) = l.sortedByDescending { it.chapter_number }
            """,
        ).count(1)
    }

    // endregion

    // region Flow

    private val sourceHeader = """
        package test.pkg
        import eu.kanade.tachiyomi.source.model.*
        import keiyoushi.network.get
        import keiyoushi.source.KeiSource
        import okhttp3.HttpUrl
    """.trimIndent()

    @Test
    fun urlLookupNotInitialized() {
        check(
            FlowDetector.INITIALIZED,
            sourceHeader + """

            abstract class A : KeiSource() {
                private fun parseDetails(s: String): SManga = TODO()
                override suspend fun getMangaByUrl(url: HttpUrl): SManga? = parseDetails("x")
            }
            abstract class B : KeiSource() {
                private fun parseDetails(s: String): SManga = TODO()
                override suspend fun getMangaByUrl(url: HttpUrl): SManga? = parseDetails("x").apply { initialized = true }
            }
            """,
        ).count(1)
    }

    @Test
    fun singleMangaByUrl() {
        check(
            FlowDetector.SINGLE_MANGA_BY_URL,
            sourceHeader + """

            abstract class A : KeiSource() {
                private fun lookup(url: HttpUrl): SManga = TODO()
                override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage = MangasPage(listOf(lookup(url)), false)
            }
            """,
        ).count(1)
    }

    @Test
    fun requestPerItemAndListingRules() {
        check(
            FlowDetector.REQUEST_PER_ITEM,
            sourceHeader + """

            abstract class A : KeiSource() {
                suspend fun getPopularManga(page: Int): MangasPage {
                    val ids = listOf("a", "b")
                    ids.map { client.get("/cover/${'$'}it") }
                    client.get("/list")
                    return MangasPage(emptyList(), false)
                }
            }
            """,
        ).count(1)
        check(
            FlowDetector.CLIENT_SIDE_PAGING,
            sourceHeader + """

            fun getSearchMangaList(all: List<SManga>, page: Int) = MangasPage(all.subList(0, 10), true)
            fun other(all: List<SManga>) = all.subList(0, 1)
            """,
        ).count(1)
        check(
            FlowDetector.RANDOM_LISTING,
            sourceHeader + """

            fun getPopularManga(all: List<SManga>) = MangasPage(all.shuffled(), false)
            """,
        ).count(1)
    }

    @Test
    fun defaultHeadersArgument() {
        check(
            FlowDetector.DEFAULT_HEADERS_ARGUMENT,
            sourceHeader + """

            abstract class A : KeiSource() {
                suspend fun f() = client.get("/a", headers)
                suspend fun g() = client.get("/a")
            }
            """,
        ).count(1)
    }

    @Test
    fun emptyPagesThrow() {
        check(
            FlowDetector.EMPTY_PAGES_THROW,
            sourceHeader + """

            abstract class A : KeiSource() {
                override suspend fun getPageList(chapter: SChapter): List<Page> {
                    val pages = listOf<Page>()
                    if (pages.isEmpty()) throw Exception("No pages")
                    return pages
                }
            }
            abstract class KeiSourcePageList { abstract suspend fun getPageList(chapter: SChapter): List<Page> }
            """,
        ).count(1)
    }

    // endregion

    // region Content

    @Test
    fun absoluteAndFragmentUrls() {
        check(
            ContentDetector.ABSOLUTE_URL,
            """
            package test.pkg
            import eu.kanade.tachiyomi.source.model.*
            import org.jsoup.nodes.Element
            fun f(baseUrl: String, slug: String, e: Element) {
                SManga.create().apply { url = "${'$'}baseUrl/manga/${'$'}slug" }
                SManga.create().apply { url = e.absUrl("href") }
                SManga.create().apply { url = "/manga/${'$'}slug" }
            }
            """,
        ).count(2)
        check(
            ContentDetector.URL_FRAGMENT,
            """
            package test.pkg
            import eu.kanade.tachiyomi.source.model.*
            fun f(slug: String, id: Int) {
                SChapter.create().apply { url = "/read/${'$'}slug#${'$'}id" }
            }
            """,
        ).count(1)
    }

    @Test
    fun hardcodedHost() {
        check(
            ContentDetector.HARDCODED_HOST,
            """
            package test.pkg
            import okhttp3.HttpUrl
            val HttpUrl.host: String get() = ""
            fun f(url: HttpUrl, base: String) = url.host == "example.com" || url.host == base
            """,
        ).count(1)
    }

    @Test
    fun urlStringSplit() {
        check(
            ContentDetector.URL_STRING_SPLIT,
            """
            package test.pkg
            fun f(url: String, name: String) = url.substringAfterLast("/") + name.substringAfterLast("/")
            """,
        ).count(1)
    }

    @Test
    fun collapsedTextAndDescription() {
        check(
            ContentDetector.COLLAPSED_TEXT,
            """
            package test.pkg
            fun f(html: String) = org.jsoup.Jsoup.parseBodyFragment(html).text()
            """,
        ).count(1)
        check(
            ContentDetector.MANUAL_DESCRIPTION,
            """
            package test.pkg
            fun f(n: Int) = "**Tracked:** ${'$'}n"
            """,
        ).count(1)
    }

    @Test
    fun stripMangaTitle() {
        check(
            ContentDetector.STRIP_MANGA_TITLE,
            """
            package test.pkg
            import eu.kanade.tachiyomi.source.model.*
            fun f(text: String, mangaTitle: String) = SChapter.create().apply { name = text.removePrefix(mangaTitle).trim() }
            """,
        ).count(1)
    }

    @Test
    fun turnstileAndZip() {
        check(
            ContentDetector.CUSTOM_TURNSTILE,
            """
            package test.pkg
            class LunarTurnstileSolver
            class TurnstileToken(val token: String)
            """,
        ).count(1)
        check(
            ContentDetector.ZIP_INPUT_STREAM,
            """
            package test.pkg
            fun f(input: java.io.InputStream) = java.util.zip.ZipInputStream(input)
            """,
        ).count(1)
    }

    // endregion
}
