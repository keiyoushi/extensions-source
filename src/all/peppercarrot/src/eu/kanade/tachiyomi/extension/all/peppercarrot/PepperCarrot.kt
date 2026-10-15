package eu.kanade.tachiyomi.extension.all.peppercarrot

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.DEFAULT_CACHE_CONTROL
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.TextNode
import org.jsoup.select.Evaluator
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

@Source
abstract class PepperCarrot :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        updateLangData(client, headers, preferences, baseUrl)
        val lang = preferences.lang.ifEmpty {
            throw Exception("Please select language in the filter")
        }
        val langMap = preferences.langData.associateBy { langData -> langData.key }
        val mangas = lang.map { key -> langMap[key]!!.toSManga() }
        val miniFantasyTheaters = lang.map { key -> langMap[key]!!.getMiniFantasyTheaterEntry() }

        return MangasPage(mangas + miniFantasyTheaters + getArtworkList(), false)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) throw Exception("No search")
        preferences.saveFrom(filters)
        return getPopularManga(page)
    }

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String {
        val key = manga.url
        return if (key.startsWith('#')) { // artwork
            "$baseUrl/en/files/${key.substring(1)}.html"
        } else if (key.startsWith("miniFantasyTheater")) {
            val langKey = key.substringAfter("#")
            "$baseUrl/$langKey/webcomics/miniFantasyTheater.html"
        } else {
            "$baseUrl/$key/webcomics/peppercarrot.html"
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        updateLangData(client, headers, preferences, baseUrl)

        val updatedManga = if (fetchDetails) getMangaDetails(manga.url) else manga
        val updatedChapters = if (fetchChapters) getChapterList(manga.url) else chapters

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun getMangaDetails(key: String): SManga = if (key.startsWith('#')) {
        getArtworkEntry(key.substring(1))
    } else if (key.startsWith("miniFantasyTheater")) {
        val langKey = key.substringAfter("#")
        preferences.langData.find { lang -> lang.key == langKey }!!.getMiniFantasyTheaterEntry()
    } else {
        preferences.langData.find { lang -> lang.key == key }!!.toSManga()
    }

    // ============================= Chapters ==============================

    private suspend fun getChapterList(key: String): List<SChapter> {
        val listUrl = if (key.startsWith('#')) { // artwork
            "$baseUrl/0_sources/0ther/${key.substring(1)}/low-res/"
        } else if (key.startsWith("miniFantasyTheater")) {
            val langKey = key.substringAfter("#")
            "$baseUrl/$langKey/webcomics/miniFantasyTheater.html"
        } else {
            "$baseUrl/$key/webcomics/peppercarrot.html"
        }
        val lastUpdated = preferences.lastUpdated
        val cache = if (lastUpdated == 0L) {
            DEFAULT_CACHE_CONTROL
        } else {
            val seconds = System.currentTimeMillis() / 1000 - lastUpdated
            CacheControl.Builder().maxStale(seconds.toInt(), TimeUnit.SECONDS).build()
        }
        val response = client.get(listUrl, cache)

        if (response.request.url.pathSegments[0] == "0_sources") return parseArtwork(response)

        val translatedChapters = response.asJsoup()
            .select(Evaluator.Tag("figure"))
            .let { (it.size downTo 1) zip it }
            .filter { it.second.hasClass("translated") }

        return translatedChapters.map { (number, it) ->
            SChapter.create().apply {
                url = it.selectFirst(Evaluator.Tag("a"))!!.attr("href").removePrefix(baseUrl)
                name = it.selectFirst(Evaluator.Tag("img"))!!.attr("title").run {
                    val index = lastIndexOf('（')
                    when {
                        index >= 0 -> substring(0, index).trimEnd()
                        else -> substringBeforeLast('(').trimEnd()
                    }
                }
                date_upload = it.selectFirst(Evaluator.Tag("figcaption"))?.text()?.let { text ->
                    dateRegex.find(text)?.value?.let { date -> dateFormat.tryParseDate(date, ZoneOffset.UTC) }
                } ?: 0L
                chapter_number = number.toFloat()
            }
        }
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = chapter.url
        if (url.endsWith(".jpg")) {
            return listOf(Page(0, imageUrl = baseUrl + url))
        }

        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val urls =
            document.select(".webcomic-page img").map { it.attr("src") } +
                document.select(".mft-cv-image").map { it.attr("src") }

        if (urls.isEmpty()) return emptyList()

        val thumbnail =
            if (urls[0].contains("miniFantasyTheater", true)) {
                emptyList()
            } else {
                listOf(urls[0].replace("P00.jpg", ".jpg"))
            }

        return (thumbnail + urls).mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageRequest(page: Page): Request {
        val url = page.imageUrl!!
        val newUrl = if (preferences.isHiRes) url.replace("/low-res/", "/hi-res/") else url
        return super.imageRequest(page).newBuilder().url(newUrl).build()
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = getFilters(preferences)

    // ============================= Utilities =============================

    private fun getArtworkList(): List<SManga> = arrayOf(
        "artworks", "wallpapers", "sketchbook", "misc",
        "book-publishing", "comissions", "eshop", "framasoft", "press", "references", "wiki",
    ).map(::getArtworkEntry)

    private fun getArtworkEntry(key: String) = SManga.create().apply {
        url = "#$key"
        title = when (key) {
            "comissions" -> "Commissions"
            "eshop" -> "Shop"
            else -> key.replaceFirstChar { it.uppercase() }
        }
        author = AUTHOR
        status = SManga.ONGOING
        thumbnail_url = "$baseUrl/0_sources/0ther/press/low-res/2015-10-12_logo_by-David-Revoy.jpg"
        initialized = true
    }

    private fun LangData.toSManga() = SManga.create().apply {
        url = key
        title = this@toSManga.title ?: if (key == "en") TITLE else "$TITLE (${key.uppercase()})"
        author = AUTHOR
        description = this@toSManga.run {
            "Language: $name\nTranslators: $translators"
        }
        status = SManga.ONGOING
        thumbnail_url =
            "$baseUrl/0_sources/0ther/artworks/low-res/2016-02-24_vertical-cover_remake_by-David-Revoy.jpg"
        initialized = true
    }

    private fun LangData.getMiniFantasyTheaterEntry() = SManga.create().apply {
        url = "miniFantasyTheater#$key"
        title = "Mini Fantasy Theater" + if (key != "en") " (${key.uppercase()})" else ""
        author = AUTHOR
        description =
            "A webcomic series featuring short stories set in the enchanting world of Pepper&Carrot. With its playful humor and whimsical tales, this collection of gag strips is perfect for audiences of all ages."
        status = SManga.ONGOING
        thumbnail_url = "$baseUrl/0_sources/0ther/artworks/low-res/2018-11-22_vertical-cover-book-three_by-David-Revoy.jpg"
        initialized = true
    }

    private fun parseArtwork(response: Response): List<SChapter> {
        val baseDir = response.request.url.toString().removePrefix(baseUrl)
        return response.asJsoup().select(Evaluator.Tag("a")).asReversed().mapNotNull {
            val filename = it.attr("href")
            if (!filename.endsWith(".jpg")) return@mapNotNull null

            val file = filename.removeSuffix(".jpg").removeSuffix("_by-David-Revoy")
            val fileStripped: String
            val date: Long
            if (file.length >= 10 && dateRegex.matches(file.substring(0, 10))) {
                fileStripped = file.substring(10)
                date = dateFormat.tryParseDate(file.substring(0, 10), ZoneOffset.UTC)
            } else {
                fileStripped = file
                val lastModified = it.nextSibling() as? TextNode
                date = dateFormat.tryParseDate(lastModified?.text()?.let { text -> dateRegex.find(text)?.value }, ZoneOffset.UTC)
            }
            val fileNormalized = fileStripped
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .replaceFirstChar { char -> char.uppercase() }

            SChapter.create().apply {
                url = baseDir + filename
                name = fileNormalized
                date_upload = date
                chapter_number = -2f
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferences(screen.context).forEach(screen::addPreference)
    }

    private val dateRegex = Regex("""\d{4}-\d{2}-\d{2}""")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
}
