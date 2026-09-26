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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
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
                url = link.attr("href").substringAfterLast('/')
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
            this.url = id.toString()
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/c/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            manga = document.toSManga().apply { url = manga.url },
            chapters = if (fetchChapters) document.toSChapterList(chapters) else chapters,
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

    private suspend fun Document.toSChapterList(knownChapters: List<SChapter>): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF, false)
        // "Extra 01" etc. sit between numbered chapters. Number them right after the preceding chapter,
        // otherwise the app parses "Extra 01" as chapter 1
        var previousNumber = 0f
        var extrasSincePrevious = 0

        val chapters = select(".catalog-list .catalog-item").mapNotNull { element ->
            val link = element.selectFirst(".title a")!!
            val title = link.text()
            val number = CHAPTER_NUMBER_REGEX.matchEntire(title)?.groupValues?.get(1)?.toFloat()
            val chapterNumber = if (number != null) {
                previousNumber = number
                extrasSincePrevious = 0
                number
            } else {
                extrasSincePrevious++
                previousNumber + extrasSincePrevious / 100f
            }
            val locked = element.selectFirst(".icon-box img[src*=lock]") != null
            if (locked && hideLocked) return@mapNotNull null

            SChapter.create().apply {
                url = link.attr("href").substringAfter("/c/")
                name = if (locked) "🔒 $title" else title
                chapter_number = chapterNumber
            }
        }

        // Release dates are only shown on each chapter's own page, so only fetch them for chapters the app doesn't have yet
        val knownDates = knownChapters.associate { it.url to it.date_upload }
        val permits = Semaphore(5)
        coroutineScope {
            chapters.map { chapter ->
                async {
                    chapter.date_upload = knownDates[chapter.url] ?: permits.withPermit { fetchReleaseDate(chapter) }
                }
            }.awaitAll()
        }

        return chapters.asReversed()
    }

    private suspend fun fetchReleaseDate(chapter: SChapter): Long {
        // "Updated at 2025/5/12 0:00:00". The time is almost always midnight, so keep only the day,
        // which then shows as the same date as on the site in any timezone
        val date = client.get(getChapterUrl(chapter)).asJsoup()
            .selectFirst(".title-box .time-box span")?.text()
            ?.removePrefix("Updated at ")?.substringBefore(' ')

        return DATE_FORMAT.tryParseDate(date)
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/c/${chapter.url}"

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
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ENGLISH)
    }
}
