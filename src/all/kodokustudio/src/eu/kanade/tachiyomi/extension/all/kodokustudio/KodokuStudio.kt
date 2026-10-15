package eu.kanade.tachiyomi.extension.all.kodokustudio

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class KodokuStudio :
    KeiSource(),
    ConfigurableSource {

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage = coroutineScope {
        val series = async { client.get("$baseUrl/api/series").parseAs<List<SeriesDto>>() }
        val chapters = async { client.get("$baseUrl/api/series/$SLUG/chapters").parseAs<List<ChapterEntryDto>>() }
        val detail = series.await().first { it.slug == SLUG }
        val mangas = chapters.await().map { it.languageCode }.distinct()
            .sortedBy { LANG_ORDER.indexOf(it).takeIf { code -> code >= 0 } ?: Int.MAX_VALUE }
            .map { detail.toSManga(it) }
        MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = getPopularManga(page).mangas
            .filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.substringBefore('/')
        val lang = manga.url.substringAfter('/', "en")
        val details = async {
            if (fetchDetails) {
                client.get("$baseUrl/api/series/$slug").parseAs<SeriesDto>().toSManga(lang)
            } else {
                manga
            }
        }
        val chapterList = async {
            if (fetchChapters) {
                val showLocked = preferences.getBoolean(SHOW_LOCKED, false)
                client.get("$baseUrl/api/series/$slug/chapters")
                    .parseAs<List<ChapterEntryDto>>()
                    .filter { it.languageCode == lang && (showLocked || it.available) }
                    .map { it.toSChapter() }
                    .sortedByDescending { it.chapter_number }
            } else {
                chapters
            }
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl/api/series/$SLUG/chapters/${chapter.url}", ensureSuccess = false)
        if (response.code == 403) {
            response.close()
            return emptyList()
        }
        return response.parseAs<ChapterDetailDto>().toPages()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manhwa/$SLUG"

    override fun getChapterUrl(chapter: SChapter): String {
        val (number, lang) = chapter.url.split('/')
        return "$baseUrl/read/$SLUG/$lang/$number"
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        val first = segments.getOrNull(0)
        val slug = segments.getOrNull(1)?.takeIf { first == "manhwa" || first == "read" } ?: return null
        val lang = segments.getOrNull(2)?.takeIf { first == "read" } ?: "en"
        val response = client.get("$baseUrl/api/series/$slug", ensureSuccess = false)
        if (response.code == 404) {
            response.close()
            return null
        }
        return response.parseAs<SeriesDto>().toSManga(lang)
    }

    companion object {
        private const val SLUG = "reverend-insanity"
        private const val SHOW_LOCKED = "pref_show_locked"
        private val LANG_ORDER = listOf("en", "ar", "tr", "ru", "vi")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED
            title = "Show locked chapters"
            summary = "Locked chapters require Patreon access to read"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }
}
