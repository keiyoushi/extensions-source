package eu.kanade.tachiyomi.extension.ja.comicfesta

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.clipstudioreader.ClipStudioReader
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class ComicFesta :
    ClipStudioReader(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()
    private val rscHeaders get() = headersBuilder()
        .add("rsc", "1")
        .build()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addClipStudioInterceptors()
        .addCookie(listOf("checked_age" to "1", "sp_display" to "1", "cf_checked_age_guest" to "1", "cf_checked_age" to "1"))

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/sales_rankings/monthly_general".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val result = client.get(url, rscHeaders).extractNextJs<RankingResponse>()
        val mangas = result?.titles.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, hasNextPage = page != 2)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/titles".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "release")
            .addQueryParameter("search_form[other_item][]", "new")
            .build()
        return parseTitleList(client.get(url).asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/titles".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("search_form[keyword]", query)
            .addQueryParameter("search", query)
            .addQueryParameter("commit", "search")
            .build()
        return parseTitleList(client.get(url).asJsoup())
    }

    private fun parseTitleList(document: Document): MangasPage {
        val mangas = document.select("div.list-detail-box").map {
            SManga.create().apply {
                title = it.selectFirst("div.title-box")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
                setUrlWithoutDomain(it.selectFirst(".list-left-box a")!!.absUrl("href").toHttpUrl().pathSegments.last())
            }
        }
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "titles") return null
        val id = url.pathSegments.getOrNull(1) ?: return null

        return parseDetails(client.get("$baseUrl/titles/$id").asJsoup()).apply { this.url = id }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // no redirect for r18 content with rsc, so details and chapters are read from the same page
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapterList = document.extractNextJs<ChapterListResponse>()
            ?.toSChapterList(hideLocked)
            .orEmpty()
            .reversed()

        return SMangaUpdate(parseDetails(document), chapterList)
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1[class*='titleName']")!!.text()
        author = document.select("a[href*='/authors/']").joinToString { it.text() }
        description = document.selectFirst("div[class*='description']")?.text()
        genre = document.select("[class*='outlineTag'] a").joinToString { it.text() }
        thumbnail_url = document.selectFirst("img[class*='thumbnail']")?.absUrl("src")
        val statusText = document.select("[class*='latest-package-num-display']").text()
        status = if (statusText.contains("完結")) SManga.COMPLETED else SManga.ONGOING
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/titles/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/volumes/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter), ensureSuccess = false)
        val landingPath = response.request.url.pathSegments.first()
        if (landingPath == "entry" || landingPath == "error") {
            response.close()
            throw Exception("Log in via WebView and purchase this product to read.")
        }
        return pageListParse(response)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
