package eu.kanade.tachiyomi.extension.zh.manwa

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
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
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class Manwa :
    KeiSource(),
    ConfigurableSource {
    private val preferences: SharedPreferences = getPreferences()

    // mirror list is fetched at runtime (see UpdateMirror), so the DSL mirrors mode can't be used
    override val baseUrl: String = getTargetUrl()

    private fun getTargetUrl(): String {
        val url = preferences.getString(APP_CUSTOMIZATION_URL_KEY, "")!!
        if (url.isNotBlank()) {
            return url
        }
        return preferences.getString(MIRROR_KEY, MIRROR_ENTRIES[0])!!
    }

    private val rewriteOctetStream: Interceptor = Interceptor { chain ->
        val originalResponse: Response = chain.proceed(chain.request())
        if (originalResponse.request.url.toString().endsWith("?v=20220724")) {
            // Decrypt images in mangas
            val key = "my2ecret782ecret".toByteArray()
            val aesKey = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/CBC/NOPADDING")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(key))
            val result = originalResponse.body.source().cipherSource(cipher).buffer()
            val newBody = result.asResponseBody("image/webp".toMediaTypeOrNull())
            originalResponse.newBuilder()
                .body(newBody)
                .build()
        } else {
            originalResponse
        }
    }

    private val updateMirror: Interceptor = UpdateMirror(baseUrl, preferences)

    private val imageSource: Interceptor = ImageSource(baseUrl, preferences)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addNetworkInterceptor(rewriteOctetStream)
        .addInterceptor(imageSource)
        .addInterceptor(updateMirror)

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/rank").asJsoup()
        val mangas = document.select("#rankList_2 > a").map { element ->
            SManga.create().apply {
                title = element.attr("title")
                url = element.attr("href")
                thumbnail_url = element.selectFirst("img")?.attr("abs:data-original")
            }
        }
        return MangasPage(mangas, false)
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val currentOffset = page * 15 - 15
        val responseData = client.get("$baseUrl/getUpdate?page=$currentOffset&date=").parseAs<LatestUpdatesDto>()

        // Get image host
        val document = client.get("$baseUrl/update${preferences.getString(IMAGE_HOST_KEY, "")}").asJsoup()
        val imgHost = document.selectFirst(".manga-list-2-cover-img")?.attr(":src")?.drop(1)?.substringBefore("'") ?: ""

        val mangas = responseData.books.map { it.toSManga(imgHost) }

        return MangasPage(mangas, responseData.total > currentOffset + 15)
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val isBookList = query == "" || query.contains("-")
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (!isBookList) {
                encodedPath("/search")
                addQueryParameter("keyword", query)
            } else {
                encodedPath("/booklist")
                filters.forEach { filter ->
                    when (filter) {
                        is UriPartFilter -> filter.setParamPair(this)
                        is TagCheckBoxFilterGroup -> filter.setParamPair(this)
                        else -> {}
                    }
                }
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        val document = client.get(url).asJsoup()
        val mangas = if (isBookList) {
            document.select("ul.manga-list-2 > li").map { li ->
                SManga.create().apply {
                    title = li.selectFirst("p.manga-list-2-title")?.text() ?: ""
                    setUrlWithoutDomain(li.selectFirst("a")!!.absUrl("href"))
                    thumbnail_url = li.selectFirst("img")?.attr("abs:src")
                }
            }
        } else {
            document.select("ul.book-list > li").map { li ->
                SManga.create().apply {
                    title = li.selectFirst("p.book-list-info-title")?.text() ?: ""
                    setUrlWithoutDomain(li.selectFirst("a")!!.absUrl("href"))
                    thumbnail_url = li.selectFirst("img")?.attr("abs:data-original")
                }
            }
        }
        val next = document.select("ul.pagination2 > li").lastOrNull()?.text() == "下一页"

        return MangasPage(mangas, next)
    }

    // Details & Chapters
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst(".detail-main-info-title")?.text() ?: ""
            thumbnail_url = document.selectFirst("div.detail-main-cover > img")?.attr("abs:data-original")
            author = document.select("p.detail-main-info-author > span.detail-main-info-value > a").text()
            artist = author
            status = when (document.select("p.detail-main-info-author:contains(更新状态) > span.detail-main-info-value").text()) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = document.select("div.detail-main-info-class > a.info-tag").eachText().joinToString(", ")
            description = document.selectFirst("#detail > p.detail-desc")?.text()
        }
        val chapterList = document.select("ul#detail-list-select > li > a").map { element ->
            SChapter.create().apply {
                url = element.attr("href")
                name = element.text()
            }
        }.reversed()
        // The site only shows the last update date of the whole manga, so it's put on the newest chapter
        chapterList.firstOrNull()?.date_upload = DATE_FORMAT.tryParseDate(
            document.selectFirst(".detail-list-title-3")?.text()?.substringBefore("更新"),
        )
        return SMangaUpdate(details, chapterList)
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        client.get("$baseUrl/static/images/pv.gif", ensureSuccess = false).close()
        val document = client.get("$baseUrl${chapter.url}${preferences.getString(IMAGE_HOST_KEY, "")}").asJsoup()
        return document.select("#cp_img > div.img-content > img[data-r-src]").mapIndexed { index, it ->
            Page(index, imageUrl = it.attr("abs:data-r-src"))
        }
    }

    // Filters
    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/booklist").asJsoup()
        val tags = LinkedHashMap<String, String>()
        document.select("div.manga-filter-row.tags > a").forEach { a ->
            tags[a.text()] = a.attr("data-val")
        }
        if (tags.isEmpty()) {
            tags["全部"] = ""
        }
        return tags.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            EndFilter(),
            CGenderFilter(),
            AreaFilter(),
            SortFilter(),
        )
        data?.parseAs<LinkedHashMap<String, String>>()?.let {
            filters.add(TagCheckBoxFilterGroup("标签", it))
        }
        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MIRROR_KEY
            title = "使用镜像网址"

            val list: Array<String> = try {
                val urlList =
                    preferences.getString(APP_URL_LIST_PREF_KEY, "")!!.parseAs<ArrayList<String>>()
                urlList.add(0, MIRROR_ENTRIES[0])
                urlList.toTypedArray()
            } catch (e: Exception) {
                MIRROR_ENTRIES
            }

            entries = list
            entryValues = list
            setDefaultValue(list[0])
        }.let { screen.addPreference(it) }

        EditTextPreference(screen.context).apply {
            key = APP_CUSTOMIZATION_URL_KEY
            title = "自定义URL"
            summary = "指定访问的目标URL，优先级高于选择的镜像URL"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(APP_CUSTOMIZATION_URL_KEY, newValue as String).commit()
            }
        }.let { screen.addPreference(it) }

        ListPreference(screen.context).apply {
            key = IMAGE_HOST_KEY
            title = "图源"
            summary =
                "切换图源能使一些无法加载的图片进行优化加载，但对于已经缓存了章节图片信息的章节只是修改图源是不会重新加载的，你需要手动在应用设置里<清除章节缓存>"

            val list: Array<ImageSourceInfo> = try {
                preferences.getString(APP_IMAGE_SOURCE_LIST_KEY, "")!!
                    .parseAs<Array<ImageSourceInfo>>()
            } catch (_: Exception) {
                arrayOf(ImageSourceInfo("None", ""))
            }

            entries = list.map { it.name }.toTypedArray()
            entryValues = list.map { it.param }.toTypedArray()
            setDefaultValue(list[0].param)
        }.let { screen.addPreference(it) }

        EditTextPreference(screen.context).apply {
            key = APP_REDIRECT_URL_KEY
            title = "重定向URL"
            summary = "该URL期望能够获取动态的目标URL列表"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(APP_REDIRECT_URL_KEY, newValue as String).commit()
            }
        }.let { screen.addPreference(it) }
    }

    companion object {
        private const val MIRROR_KEY = "MIRROR"
        private val MIRROR_ENTRIES
            get() = arrayOf(
                "https://manwa.me",
                "https://manwass.cc",
                "https://manwatg.cc",
                "https://manwast.cc",
                "https://manwasy.cc",
            )
        private const val IMAGE_HOST_KEY = "IMG_HOST"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d", Locale.ROOT)
    }
}

const val APP_IMAGE_SOURCE_LIST_KEY = "APP_IMAGE_SOURCE_LIST_KEY"
const val APP_REDIRECT_URL_KEY = "APP_REDIRECT_URL_KEY"
const val APP_URL_LIST_PREF_KEY = "APP_URL_LIST_PREF_KEY"
const val APP_CUSTOMIZATION_URL_KEY = "APP_CUSTOMIZATION_URL_KEY"
