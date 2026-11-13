package eu.kanade.tachiyomi.extension.all.mangasincensura

import app.cash.quickjs.QuickJs
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class MangasSinCensura : KeiSource() {

    private val siteUrl get() = baseUrl.toHttpUrl()

    // The site lists its whole catalogue at once, with no dates
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(fetchSeries().map { it.toSManga(siteUrl) }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val series = fetchSeries().map { it.toSManga(siteUrl) }.filter { it.title.contains(query.trim(), ignoreCase = true) }
        return MangasPage(series, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != siteUrl.host) return null
        val id = url.queryParameter("id") ?: url.queryParameter("manhwa") ?: return null
        val catalogue = fetchCatalogue()
        // Older English slugs still lead to the series under its current id
        val series = catalogue[id]?.let { catalogue[it.id] } ?: return null

        return series.toSManga(siteUrl).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = fetchCatalogue()[getMangaUrl(manga).toHttpUrl().queryParameter("id")!!]!!
        return SMangaUpdate(series.toSManga(siteUrl), series.toChapters(if (lang == "es") "Capítulo" else "Chapter"))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val series = fetchCatalogue()[chapterUrl.queryParameter("manhwa")!!]!!
        val number = chapterUrl.queryParameter("cap")!!.toInt()
        val vip = series.isVip(number)
        val folder = (if (vip) siteUrl.resolve("/premium-content/manhwas")!! else storageUrl).newBuilder()
            .addPathSegment(series.storageName)
            .addPathSegment("ch" + number.toString().padStart(3, '0'))
            .build()
        val manifest = folder.newBuilder().addPathSegment("manifest.json").build()

        // Free chapters' manifests go through the site's API, VIP ones need the session of a VIP account
        val manifestUrl = if (vip) manifest else siteUrl.resolve("/api/manifest")!!.newBuilder().addQueryParameter("url", manifest.toString()).build()

        return client.get(manifestUrl, Fetch.XHR.from(chapterUrl)).parseAs<ManifestDto>().pages.mapIndexed { index, page ->
            Page(index, imageUrl = folder.newBuilder().addPathSegment(page.fileName).build().toString())
        }
    }

    private val storageUrl get() = (if (lang == "es") "https://img.mangasincensura.com/manhwas" else "https://cdn.uncensoredmanga.com/manhwas").toHttpUrl()

    // Series are listed under their id; older slugs are kept as extra keys pointing to the same id
    private suspend fun fetchSeries() = fetchCatalogue().filter { (key, series) -> key == series.id }.values

    // The catalogue is a constant in one of the scripts that the home page preloads
    private suspend fun fetchCatalogue(): Map<String, SeriesDto> {
        val script = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
            .select("link[rel=modulepreload]")
            .firstNotNullOf { link ->
                client.get(link.absUrl("href"), Fetch.SCRIPT).use { it.body.string() }.takeIf { CATALOGUE_START.containsMatchIn(it) }
            }
        val start = CATALOGUE_START.find(script)!!.range.first + 1
        val literal = script.substring(start, script.closingBrace(start) + 1)

        return QuickJs.create().use { it.evaluate("JSON.stringify($literal)") as String }.parseAs()
    }

    // Index of the brace that closes the object opened at [start], skipping strings
    private fun String.closingBrace(start: Int): Int {
        var depth = 0
        var quote: Char? = null
        var index = start
        while (true) {
            val char = this[index]
            when {
                quote != null -> if (char == '\\') {
                    index++
                } else if (char == quote) {
                    quote = null
                }
                char == '"' || char == '\'' || char == '`' -> quote = char
                char == '{' -> depth++
                char == '}' -> if (--depth == 0) return index
            }
            index++
        }
    }

    companion object {
        private val CATALOGUE_START = Regex("""=\{\w+:\{[^{}]*\btotalChapters:""")
    }
}
