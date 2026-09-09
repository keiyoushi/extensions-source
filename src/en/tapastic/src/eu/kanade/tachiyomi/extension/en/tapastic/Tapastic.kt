package eu.kanade.tachiyomi.extension.en.tapastic

import android.content.SharedPreferences
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
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.IOException

@Source
abstract class Tapastic :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl = "https://story-api.${baseUrl.substringAfterLast("/")}"

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(TextInterceptor())
    }

    override fun Headers.Builder.configureHeaders() = apply {
        set("Referer", "https://m.tapas.io")
        set("User-Agent", USER_AGENT)
    }

    // ============================== Popular ===================================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/cosmos/api/v1/landing/ranking".toHttpUrl().newBuilder()
            .addQueryParameter("category_type", "COMIC")
            .addQueryParameter("subtab_id", "17")
            .addQueryParameter("size", "25")
            .addQueryParameter("page", (page - 1).toString())
            .build()

        return parseMangaList(client.get(url))
    }

    private fun parseMangaList(response: Response): MangasPage {
        val dto = response.parseAs<DataWrapper<WrapperContent>>()
        val mangas = dto.items.map(MangaDto::toSManga)
        return MangasPage(mangas, dto.hasNextPage())
    }

    // ============================== Latest ====================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/cosmos/api/v1/landing/genre".toHttpUrl().newBuilder()
            .addQueryParameter("category_type", "COMIC")
            .addQueryParameter("sort_option", "NEWEST_EPISODE")
            .addQueryParameter("subtab_id", "17")
            .addQueryParameter("pageSize", "25")
            .addQueryParameter("page", (page - 1).toString())
            .build()

        return parseMangaList(client.get(url))
    }

    // ============================== Search ====================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("pageNumber", page.toString())
            .addQueryParameter("q", query)
            .addQueryParameter("t", "COMICS")
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select(".search-item-wrap").map { element ->
            SManga.create().apply {
                title = element.select(".item__thumb img").firstOrNull()?.attr("alt") ?: element.select(".title-section .title a").text()
                thumbnail_url = element.select(".item__thumb img, .thumb-wrap img").attr("src")
                description = element.selectFirst(".desc.force.mbm")?.text()
                this.url = "/series/" + element.selectFirst(".item__thumb a, .title-section .title a")!!.attr("data-series-id")
            }
        }

        return MangasPage(mangas, hasNextPage = document.selectFirst("a[class*=paging__button--next]") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "series") {
            return null
        }

        val slug = url.pathSegments[1]
        val mangaUrl = if (slug.toIntOrNull() != null) {
            "/series/$slug"
        } else {
            val document = client.get(url).asJsoup()
            val id = document.selectFirst("meta[content^=tapastic://series]")
                ?.attr("content")
                ?.substringAfterLast('/')
                ?: return null
            "/series/$id"
        }

        val manga = SManga.create().apply {
            this.url = mangaUrl
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
                this.url = mangaUrl
            }
    }

    // ============================== Updates ===================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}/info"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val (manga, chapters) = coroutineScope {
            val mangaD = async { if (fetchDetails) getMangaDetails(manga) else manga }
            val chaptersD = async { if (fetchChapters) getChapterList(manga) else chapters }
            mangaD.await() to chaptersD.await()
        }

        return SMangaUpdate(manga, chapters)
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".info__right .title")!!.text()
            thumbnail_url = document.selectFirst(".thumb.js-thumbnail img")?.absUrl("src")
            description = buildString {
                append(document.selectFirst(".description__body")?.text())
                document.selectFirst(".colophon")?.text()?.let {
                    appendLine("\n\n$it")
                }
            }

            genre = document.select(".genre-btn").map { it.text() }
                .distinct()
                .joinToString()

            author = document.select(".creator-section .name").joinToString { it.text() }

            document.selectFirst(".schedule-ico:has(.sp-ico-updated-line-pwt) + .schedule-label")?.text()?.let {
                status = when {
                    it.contains("updates", ignoreCase = true) -> SManga.ONGOING
                    it.contains("completed", ignoreCase = true) -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
        }
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        var page = 1
        val chapters = mutableListOf<SChapter>()
        do {
            val url = "$baseUrl${manga.url}/episodes".toHttpUrl().newBuilder()
                .addQueryParameter("page", (page++).toString())
                .addQueryParameter("sort", "NEWEST")
                .addQueryParameter("since", System.currentTimeMillis().toString())
                .addQueryParameter("large", "true")
                .addQueryParameter("last_access", "0")
                .addQueryParameter("", "") // make the same request as the browser
                .build()
            val response = client.get(url)
            val dto = response.parseAs<DataWrapper<ChapterListDto>>()
            chapters += dto.data.episodes
                .filter { it.isPaywalledVisible() && it.isScheduledVisible() }
                .map(ChapterDto::toSChapter)
        } while (dto.data.hasNextPage())

        return chapters
    }

    private fun ChapterDto.isPaywalledVisible() = showLockedChapterPref || unlocked || free

    private fun ChapterDto.isScheduledVisible() = showScheduledChapterPrefer || !scheduled

    // ============================== Pages =====================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()

        val pages = document.select("img.content__img").mapIndexed { i, img ->
            Page(i, "", img.let { if (it.hasAttr("data-src")) it.attr("abs:data-src") else it.attr("abs:src") })
        }.toMutableList()

        if (showAuthorsNotesPref) {
            val episodeStory = document.select("p.js-episode-story").html()

            if (episodeStory.isNotEmpty()) {
                val creator = document.selectFirst("a.name.js-fb-tracking")!!.text()
                pages += Page(
                    index = pages.size,
                    imageUrl = TextInterceptorHelper.createUrl("Author's Notes from $creator", episodeStory),
                )
            }
        }

        return pages.takeIf { it.isNotEmpty() } ?: throw IOException("Chapter locked")
    }

    // ============================== Settings ==================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = LOCKED_CHAPTER_VISIBILITY_PREF
            title = "Show paywalled chapters"
            summary = buildString {
                append("Tapas requires login/payment for some chapters. Enable to always show paywalled chapters. ")
                append("Hiding chapters with paid access will also hide scheduled chapters.")
            }

            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                showLockedChapterPref = newValue as Boolean
                true
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SCHEDULED_CHAPTER_VISIBILITY_PREF
            title = "Show scheduled chapters"
            summary = "Scheduled chapters can be hidden from the chapter list by enabling this option."
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                showScheduledChapterPrefer = newValue as Boolean
                true
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_AUTHORS_NOTES_KEY
            title = "Show author's notes"
            summary = "Enable to see the author's notes at the end of chapters (if they're there)."
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                showAuthorsNotesPref = newValue as Boolean
                true
            }
        }.also(screen::addPreference)
    }

    private var showLockedChapterPref: Boolean get() =
        preferences.getBoolean(LOCKED_CHAPTER_VISIBILITY_PREF, true)
        set(value) {
            preferences.edit()
                .putBoolean(LOCKED_CHAPTER_VISIBILITY_PREF, value)
                .apply()
        }

    private var showScheduledChapterPrefer: Boolean
        get() = preferences.getBoolean(SCHEDULED_CHAPTER_VISIBILITY_PREF, true)
        set(value) {
            preferences.edit()
                .putBoolean(SCHEDULED_CHAPTER_VISIBILITY_PREF, value)
                .apply()
        }

    private var showAuthorsNotesPref: Boolean
        get() = preferences.getBoolean(SHOW_AUTHORS_NOTES_KEY, true)
        set(value) {
            preferences.edit()
                .putBoolean(SHOW_AUTHORS_NOTES_KEY, value)
                .apply()
        }
    companion object {
        private const val LOCKED_CHAPTER_VISIBILITY_PREF = "lockedChapterVisibilityPref"
        private const val SCHEDULED_CHAPTER_VISIBILITY_PREF = "scheduledChapterVisibilityPref"
        private const val SHOW_AUTHORS_NOTES_KEY = "showAuthorsNotes"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:105.0) Gecko/20100101 Firefox/105.0"
    }
}
