package eu.kanade.tachiyomi.extension.en.alphamanga

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
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class AlphaManga :
    KeiSource(),
    ConfigurableSource {
    override val supportsLatest = false
    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.value
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value
        val url = "$baseUrl/manga/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("progress", status)
            .addQueryParameter("genre", genre)
            .addQueryParameter("page", page.toString())
            .build()
        val result = client.get(url).parseAs<SearchResponse>()
        val mangas = result.data.map { it.toSManga() }
        return MangasPage(mangas, result.hasMore)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val mangas = async {
            if (!fetchDetails) return@async manga
            val document = client.get(getMangaUrl(manga), desktopHeaders).asJsoup()
            SManga.create().apply {
                title = document.selectFirst("h1.c-h1")!!.text()
                author = document.select("h3.p-manga-detail__about-label")
                    .filter { it.text().contains("Author") }
                    .joinToString { it.text().substringBeforeLast("/").trim() }
                artist = document.select("h3.p-manga-detail__about-label")
                    .filter { it.text().contains("Illustrator") }
                    .joinToString { it.text().substringBeforeLast("/").trim() }
                description = document.selectFirst("p.p-manga-detail__about-overview-text")?.text()
                genre = document.select(".p-manga-detail__tags .c-tag").joinToString { it.text() }
                status = when (document.selectFirst(".p-manga-detail__status p")?.text()) {
                    "Ongoing" -> SManga.ONGOING
                    "Completed" -> SManga.COMPLETED
                    "Suspended" -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }
                thumbnail_url = document.selectFirst("img.p-manga-detail__banner-image")?.absUrl("src")
            }
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters
            client.get("$baseUrl/manga/${manga.url}/episodes.json").parseAs<ChapterResponse>().episodes
                .filter { !hideLocked || !it.isLocked }
                .map { it.toSChapter(manga.url) }
        }

        SMangaUpdate(
            mangas.await(),
            chapterList.await(),
        )
    }

    // load desktop selectors
    private val desktopHeaders get() = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36")
        .build()

    // force mobile ua for high resolution images
    private val mobileHeaders get() = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36")
        .build()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), mobileHeaders).asJsoup()
        val manga = document.selectFirst("viewer-manga-vertical")?.attr("v-bind:pages") ?: throw Exception("Log in via WebView and rent or purchase this chapter to read.")
        val pages = manga.parseAs<List<String>>()
        return pages.filterNot { it == "first" || it == "last" }.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        StatusFilter(),
        GenreFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.memo["titleId"]!!.string}/${chapter.url}?mode=vertical"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
