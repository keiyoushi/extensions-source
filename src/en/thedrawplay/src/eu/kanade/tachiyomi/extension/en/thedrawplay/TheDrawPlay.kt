package eu.kanade.tachiyomi.extension.en.thedrawplay

import android.text.Html
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
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TheDrawPlay :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(TextInterceptor()).rateLimit(2)

    private fun comic() = SManga.create().apply {
        url = "/archive/"
        title = "The Draw Play"
        author = "Dave Rappoccio"
        artist = "Dave Rappoccio"
        description = "The Draw Play is a labor of love by a football fan who needs something to do during the offseason. " +
            "It exists as an outlet to make fun of and comment on the current state of the NFL and occasionally other things."
        status = SManga.ONGOING
        thumbnail_url = "$baseUrl/wp-content/uploads/2025/09/cropped-SiteLogo.png"
    }

    override suspend fun getPopularManga(page: Int) = MangasPage(listOf(comic()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = listOf(comic()).filter { manga ->
            listOfNotNull(manga.title, manga.author, manga.description).any { it.contains(query, ignoreCase = true) }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        if (url.pathSegments.first() !in listOf("comic", "archive")) return null
        return comic()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(comic(), chapters)

        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        var year = ""
        val chapterList = document.select("h3.year-title, tr:has(td.archive-title)").mapNotNull { element ->
            if (element.tagName() == "h3") {
                year = element.text()
                return@mapNotNull null
            }
            val link = element.selectFirst("td.archive-title > a")!!
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.text()
                date_upload = dateFormat.tryParseDate(element.selectFirst("td.archive-date")?.text()?.let { "$it $year" }, siteZone)
            }
        }
        return SMangaUpdate(comic(), chapterList.reversed())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val showNotes = preferences.getBoolean(SHOW_NOTES_KEY, false)
        val postTitle = document.selectFirst(".post-title")?.text()
        val imageUrls = buildList {
            document.select("#comic img").forEach { strip ->
                add(strip.absUrl("src"))
                // Strips without hover text carry the post title there
                strip.attrOrNull("title")?.takeIf { it != postTitle }?.let { caption ->
                    add(TextInterceptorHelper.createUrl("", caption.lines().joinToString("<br>") { Html.escapeHtml(it) }))
                }
            }
            val notes = StringBuilder()
            fun addNotes() {
                if (notes.isEmpty()) return
                add(TextInterceptorHelper.createUrl("", notes.toString()))
                notes.clear()
            }
            // Cartoon Bets and draft posts carry more panels in the entry body
            document.selectFirst(".post-content .entry")?.children()?.forEach { block ->
                val images = block.select("img")
                if (images.isNotEmpty()) {
                    addNotes()
                    images.forEach { add(it.absUrl("src")) }
                }
                if (showNotes && block.text().isNotBlank()) {
                    notes.append(block.clone().apply { select("img").remove() }.outerHtml())
                    if (notes.length > NOTES_PAGE_LENGTH) addNotes()
                }
            }
            addNotes()
        }
        return imageUrls.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private val preferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_NOTES_KEY
            title = "Show author's notes"
            summary = "Adds the text posted with each strip as pages"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }
}

private const val SHOW_NOTES_KEY = "showAuthorsNotes"
private const val NOTES_PAGE_LENGTH = 3000
private val dateFormat = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH)
private val siteZone = ZoneId.of("America/Los_Angeles")
