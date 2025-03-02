package eu.kanade.tachiyomi.extension.all.mangareaderto

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class MangaReader(
    language: Language,
) : MangaReader(
    "MangaReader",
    "https://mangareader.to",
    language.code,
),
    ConfigurableSource {

    override val client = super.client.newBuilder()
        .addInterceptor(ImageInterceptor)
        .build()

    private val preferences: SharedPreferences by getPreferencesLazy()

    // =============================== Search ===============================

    override fun searchMangaParse(response: Response): MangasPage {
        val mangasPage = super.searchMangaParse(response)
        var entries = mangasPage.mangas
        if (preferences.getBoolean(SHOW_VOLUME_PREF, false)) {
            entries = entries.flatMapTo(ArrayList(entries.size * 2)) { manga ->
                val volume = SManga.create().apply {
                    url = manga.url + VOLUME_URL_SUFFIX
                    title = VOLUME_TITLE_PREFIX + manga.title
                    thumbnail_url = manga.thumbnail_url
                }
                listOf(manga, volume)
            }
        }
        return MangasPage(entries, mangasPage.hasNextPage)
    }

    // ============================== Chapters ==============================

    private val volumeType = "vol"
    private val chapterType = "chap"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val path = manga.url
        val isVolume = path.endsWith(VOLUME_URL_SUFFIX)
        val type = if (isVolume) volumeType else chapterType

        val request = chapterListRequest(path.removeSuffix(VOLUME_URL_SUFFIX), type)
        val response = client.newCall(request).execute()

        return Observable.just(chapterListParse(response, isVolume))
    }

    private fun chapterListRequest(mangaUrl: String, type: String): Request {
        val id = mangaUrl.substringAfterLast('-')
        return GET("$baseUrl/ajax/manga/reading-list/$id?readingBy=$type", headers)
    }

    private fun chapterListParse(response: Response, isVolume: Boolean): List<SChapter> {
        val container = response.parseHtmlProperty().run {
            val type = if (isVolume) "volumes" else "chapters"
            selectFirst("#$lang-$type") ?: return emptyList()
        }
        return container.children().map { element ->
            chapterFromElement(element).apply {
                val dataId = url.substringAfterLast('#', "")
                if (dataId.isNotEmpty()) {
                    url = "${url.substringBeforeLast('#')}#${if (isVolume) volumeType else chapterType}/$dataId"
                }
            }
        }
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val typeAndId = chapter.url.substringAfterLast('#', "").ifEmpty {
            val document = client.newCall(GET(baseUrl + chapter.url, headers)).execute().asJsoup()
            val wrapper = document.selectFirst("#wrapper")!!
            wrapper.attr("data-reading-by") + '/' + wrapper.attr("data-reading-id")
        }

        val ajaxUrl = "$baseUrl/ajax/image/list/$typeAndId?quality=${preferences.quality}"
        return GET(ajaxUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pageDocument = response.parseHtmlProperty()

        return pageDocument.getElementsByClass("iv-card").mapIndexed { index, img ->
            val url = img.attr("data-url")
            val imageUrl = if (img.hasClass("shuffled")) "$url#${ImageInterceptor.SCRAMBLED}" else url
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferences(screen.context).forEach(screen::addPreference)
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_VOLUME_PREF
            title = "Show volume entries in search result"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    // ============================= Utilities ==============================

    companion object {
        private const val SHOW_VOLUME_PREF = "show_volume"

        private const val VOLUME_URL_FRAGMENT = "vol"
        private const val VOLUME_URL_SUFFIX = "#$VOLUME_URL_FRAGMENT"
        private const val VOLUME_TITLE_PREFIX = "[VOL] "
    }

    // ============================== Filters ===============================

    override fun getFilterList() = FilterList(
        Note,
        TypeFilter(),
        StatusFilter(),
        RatingFilter(),
        ScoreFilter(),
        StartDateFilter(),
        EndDateFilter(),
        getSortFilter(),
        GenreFilter(),
    )
}
