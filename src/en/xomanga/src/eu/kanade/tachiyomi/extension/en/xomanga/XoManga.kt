package eu.kanade.tachiyomi.extension.en.xomanga

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class XoManga :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val body = client.get("$baseUrl/our-works").body.string()
        val block = EXCLUSIVE_REGEX.find(body)?.groupValues?.get(1) ?: return MangasPage(emptyList(), false)
        val exclusiveTitles = QUOTED_REGEX.findAll(block).map { it.groupValues[1].lowercase().trim().replace(Regex("""\s+"""), " ") }.toSet()
        val index = client.get("$baseUrl/index.json").parseAs<IndexResponse>()
        val mangas = index.latest.filter { it.isExclusive(exclusiveTitles) }.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = client.get("$baseUrl/index.json").parseAs<IndexResponse>()
        val mangas = result.latest.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val result = client.get("$baseUrl/index.json").parseAs<IndexResponse>()
        val mangas = result.latest
            .filter { it.matchesQuery(query) }
            .map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = url.queryParameter("id") ?: return null
        val manga = SManga.create().apply {
            this.url = id
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
                this.url = id
            }
    }

    // =========================== Manga Updates ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get("$baseUrl/manga/${manga.url}/details.json").parseAs<DetailsResponse>()
        val hideVip = hideVIP()

        return SMangaUpdate(
            manga = details.toSManga(),
            chapters = details.chaptersList.mapNotNull { it.toSChapter(baseUrl, hideVip) },
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/details.html?id=${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val slug = url.pathSegments.first()
        val chapterNum = url.fragment
        val chapterUrl = "$baseUrl/reader.html".toHttpUrl().newBuilder()
            .addQueryParameter("id", slug)
            .addQueryParameter("ch", chapterNum)
            .build()
            .toString()
        return chapterUrl
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val slug = url.pathSegments.first()
        val chapterNum = url.fragment
        val images = client.get("$baseUrl/manga/$slug/chapters/$chapterNum.json")
            .parseAs<ImageResponse>().images

        return images.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    // ============================== Preferences ==============================
    private fun hideVIP(): Boolean = preferences.getBoolean(HIDE_LOCKED_CHAPTERS, true)
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_CHAPTERS
            title = HIDE_LOCKED_CHAPTERS_TITLE
            summaryOn = HIDE_LOCKED_CHAPTERS_SUM_ON
            summaryOff = HIDE_LOCKED_CHAPTERS_SUM_OFF
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    companion object {
        private val EXCLUSIVE_REGEX = Regex("""myExclusiveWorksTitles\s*=\s*\[([^]]+)]""", RegexOption.DOT_MATCHES_ALL)
        private val QUOTED_REGEX = Regex("""["']([^"'\n]+)["']""")
        const val HIDE_LOCKED_CHAPTERS = "hide_vip_chapters"
        const val HIDE_LOCKED_CHAPTERS_TITLE = "Hide VIP chapters"
        const val HIDE_LOCKED_CHAPTERS_SUM_ON = "Chapters will be hidden"
        const val HIDE_LOCKED_CHAPTERS_SUM_OFF = "Chapters will be marked with an icon: \uD83D\uDD12"
    }
}
