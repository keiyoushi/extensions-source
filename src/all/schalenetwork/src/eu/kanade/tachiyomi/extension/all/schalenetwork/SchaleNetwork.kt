package eu.kanade.tachiyomi.extension.all.schalenetwork

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.minutes

@Source
abstract class SchaleNetwork :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val searchLang: String
        get() = when (lang) {
            "en" -> "english"
            "ja" -> "japanese"
            "zh" -> "chinese"
            else -> ""
        }

    private val apiDomain = "schale.network"
    private val apiUrl get() = "https://api.$apiDomain"
    private val authUrl get() = "https://auth.$apiDomain"

    private fun qualityPref() = preferences.getString(PREF_IMAGERES, "1280")!!

    private fun trimTitlePref() = preferences.getBoolean(PREF_REM_ADD, false)

    private fun excludeTagsPref(): Set<String> = preferences.getString(PREF_EXCLUDE_TAGS, null)
        ?.split(",")
        ?.mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }
        ?.toSet()
        .orEmpty()

    private suspend fun getBooks(page: Int, sort: String? = null): MangasPage {
        val url = "$apiUrl/books".toHttpUrl().newBuilder().apply {
            sort?.let { addQueryParameter("sort", it) }
            addQueryParameter("page", page.toString())

            val terms = mutableListOf<String>()
            if (lang != "all") terms += "language:\"^$searchLang$\""
            val excluded = excludeTagsPref()
            if (excluded.isNotEmpty()) {
                terms += "tag:\"${excluded.joinToString(",") { "-$it" }}\""
            }
            if (terms.isNotEmpty()) addQueryParameter("s", terms.joinToString(" "))
        }.build()

        val data = client.get(url).parseAs<Books>()
        return MangasPage(
            data.entries.map { it.toSManga(trimTitlePref()) },
            data.page * data.limit < data.total,
        )
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getBooks(page, sort = "8")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getBooks(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/books".toHttpUrl().newBuilder().apply {
            val terms: MutableList<String> = mutableListOf()
            val includedTags: MutableList<Int> = mutableListOf()
            val excludedTags: MutableList<Int> = mutableListOf()

            if (lang != "all") terms += "language:\"^$searchLang$\""

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.getValue())

                    is CategoryFilter -> {
                        val activeFilter = filter.state.filter { it.state }
                        if (activeFilter.isNotEmpty()) {
                            addQueryParameter("cat", activeFilter.sumOf { it.value }.toString())
                        }
                    }

                    is TagFilter -> {
                        includedTags += filter.state
                            .filter { it.isIncluded() }
                            .map { it.id }
                        excludedTags += filter.state
                            .filter { it.isExcluded() }
                            .map { it.id }
                    }

                    is TagConditionFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter(filter.param, filter.toUriPart())
                        }
                    }

                    is TextFilter -> {
                        if (filter.state.isNotEmpty()) {
                            val tags = filter.state.split(",").filter(String::isNotBlank).joinToString(",")
                            if (tags.isNotBlank()) {
                                terms += "${filter.type}:" + if (filter.type == "pages") tags else "\"$tags\""
                            }
                        }
                    }

                    else -> {}
                }
            }

            if (includedTags.isNotEmpty()) {
                addQueryParameter("include", includedTags.joinToString(","))
            }
            if (excludedTags.isNotEmpty()) {
                addQueryParameter("exclude", excludedTags.joinToString(","))
            }

            if (query.isNotEmpty()) terms.add("title:\"$query\"")
            if (terms.isNotEmpty()) addQueryParameter("s", terms.joinToString(" "))
            addQueryParameter("page", page.toString())
        }.build()

        val data = client.get(url).parseAs<Books>()
        return MangasPage(
            data.entries.map { it.toSManga(trimTitlePref()) },
            data.page * data.limit < data.total,
        )
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/books/tags/filters").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<FilterDto>>().orEmpty()
        val excluded = excludeTagsPref()

        val filters = buildList {
            add(SortFilter())
            add(CategoryFilter())
            add(Filter.Separator())

            if (tags.isNotEmpty()) {
                add(TagFilter("Tags", tags.filter { it.namespace == 0 }, excluded))
                add(TagFilter("Female Tags", tags.filter { it.namespace == 9 }, excluded))
                add(TagFilter("Male Tags", tags.filter { it.namespace == 8 }, excluded))
                add(TagFilter("Artists", tags.filter { it.namespace == 1 }, excluded))
                add(TagFilter("Circles", tags.filter { it.namespace == 2 }, excluded))
                add(TagFilter("Parodies", tags.filter { it.namespace == 3 }, excluded))
                add(TagFilter("Mixed", tags.filter { it.namespace == 10 }, excluded))
                add(TagFilter("Other", tags.filter { it.namespace == 12 }, excluded))
                add(TagIncludeCondition())
                add(TagExcludeCondition())
                add(Filter.Separator())
            }

            add(Filter.Header("Separate tags with commas (,)"))
            add(Filter.Header("Prepend with dash (-) to exclude"))
            add(TextFilter("Magazines", "magazine"))
            add(TextFilter("Publishers", "publisher"))
            add(TextFilter("Characters", "character"))
            add(TextFilter("Cosplayers", "cosplayer"))
            add(Filter.Header("Filter by pages, for example: (>20)"))
            add(TextFilter("Pages", "pages"))
        }

        return FilterList(filters)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = client.get("$apiUrl/books/detail/${manga.url}").parseAs<MangaDetail>()

        return SMangaUpdate(
            data.toSManga(trimTitlePref()),
            listOf(data.toSChapter()),
        )
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/g/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/g/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (chapterId, chapterKey) = chapter.url.split("/", limit = 2)

        return withClearance { crt ->
            val data = client.post("$apiUrl/books/detail/$chapterId/$chapterKey?crt=$crt", RequestBody.EMPTY)
                .parseAs<MangaData>().data

            val selected = data.getBestQuality(qualityPref()) ?: return@withClearance emptyList()

            val images = client.get(
                "$apiUrl/books/data/$chapterId/$chapterKey/${selected.id}/${selected.key}/${selected.quality}?crt=$crt",
            ).parseAs<ImagesInfo>()

            images.entries.mapIndexed { index, image ->
                Page(index, imageUrl = "${images.base}/${image.path}?w=${selected.quality}")
            }
        }
    }

    private suspend fun <T> withClearance(block: suspend (crt: String) -> T): T {
        var crt = getClearance()
        return try {
            block(crt)
        } catch (e: Exception) {
            if (e.is403()) {
                crt = getClearance(failedToken = crt)
                block(crt)
            } else {
                throw e
            }
        }
    }

    private fun Exception.is403(): Boolean = this is HttpException && message?.contains("403") == true

    private var clearance: String?
        get() = preferences.getString("clearance_cache", null)
        set(value) {
            if (value == null) {
                preferences.edit().remove("clearance_cache").apply()
            } else {
                preferences.edit().putString("clearance_cache", value).apply()
            }
        }
    private val mutex = Mutex()

    private suspend fun getClearance(failedToken: String? = null): String = mutex.withLock {
        if (failedToken != null && clearance == failedToken) {
            clearance = null
        }

        clearance?.takeIf { it != failedToken }?.also { return it }

        if (failedToken == null) {
            runCatching { getLocalStorage(baseUrl, "clearance") }.getOrNull()?.also {
                clearance = it
                return@withLock it
            }
        }

        var captcha: CaptchaDialog? = null
        var cssHeight = 65

        val challenge = try {
            runWebView(2.minutes) {
                userAgent = headers["User-Agent"]!!

                jsBridge("turnstileToken") { resolve(it) }
                jsBridge("turnstileError") { reject(Exception(it)) }

                jsBridge("turnstileReady") {
                    evaluateJs(
                        """
                        turnstile.render("#challenge", {
                            sitekey: "0x4AAAAAAA1gtfQl-5lpZVcM",
                            appearance: "interaction-only",
                            callback: token => window.turnstileToken.post(token),
                            "error-callback": error => window.turnstileError.post(error || "error"),
                            "expired-callback": () => window.turnstileError.post("expired"),
                            "before-interactive-callback": () => window.turnstileInteractive.post("interactive"),
                            "after-interactive-callback": () => window.turnstileInteractiveDone.post("done"),
                            "unsupported-callback": () => window.turnstileError.post("unsupported"),
                            "timeout-callback": () => window.turnstileError.post("timeout"),
                        });
                        """.trimIndent(),
                    )
                }

                jsBridge("turnstileResize") {
                    it.toIntOrNull()?.let { h ->
                        cssHeight = h
                        captcha?.resize(h)
                    }
                }

                jsBridge("turnstileInteractive") {
                    val activity = currentActivity?.get()
                    if (activity == null) {
                        reject(Exception("Captcha needs the app in the foreground"))
                        return@jsBridge
                    }
                    captcha = CaptchaDialog(activity, getWebView(), cssHeight) {
                        reject(Exception("Captcha cancelled"))
                    }.also { it.show() }
                }

                jsBridge("turnstileInteractiveDone") { captcha?.dismiss() }

                loadData(
                    baseUrl,
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <style>
                            html, body { margin: 0; background: transparent; }
                            body { display: flex; justify-content: center; align-items: flex-start; }
                            #challenge { align-self: flex-start; }
                        </style>
                    </head>
                    <body>
                        <div id="challenge"></div>
                        <script
                            src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"
                            onload="window.turnstileReady.post('ready')"
                            onerror="window.turnstileError.post(event.type)"></script>
                        <script>
                            const challenge = document.getElementById('challenge');
                            new ResizeObserver(() => {
                                const h = Math.ceil(challenge.getBoundingClientRect().height);
                                if (h > 0) window.turnstileResize.post(String(h));
                            }).observe(challenge);
                        </script>
                    </body>
                    </html>
                    """.trimIndent(),
                )
            }
        } finally {
            captcha?.dismiss()
        }

        val authHeaders = headersBuilder()
            .set("Accept", "*/*")
            .set("Accept-Language", "en-US,en;q=0.9")
            .set("Authorization", "Bearer $challenge")
            .set("host", "auth.$apiDomain")
            .set("Sec-Fetch-Dest", "empty")
            .set("Sec-Fetch-Mode", "cors")
            .set("Sec-Fetch-Site", "cross-site")
            .build()

        return client.post("$authUrl/clearance", authHeaders, RequestBody.EMPTY).body.string()
            .also { clearance = it }
    }

    private var currentActivity: WeakReference<Activity>? = null

    init {
        applicationContext.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                currentActivity = WeakReference(a)
            }
            override fun onActivityPaused(a: Activity) {
                if (currentActivity?.get() === a) currentActivity = null
            }
            override fun onActivityDestroyed(a: Activity) {
                if (currentActivity?.get() === a) currentActivity = null
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        })
    }

    private class CaptchaDialog(
        private val activity: Activity,
        private val webView: WebView,
        initialCssHeight: Int,
        private val onCancel: () -> Unit,
    ) {
        private var dialog: AlertDialog? = null
        private var holder: FrameLayout? = null
        private var closing = false
        private val initialHeight = initialCssHeight.coerceIn(65, 400)

        fun show() = activity.runOnUiThread {
            if (dialog != null || closing) return@runOnUiThread

            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.setBackgroundColor(Color.TRANSPARENT)

            val frame = FrameLayout(activity).apply {
                setPadding(dp(8), dp(8), dp(8), dp(16))
                addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, dp(initialHeight)))
            }
            holder = frame

            dialog = AlertDialog.Builder(activity)
                .setTitle("Captcha Required!")
                .setView(frame)
                .setOnDismissListener {
                    holder?.removeView(webView)
                    holder = null
                    dialog = null
                    if (!closing) onCancel()
                }
                .show()
        }

        fun resize(cssPx: Int) = activity.runOnUiThread {
            webView.layoutParams = webView.layoutParams?.apply {
                height = dp(cssPx.coerceIn(65, 400))
            }
        }

        fun dismiss() = activity.runOnUiThread {
            closing = true
            dialog?.takeIf { it.isShowing }?.dismiss()
        }

        private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = runCatching {
        val token = clearance ?: return emptyList() // no need to load webview for related
        val data = client.post("$apiUrl/books/detail/${manga.url}?crt=$token", RequestBody.EMPTY)
            .parseAs<MangaData>()

        data.similar.map { it.toSManga(trimTitlePref()) }
    }.getOrDefault(emptyList())

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_IMAGERES
            title = "Image Resolution"
            entries = arrayOf("780x", "980x", "1280x", "1600x", "Original")
            entryValues = arrayOf("780", "980", "1280", "1600", "0")
            summary = "%s"
            setDefaultValue("1280")
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_REM_ADD
            title = "Remove additional information in title"
            summary = "Remove anything in brackets from manga titles.\n" +
                "Reload manga to apply changes to loaded manga."
            setDefaultValue(false)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_EXCLUDE_TAGS
            title = "Tags to exclude from browse/search"
            summary = "Separate tags with commas (,).\n" +
                "Excluding: ${excludeTagsPref().joinToString(", ")}"
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_IMAGERES = "pref_image_quality"
        private const val PREF_REM_ADD = "pref_remove_additional"
        private const val PREF_EXCLUDE_TAGS = "pref_exclude_tags"
    }
}
