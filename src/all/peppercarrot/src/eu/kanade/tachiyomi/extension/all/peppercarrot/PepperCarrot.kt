package eu.kanade.tachiyomi.extension.all.peppercarrot

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.TextNode
import org.jsoup.select.Evaluator
import rx.Observable
import rx.Single
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class PepperCarrot :
    HttpSource(),
    ConfigurableSource {
    override val name = TITLE
    override val lang = "all"
    override val supportsLatest = false

    override val baseUrl = BASE_URL

    private val preferences by getPreferencesLazy()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = Single.create<MangasPage> {
        updateLangData(client, headers, preferences)
        val lang = preferences.lang.ifEmpty {
            throw Exception("Please select language in the filter")
        }
        val langMap = preferences.langData.associateBy { langData -> langData.key }
        val mangas = lang.map { key -> langMap[key]!!.toSManga() }
        val miniFantasyTheaters = lang.map { key -> langMap[key]!!.getMiniFantasyTheaterEntry() }
        val result = MangasPage(mangas + miniFantasyTheaters + getArtworkList(), false)
        it.onSuccess(result)
    }.toObservable()

    private fun getArtworkList(): List<SManga> = arrayOf(
        "artworks", "wallpapers", "sketchbook", "misc",
        "book-publishing", "comissions", "eshop", "framasoft", "press", "references", "wiki",
    ).map(::getArtworkEntry)

    override fun getFilterList() = getFilters(preferences)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isNotEmpty()) return Observable.error(Exception("No search"))
        if (filters.isNotEmpty()) preferences.saveFrom(filters)
        return fetchPopularManga(page)
    }

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Single.create<SManga> {
        updateLangData(client, headers, preferences)
        val key = manga.url
        val result = if (key.startsWith('#')) {
            getArtworkEntry(key.substring(1))
        } else if (key.startsWith("miniFantasyTheater")) {
            val langKey = key.substringAfter("#")
            preferences.langData.find { lang -> lang.key == langKey }!!.getMiniFantasyTheaterEntry()
        } else {
            preferences.langData.find { lang -> lang.key == key }!!.toSManga()
        }
        it.onSuccess(result)
    }.toObservable()

    override fun mangaDetailsRequest(manga: SManga): Request {
        val key = manga.url
        val url = if (key.startsWith('#')) { // artwork
            "$BASE_URL/en/files/${key.substring(1)}.html"
        } else if (key.startsWith("miniFantasyTheater")) {
            val langKey = key.substringAfter("#")
            "$BASE_URL/$langKey/webcomics/miniFantasyTheater.html"
        } else {
            "$BASE_URL/$key/webcomics/peppercarrot.html"
        }
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    private fun LangData.toSManga() = SManga.create().apply {
        url = key
        title = this@toSManga.title ?: if (key == "en") TITLE else "$TITLE (${key.uppercase()})"
        author = AUTHOR
        description = this@toSManga.run {
            "Language: $name\nTranslators: $translators"
        }
        status = SManga.ONGOING
        thumbnail_url =
            "$BASE_URL/0_sources/0ther/artworks/low-res/2016-02-24_vertical-cover_remake_by-David-Revoy.jpg"
        initialized = true
    }

    private fun LangData.getMiniFantasyTheaterEntry() = SManga.create().apply {
        url = "miniFantasyTheater#$key"
        title = "Mini Fantasy Theater" + if (key != "en") " (${key.uppercase()})" else ""
        author = AUTHOR
        description =
            "A webcomic series featuring short stories set in the enchanting world of Pepper&Carrot. With its playful humor and whimsical tales, this collection of gag strips is perfect for audiences of all ages."
        status = SManga.ONGOING
        thumbnail_url = "$BASE_URL/0_sources/0ther/artworks/low-res/2018-11-22_vertical-cover-book-three_by-David-Revoy.jpg"
        initialized = true
    }

    private fun getArtworkEntry(key: String) = SManga.create().apply {
        url = "#$key"
        title = when (key) {
            "comissions" -> "Commissions"
            "eshop" -> "Shop"
            else -> key.replaceFirstChar { it.uppercase() }
        }
        author = AUTHOR
        status = SManga.ONGOING
        thumbnail_url = "$BASE_URL/0_sources/0ther/press/low-res/2015-10-12_logo_by-David-Revoy.jpg"
        initialized = true
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Single.create<List<SChapter>> {
        updateLangData(client, headers, preferences)
        val response = client.newCall(chapterListRequest(manga)).execute()
        it.onSuccess(chapterListParse(response))
    }.toObservable()

    override fun chapterListRequest(manga: SManga): Request {
        val key = manga.url
        val url = if (key.startsWith('#')) { // artwork
            "$BASE_URL/0_sources/0ther/${key.substring(1)}/low-res/"
        } else if (key.startsWith("miniFantasyTheater")) {
            val langKey = key.substringAfter("#")
            "$BASE_URL/$langKey/webcomics/miniFantasyTheater.html"
        } else {
            "$BASE_URL/$key/webcomics/peppercarrot.html"
        }
        val lastUpdated = preferences.lastUpdated
        if (lastUpdated == 0L) return GET(url, headers)

        val seconds = System.currentTimeMillis() / 1000 - lastUpdated
        val cache = CacheControl.Builder().maxStale(seconds.toInt(), TimeUnit.SECONDS).build()
        return GET(url, headers, cache)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.request.url.pathSegments[0] == "0_sources") return parseArtwork(response)

        val translatedChapters = response.asJsoup()
            .select(Evaluator.Tag("figure"))
            .let { (it.size downTo 1) zip it }
            .filter { it.second.hasClass("translated") }

        return translatedChapters.map { (number, it) ->
            SChapter.create().apply {
                url = it.selectFirst(Evaluator.Tag("a"))!!.attr("href").removePrefix(BASE_URL)
                name = it.selectFirst(Evaluator.Tag("img"))!!.attr("title").run {
                    val index = lastIndexOf('（')
                    when {
                        index >= 0 -> substring(0, index).trimEnd()
                        else -> substringBeforeLast('(').trimEnd()
                    }
                }
                date_upload = it.selectFirst(Evaluator.Tag("figcaption"))?.text()?.let { text ->
                    dateRegex.find(text)?.value?.let { date -> dateFormat.tryParse(date) }
                } ?: 0L
                chapter_number = number.toFloat()
            }
        }
    }

    private fun parseArtwork(response: Response): List<SChapter> {
        val baseDir = response.request.url.toString().removePrefix(BASE_URL)
        return response.asJsoup().select(Evaluator.Tag("a")).asReversed().mapNotNull {
            val filename = it.attr("href")
            if (!filename.endsWith(".jpg")) return@mapNotNull null

            val file = filename.removeSuffix(".jpg").removeSuffix("_by-David-Revoy")
            val fileStripped: String
            val date: Long
            if (file.length >= 10 && dateRegex.matches(file.substring(0, 10))) {
                fileStripped = file.substring(10)
                date = dateFormat.tryParse(file.substring(0, 10))
            } else {
                fileStripped = file
                val lastModified = it.nextSibling() as? TextNode
                date = if (lastModified == null) 0 else dateFormat.tryParse(lastModified.text())
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

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val url = chapter.url
        return if (url.endsWith(".jpg")) {
            Observable.just(listOf(Page(0, imageUrl = BASE_URL + url)))
        } else {
            super.fetchPageList(chapter)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val urls = document.select(".webcomic-page img").map { it.attr("src") }
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

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val url = page.imageUrl!!
        val newUrl = if (preferences.isHiRes) url.replace("/low-res/", "/hi-res/") else url
        return GET(newUrl, headers)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferences(screen.context).forEach(screen::addPreference)
    }

    private val dateRegex by lazy { Regex("""\d{4}-\d{2}-\d{2}""") }
    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
}
