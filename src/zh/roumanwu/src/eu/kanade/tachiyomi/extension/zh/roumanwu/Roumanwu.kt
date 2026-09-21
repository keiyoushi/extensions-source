package eu.kanade.tachiyomi.extension.zh.roumanwu

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class Roumanwu :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ScrambledImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/books".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .build()
        return parseMangaList(client.get(url).asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/home").asJsoup()
        return parseHomePage(document, Regex("最近更新"))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", query)
                .addQueryParameter("page", (page - 1).toString())
                .build()
        } else {
            "$baseUrl/books".toHttpUrl().newBuilder()
                .addQueryParameter("page", (page - 1).toString())
                .apply {
                    when (filters.firstInstance<StatusFilter>().state) {
                        1 -> addQueryParameter("continued", "true")
                        2 -> addQueryParameter("continued", "false")
                    }
                }
                .build()
        }
        return parseMangaList(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "books") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return parseMangaDetails(client.get("$baseUrl/books/$id").asJsoup())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = client.get(getChapterUrl(chapter)).use { it.body.string() }

        val fromNextJs = body.asJsoup(baseUrl).extractNextJs<ChapterPages>()?.toPageList().orEmpty()
        if (fromNextJs.isNotEmpty()) return fromNextJs

        // TanStack hydration: imagePaths:$R[n]=["https://...", ...]
        val marker = body.indexOf("imagePaths:")
        val arrayStart = if (marker >= 0) body.indexOf("=[", marker) + 1 else -1
        val arrayEnd = if (arrayStart > 0) body.indexOf(']', arrayStart) else -1
        if (arrayEnd < 0) return emptyList()
        return body.substring(arrayStart, arrayEnd + 1).parseAs<List<String>>()
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("提示：搜尋時篩選無效"),
        StatusFilter(),
    )

    private fun parseEntries(container: Element): List<SManga> = container.select("a.site-comic").map {
        SManga.create().apply {
            title = it.selectFirst("h3")!!.text()
            url = it.attr("href")
            thumbnail_url = it.selectFirst("img")!!.absUrl("src")
        }
    }

    private fun parseMangaList(document: Document): MangasPage = MangasPage(parseEntries(document), hasNextPage(document))

    // 页码文案形如 "1 / 103"；末页「下一頁」会变成 disabled button
    private fun hasNextPage(document: Document): Boolean {
        val parts = document.selectFirst(".site-pagination-mobile")?.text()?.split('/').orEmpty()
        val current = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return false
        val total = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return false
        return current < total
    }

    private fun parseHomePage(document: Document, sections: Regex): MangasPage {
        val entries = document.selectFirst("div.site-home")!!.children().flatMap { section ->
            val heading = section.selectFirst(".site-section-heading")?.text().orEmpty()
            if (heading.contains(sections)) {
                parseEntries(section)
            } else {
                emptyList()
            }
        }.distinctBy { it.url }

        return MangasPage(entries, false)
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst("div.site-book-info")!!

        setUrlWithoutDomain(document.location())
        title = info.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("img.site-detail-cover")!!.absUrl("src")

        val alias = info.selectFirst("p.site-book-alias")?.text()
        val synopsis = document.selectFirst("div.site-book-synopsis")?.text().orEmpty()
        description = buildString {
            if (!alias.isNullOrEmpty() && alias != title) {
                append("別名: ")
                append(alias)
                append("\n\n")
            }
            append(synopsis)
        }

        val data = info.select("dl.site-book-data dt").associate { dt ->
            dt.text() to dt.nextElementSibling()?.text().orEmpty()
        }
        author = data["作者"]
        status = when {
            data["狀態"]?.startsWith("連載中") == true -> SManga.ONGOING
            data["狀態"]?.startsWith("已完結") == true -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val genres = ArrayList<String>()
        data["地區"]?.takeIf { it.isNotEmpty() }?.let { genres.add(it) }
        info.selectFirst("p.site-eyebrow")?.text()
            ?.substringBefore("/")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { genres.add(it) }
        genre = genres.joinToString()
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val chapters = document.select("a.site-chapter-link").map {
            SChapter.create().apply {
                url = it.attr("href")
                name = it.selectFirst("span")!!.attr("title")
            }
        }.asReversed()
        if (chapters.isNotEmpty()) {
            val date = DATE_FORMAT.tryParseDate(
                document.selectFirst("dl.site-book-data dt:contains(更新) + dd")?.text(),
                ZoneId.of("Asia/Taipei"),
            )
            if (date != 0L) {
                chapters[0].date_upload = date
            }
        }
        return chapters
    }

    private class StatusFilter : Filter.Select<String>("狀態", arrayOf("全部", "連載中", "已完結"))

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // 框架生成类会在调用本方法【之前】通过 CustomUrlPreferences 在 screen 上添加一个
        // 「自定义基础 URL」输入框（key = overrideBaseUrl，baseUrl 始终读取该值）。
        // 由于 extensions-lib 的 PreferenceScreen 是精简版，无法隐藏该输入框，
        // 因此我们把「内置镜像站点下拉」「使用自定义域名开关」「框架自定义输入框」三者统一到
        // 同一个 overrideBaseUrl 上：
        //   - 开关关：baseUrl = 内置镜像下拉选中的域名
        //   - 开关开：baseUrl = 用户在「自定义基础 URL」输入框填写的域名
        // 每次打开设置页同步一次，确保状态一致。
        val useCustom = preferences.getBoolean(USE_CUSTOM_KEY, false)
        val mirrorIndex = getMirrorIndex(preferences)
        syncBaseUrl(useCustom, mirrorIndex)

        // 内置镜像站点下拉：官方公布的两个内置域名，二选一。
        // 精简版 PreferenceScreen 不支持 isEnabled/findPreference，
        // 因此在「使用自定义域名」开启时仅以 summary 提示「已禁用」，并在变更回调中忽略修改。
        val mirrorPref = ListPreference(screen.context).apply {
            key = MIRROR_INDEX_KEY
            title = "常用镜像站点"
            summary = if (useCustom) MIRROR_DISABLED_SUMMARY else BUILTIN_MIRRORS[mirrorIndex]
            entries = BUILTIN_MIRRORS.toTypedArray()
            entryValues = BUILTIN_MIRRORS.indices.map { it.toString() }.toTypedArray()
            setDefaultValue(DEFAULT_MIRROR_INDEX.toString())
            setOnPreferenceChangeListener { preference, newValue ->
                if (preferences.getBoolean(USE_CUSTOM_KEY, false)) {
                    // 自定义模式：本下拉不生效，恢复原 summary 并不写入
                    (preference as ListPreference).summary = MIRROR_DISABLED_SUMMARY
                    return@setOnPreferenceChangeListener true
                }
                val index = (newValue as String).toIntOrNull() ?: DEFAULT_MIRROR_INDEX
                (preference as ListPreference).summary = BUILTIN_MIRRORS[index]
                putMirrorIndex(preferences, index)
                syncBaseUrl(false, index)
                true
            }
        }
        screen.addPreference(mirrorPref)

        // 开关：是否使用自定义域名。关闭时回退到内置镜像，开启后「自定义基础 URL」输入框生效。
        SwitchPreferenceCompat(screen.context).apply {
            key = USE_CUSTOM_KEY
            title = "使用自定义域名"
            summary = buildSwitchSummary(useCustom)
            setDefaultValue(false)
            setOnPreferenceChangeListener { preference, newValue ->
                val enabled = newValue as Boolean
                // 开关切换时更新镜像下拉的可选状态提示，并把选中结果写入 overrideBaseUrl
                val idx = getMirrorIndex(preferences)
                mirrorPref.summary = if (enabled) MIRROR_DISABLED_SUMMARY else BUILTIN_MIRRORS[idx]
                syncBaseUrl(enabled, idx)
                (preference as SwitchPreferenceCompat).summary = buildSwitchSummary(enabled)
                true
            }
        }.also(screen::addPreference)
    }

    // 把当前最终生效的域名写入 overrideBaseUrl，供框架 baseUrl 读取。
    private fun syncBaseUrl(useCustom: Boolean, mirrorIndex: Int) {
        val url = if (useCustom) {
            preferences.getString(CUSTOM_BASE_URL_KEY, DEFAULT_BASE_URL)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        } else {
            BUILTIN_MIRRORS[mirrorIndex]
        }
        preferences.edit().putString(CUSTOM_BASE_URL_KEY, url).apply()
    }

    private fun buildSwitchSummary(useCustom: Boolean): String {
        val currentBaseUrl = preferences.getString(CUSTOM_BASE_URL_KEY, DEFAULT_BASE_URL)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        return if (useCustom) {
            "已开启：当前使用自定义域名 $currentBaseUrl（在下方「自定义基础 URL」输入框修改）"
        } else {
            val mirrorIndex = getMirrorIndex(preferences)
            "已关闭：当前使用内置域名 ${BUILTIN_MIRRORS[mirrorIndex]}"
        }
    }

    companion object {
        // keiyoushi.source.CustomUrlPreferences 内部的 baseUrl key
        private const val CUSTOM_BASE_URL_KEY = "overrideBaseUrl"

        // 本扩展「是否使用自定义域名」开关的独立 key
        private const val USE_CUSTOM_KEY = "roumanwu_use_custom"

        // 内置镜像下拉选中索引 key（存字符串，避免与旧版本残留的 String/Int 类型冲突导致崩溃）
        private const val MIRROR_INDEX_KEY = "roumanwu_mirror_index_v2"

        // 从 SharedPreferences 安全读取镜像索引：先以 String 读取，toIntOrNull 兜底，杜绝类型不匹配崩溃
        private fun getMirrorIndex(prefs: SharedPreferences): Int {
            val raw = prefs.getString(MIRROR_INDEX_KEY, null)
            return raw?.toIntOrNull() ?: DEFAULT_MIRROR_INDEX
        }

        private fun putMirrorIndex(prefs: SharedPreferences, index: Int) {
            prefs.edit().putString(MIRROR_INDEX_KEY, index.toString()).apply()
        }

        // 官方公布的内置域名（常用镜像站点），需与 deeplink host 保持一致
        private val BUILTIN_MIRRORS = listOf(
            "https://rouman5.com",
            "https://roum29.xyz",
        )
        private const val DEFAULT_MIRROR_INDEX = 0

        // 「使用自定义域名」开启时，镜像下拉的提示文案
        private const val MIRROR_DISABLED_SUMMARY = "（已禁用：当前使用自定义域名）"

        // 内置默认域名（与 build.gradle.kts 中 baseUrl.custom(...) 保持一致）
        private const val DEFAULT_BASE_URL = "https://rouman5.com"

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy")
    }
}
