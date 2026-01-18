package eu.kanade.tachiyomi.extension.all.xkcd

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

open class Xkcd(
    final override val baseUrl: String,
    final override val lang: String,
) : ConfigurableSource, HttpSource() {
    final override val name = "xkcd"

    final override val supportsLatest = false
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(TextInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (url.host != "thumbnail") return@addInterceptor chain.proceed(request)

            val image = this::class.java
                .getResourceAsStream("/assets/thumbnail.png")!!
                .readBytes()
            val responseBody = image.toResponseBody("image/png".toMediaType())
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody)
                .build()
        }
        .build()

    protected open val archive = "/archive"

    protected open val creator = "Randall Munroe"

    protected open val synopsis =
        "A webcomic of romance, sarcasm, math and language."

    protected open val interactiveText =
        "To experience the interactive version of this comic, open it in WebView/browser."

    protected open val chapterListSelector = "#middleContainer > a"

    protected open val imageSelector = "#comic > img"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    protected open fun String.timestamp(): Long {
        // normalize dates like "2022-2-2" to "2022-02-02"
        val normalized = this.split("-").let { parts ->
            "${parts[0]}-${parts[1].padStart(2, '0')}-${parts[2].padStart(2, '0')}"
        }
        return dateFormat.tryParse(normalized)
    }

    val chapterTitleFormatter: (Int, String) -> String = { number, text -> "$number: $text" }

    // Preferences

    private val preferences: SharedPreferences by getPreferencesLazy()

    companion object {
        private const val CACHE_EXPIRY_MS = 60 * 60 * 1000L // 1 hour
        private const val ENGLISH_BASE_URL = "https://xkcd.com"

        enum class OrganizationMethod(val key: String, val displayName: String) {
            SINGLE("SINGLE", "Single manga (all comics)"),
            BY_YEAR("BY_YEAR", "By year"),
            BY_YEAR_MONTH("BY_YEAR_MONTH", "By year-month"),
            ;

            companion object {
                const val PREF_KEY = "organization_method"
                val defaultOption = SINGLE
                fun fromKey(key: String) = values().find { it.key == key } ?: defaultOption
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = OrganizationMethod.PREF_KEY
            title = "Organization Method"
            entries = OrganizationMethod.values().map { it.displayName }.toTypedArray()
            entryValues = OrganizationMethod.values().map { it.key }.toTypedArray()
            setDefaultValue(OrganizationMethod.defaultOption.key)
            summary = "Current: %s\n\n" +
                "Changing this will show comics grouped differently. " +
                "Your reading progress is tied to the organization method.\n\n" +
                "Note: You may need to exit and re-enter the extension for the manga list to update."
        }.let(screen::addPreference)
    }

    private fun getOrganizationMethod(): OrganizationMethod {
        val key = preferences.getString(OrganizationMethod.PREF_KEY, OrganizationMethod.defaultOption.key)!!
        return OrganizationMethod.fromKey(key)
    }

    // what key to use for grouping comics into mangas
    private fun getChapterToKey(): (SChapter) -> String {
        return when (getOrganizationMethod()) {
            OrganizationMethod.SINGLE -> { chapter -> "SINGLE" }
            OrganizationMethod.BY_YEAR -> { chapter ->
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(chapter.date_upload)
                date.split("-")[0] // Extract year
            }
            OrganizationMethod.BY_YEAR_MONTH -> { chapter ->
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(chapter.date_upload)
                val parts = date.split("-")
                "${parts[0]}-${parts[1].padStart(2, '0')}" // "2024-01"
            }
        }
    }

    private fun getKeyToTitleFormatter(): (String) -> String {
        return when (getOrganizationMethod()) {
            OrganizationMethod.SINGLE -> { key -> "xkcd" }
            OrganizationMethod.BY_YEAR -> { key -> "xkcd $key" }
            OrganizationMethod.BY_YEAR_MONTH -> { key -> "xkcd $key" }
        }
    }

    // Archive caching

    private var comicDateMapping: Map<Int, String>? = null // Comic number -> date mapping from English
    private var comicDateMappingTime: Long = 0
    private var allChaptersCache: List<SChapter>? = null // Full chapter list with dates
    private var allChaptersCacheTime: Long = 0

    // some translations don't provide dates for their comics, but we can look up the dates
    // (of english publication) given the comic number which is shared across translations
    protected fun getComicDateMappingFromEnglishArchive(): Map<Int, String> {
        val now = System.currentTimeMillis()
        if (comicDateMapping == null || now - comicDateMappingTime > CACHE_EXPIRY_MS) {
            comicDateMapping = try {
                val response = client.newCall(GET("$ENGLISH_BASE_URL/archive/", headers)).execute()
                val doc = response.asJsoup()
                doc.select("#middleContainer > a").associate { element ->
                    val number = element.attr("href").removeSurrounding("/").toInt()
                    val date = element.attr("title") // "2026-1-9" format
                    number to date
                }
            } catch (e: Exception) {
                emptyMap()
            }
            comicDateMappingTime = now
        }
        return comicDateMapping ?: emptyMap()
    }

    private fun getAllComicsAsChapters(): List<SChapter> {
        val now = System.currentTimeMillis()
        if (allChaptersCache == null || now - allChaptersCacheTime > CACHE_EXPIRY_MS) {
            val response = client.newCall(GET(baseUrl + archive, headers)).execute()
            allChaptersCache = chapterListParse(response)
            allChaptersCacheTime = now
        }
        return allChaptersCache!!
    }

    private fun getGroupedChapters(): Map<String, List<SChapter>> {
        return getAllComicsAsChapters().groupBy(getChapterToKey())
    }

    // organize chapters into mangas according to the chosen key
    private fun makeGroupedManga(page: Int, perPage: Int = 10): MangasPage {
        val groupedChapters = getGroupedChapters()
        val allKeys = groupedChapters.keys.sortedDescending() // Newest first

        val startIndex = (page - 1) * perPage
        val endIndex = minOf(startIndex + perPage, allKeys.size)
        val hasNextPage = endIndex < allKeys.size

        if (startIndex >= allKeys.size) {
            return MangasPage(emptyList(), false)
        }

        val pageKeys = allKeys.subList(startIndex, endIndex)
        val mangas = pageKeys.map { key ->
            val chapters = groupedChapters[key]!!
            val firstChapter = chapters.firstOrNull()
            SManga.create().apply {
                title = getKeyToTitleFormatter()(key)
                url = key

                // these constants can get overriden by translations
                artist = creator
                author = creator
                description = synopsis

                // TODO: this could theoretically be improved - xkcd doesn't retroactively
                // publish comics so if organizing by date, the manga for periods != current
                // period could be set to COMPLETED instead
                status = SManga.ONGOING

                // fetch real thumbnail
                thumbnail_url = if (firstChapter != null) {
                    fetchThumbnailUrlForChapter(firstChapter)
                } else {
                    "https://thumbnail/xkcd.png"
                }
                initialized = true
            }
        }

        return MangasPage(mangas, hasNextPage)
    }

    // overrides

    final override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        Observable.just(makeGroupedManga(page))

    final override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        Observable.just(MangasPage(emptyList(), false))

    final override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        Observable.just(manga)

    final override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        Observable.just(getGroupedChapters()[manga.url] ?: emptyList())

    // methods for translations to override

    // archiveResponse -> List<SChapter>
    override fun chapterListParse(response: Response) =
        response.asJsoup().select(chapterListSelector).map {
            SChapter.create().apply {
                // turn comic entry from archive entry into SChapter
                url = it.attr("href")
                val comicNumber = url.removeSurrounding("/").toInt()
                name = chapterTitleFormatter(comicNumber, it.text())
                chapter_number = comicNumber.toFloat()
                date_upload = it.attr("title").timestamp()
            }
        }

    override fun pageListParse(response: Response): List<Page> {
        // if the img tag is empty or has siblings then it is an interactive comic
        val img = response.asJsoup().selectFirst(imageSelector)?.takeIf {
            it.nextElementSibling() == null
        } ?: error(interactiveText)

        // if an HD image is available it'll be the srcset attribute
        val image = when {
            !img.hasAttr("srcset") -> img.attr("abs:src")
            else -> img.attr("abs:srcset").substringBefore(' ')
        }

        // create a text image for the alt text
        val text = TextInterceptorHelper.createUrl(img.attr("alt"), img.attr("title"))

        return listOf(Page(0, "", image), Page(1, "", text))
    }

    protected open fun extractImageFromContainer(container: Element): Element? {
        return container
    }

    protected open val defaultFallbackThumbnail = "https://thumbnail/xkcd.png"

    protected open fun fetchThumbnailUrlForChapter(chapter: SChapter): String {
        return try {
            val response = client.newCall(GET(baseUrl + chapter.url, headers)).execute()
            if (!response.isSuccessful) {
                return defaultFallbackThumbnail
            }

            val doc = response.asJsoup()
            val container = doc.selectFirst(imageSelector)

            val img = container?.let { extractImageFromContainer(it) }
                ?: doc.selectFirst("img[alt]") // Fallback to any img with alt text

            val url = img?.let {
                when {
                    it.hasAttr("srcset") -> it.attr("abs:srcset").substringBefore(' ')
                    it.hasAttr("src") -> it.attr("abs:src")
                    else -> null
                }
            }

            url?.takeIf { it.isNotBlank() && !it.contains("thumbnail") } ?: defaultFallbackThumbnail
        } catch (e: Exception) {
            defaultFallbackThumbnail
        }
    }

    final override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()

    final override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException()

    final override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException()

    final override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException()

    final override fun popularMangaParse(response: Response) =
        throw UnsupportedOperationException()

    final override fun popularMangaRequest(page: Int) =
        throw UnsupportedOperationException()

    final override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException()

    final override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException()
}
