package eu.kanade.tachiyomi.extension.en.infinitescan

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class InfiniteScan : KeiSource() {

    override val supportsLatest = false

    private class Site(val series: List<String>, val imageHost: String)

    private val siteMutex = Mutex()
    private var site: Site? = null

    // The site is a single-page app: its home page's gallery and the host of its images are written in its script
    private suspend fun getSite(refresh: Boolean = false): Site = siteMutex.withLock {
        site?.takeUnless { refresh } ?: run {
            val scriptUrl = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
                .selectFirst("script[type=module][src]")!!
                .absUrl("src")
            val script = client.get(scriptUrl, Fetch.SCRIPT).body.string()
            // Besides the series' covers, the gallery holds other pictures, mostly "-thumb-small" thumbnails
            val slugs = GALLERY_REGEX.findAll(script).map { it.groupValues[1] }
                .filterNot { it.endsWith("-thumb-small") }
                .distinct()
                .toList()
            // Only series have a chapter list
            val series = coroutineScope {
                slugs.map { slug -> async { slug.takeIf { getChapterCounts(it) != null } } }.awaitAll()
            }.filterNotNull()
            Site(series, IMAGE_HOST_REGEX.find(script)!!.value).also { site = it }
        }
    }

    // A series' chapters ("chapter_001") and their page counts
    private suspend fun getChapterCounts(slug: String): Map<String, Int>? {
        val response = client.get("$baseUrl/json/$slug.json", Fetch.XHR, ensureSuccess = false)
        if (!response.isSuccessful) {
            response.close()
            // Names that aren't series get a 404
            if (response.code == 404) return null
            throw HttpException(response.code)
        }
        return response.parseAs<Map<String, Map<String, Int>>>()[slug]
    }

    override suspend fun getPopularManga(page: Int) = MangasPage(getSite(refresh = true).series.map(::seriesManga), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = getSite().series.map(::seriesManga).filter { it.title.contains(query.trim(), ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    // The site shows no titles or descriptions, so titles come from the series' names in its links
    private fun seriesManga(slug: String) = SManga.create().apply {
        url = "/$slug"
        title = slug.split('-')
            .mapIndexed { index, word -> if (index > 0 && word in SMALL_WORDS) word else word.replaceFirstChar(Char::uppercase) }
            .joinToString(" ")
        thumbnail_url = "$baseUrl/gallery/$slug.webp"
        initialized = true
    }

    // Series links are /#/<series>
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val slug = url.fragment?.trim('/')?.substringBefore('/')?.takeIf { it.isNotEmpty() } ?: return null
        return seriesManga(slug).takeIf { getChapterCounts(slug) != null }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/#${manga.url}"

    // The site's reader is the series page, with a chapter picker
    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/#/${chapter.url.removePrefix("/").substringBefore('/')}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.removePrefix("/")
        val counts = getChapterCounts(slug) ?: throw Exception("This series isn't on the site anymore")
        val chapterList = counts.keys
            .mapNotNull { it.removePrefix("chapter_").toIntOrNull() }
            .sortedDescending()
            .map { number ->
                SChapter.create().apply {
                    url = "/$slug/$number"
                    name = "Chapter $number"
                    chapter_number = number.toFloat()
                }
            }
        return SMangaUpdate(seriesManga(slug), chapterList)
    }

    // Like the site's reader: book/<series>/Chapter_001/01.webp, as many pages as the chapter list gives
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, number) = chapter.url.removePrefix("/").split('/')
        val key = "chapter_" + number.padStart(3, '0')
        val count = getChapterCounts(slug)?.get(key) ?: throw Exception("This chapter isn't on the site anymore")
        val folder = "${getSite().imageHost}/book/$slug/${key.replaceFirstChar(Char::uppercase)}"
        return (1..count).map { page -> Page(page - 1, imageUrl = "$folder/${page.toString().padStart(2, '0')}.webp") }
    }

    companion object {
        private val GALLERY_REGEX = Regex("""/gallery/([a-z0-9]+(?:-[a-z0-9]+)*)\.webp""")
        private val IMAGE_HOST_REGEX = Regex("""https://pub-[0-9a-f]+\.r2\.dev""")
        private val SMALL_WORDS = setOf("a", "an", "and", "as", "at", "by", "for", "in", "of", "on", "or", "the", "to")
    }
}
