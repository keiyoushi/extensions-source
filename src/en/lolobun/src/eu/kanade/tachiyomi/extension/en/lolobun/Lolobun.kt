package eu.kanade.tachiyomi.extension.en.lolobun

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
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Lolobun :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    // The homepage section is the site's only comic listing
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select(".section-item:has(a.name[href^=/c/])").map { element ->
            val link = element.selectFirst("a.name")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = element.selectFirst("img.cover")?.absUrl("src")
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)

        val url = "$baseUrl/ajax/Common.ashx".toHttpUrl().newBuilder()
            .addQueryParameter("op", "searchWorks")
            .addQueryParameter("q", query.trim())
            .addQueryParameter("type", "comic")
            .addQueryParameter("pi", (page - 1).toString())
            .build()
        val result = client.get(url).parseAs<ResponseDto<SearchDto>>().requireData()

        return MangasPage(result.items.map { it.toSManga() }, result.hasMore)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        if (url.pathSegments.getOrNull(0) != "c") return null
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null

        return client.get("$baseUrl/c/$id").asJsoup().toSManga().apply {
            this.url = "/c/$id"
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            manga = document.toSManga().apply { url = manga.url },
            chapters = document.toSChapterList(),
        )
    }

    private fun Document.toSManga() = SManga.create().apply {
        title = selectFirst(".header-item-info .name")!!.text()
        thumbnail_url = selectFirst("img.header-item-cover")?.absUrl("src")
        description = selectFirst("#comic-desc")?.wholeText()?.trim()

        // "Ongoing · Magic"
        val info = selectFirst(".header-item-info .info")?.text().orEmpty().split("·").map { it.trim() }
        status = when (info.first().lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = info.drop(1).joinToString().ifEmpty { null }
    }

    private fun Document.toSChapterList(): List<SChapter> {
        val items = select(".catalog-list .catalog-item")
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF, false)
        // The site only shows when the latest chapter was released
        val latestDate = DATE_FORMAT.tryParseDate(selectFirst(".lastest-update-time")?.text(), SITE_ZONE)
        // "Extra 01" etc. sit between numbered chapters. Number them after the preceding chapter,
        // otherwise the app parses "Extra 01" as chapter 1
        var previousNumber = 0f

        return items.mapIndexedNotNull { index, element ->
            val link = element.selectFirst(".title a")!!
            val title = link.text()
            val number = CHAPTER_NUMBER_REGEX.matchEntire(title)?.groupValues?.get(1)?.toFloat()
                ?.also { previousNumber = it }
                ?: (previousNumber + 0.5f)
            val locked = element.selectFirst(".icon-box img[src*=lock]") != null
            if (locked && hideLocked) return@mapIndexedNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = if (locked) "🔒 $title" else title
                chapter_number = number
                if (index == items.lastIndex) date_upload = latestDate
            }
        }.asReversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = FormBody.Builder()
            .add("chapId", chapter.url.substringAfterLast('/'))
            .build()

        // Locked chapters come back as an empty list
        return client.post("$baseUrl/ajax/comic.ashx?op=getChapterPic", body)
            .parseAs<ResponseDto<List<String>>>()
            .requireData()
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF
            title = "Hide locked chapters"
            summary = "Don't list paid chapters (🔒). Refresh a comic's chapter list to apply."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF = "hide_locked_chapters"
        private val CHAPTER_NUMBER_REGEX = Regex("""chapter\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH)
        private val SITE_ZONE = ZoneId.of("Asia/Shanghai")
    }
}
