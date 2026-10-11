package eu.kanade.tachiyomi.extension.all.dbnewhope

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class DBNewHope : KeiSource() {

    private data class LangEntry(
        val title: String,
        val listPath: String,
        val landingPath: String,
    )

    private val languages = listOf(
        LangEntry("DB New Hope (English)", "/english/chapters.php", "/english.php"),
        LangEntry("DB New Hope (Français)", "/francais/chapitres.php", "/francais.php"),
        LangEntry("DB New Hope (Español)", "/espanol/capitulos.php", "/espanol.php"),
    )

    private fun langOf(path: String): LangEntry? = languages.firstOrNull { path.startsWith(it.listPath.substringBeforeLast('/')) }

    private fun entryManga(entry: LangEntry): SManga = SManga.create().apply {
        url = entry.listPath
        title = entry.title
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(languages.map(::entryManga), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(languages.map(::entryManga), false)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val results = languages
            .filter { it.title.contains(query, ignoreCase = true) }
            .map(::entryManga)
        return MangasPage(results, false)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val entry = langOf(url.encodedPath) ?: return null
        return entryManga(entry)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val entry = languages.firstOrNull { it.listPath == manga.url } ?: languages.first()
        return coroutineScope {
            val details = async { parseMangaDetails(client.get(baseUrl + entry.landingPath).asJsoup(), entry) }
            val chapterList = async { parseChapterList(client.get(baseUrl + entry.listPath).asJsoup()) }
            SMangaUpdate(details.await(), chapterList.await())
        }
    }

    private fun parseMangaDetails(document: Document, entry: LangEntry): SManga = SManga.create().apply {
        url = entry.listPath
        title = entry.title
        thumbnail_url = document.selectFirst("img.masthead-avatar")?.attr("abs:src")
        val welcome = document.selectFirst("header p.masthead-subheading")?.text().orEmpty().trim()
        val sagas = document.select("section#sagas p")
            .map { it.text().trim() }
            .filter { it.length > 60 }
        description = (listOf(welcome) + sagas)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .ifBlank { document.selectFirst("meta[name=description]")?.attr("content") }
    }

    private val chapterNumberRegex = Regex("(?i)(?:chapter|chapitre|cap[ií]tulo)\\s*(\\d+)")

    private fun parseChapterList(document: Document): List<SChapter> {
        return document.select("div.card:has(a[href$=\"/01.php\"])")
            .mapNotNull { card ->
                val link = card.selectFirst("a[href]") ?: return@mapNotNull null
                val name = card.selectFirst("h5.card-title")?.text()?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                SChapter.create().apply {
                    url = link.attr("abs:href")
                    this.name = name
                    chapter_number = chapterNumberRegex.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    private fun pageImage(document: Document): String = document.selectFirst("img.manga-page")?.attr("abs:src")?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Page image not found")

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterBase = chapter.url.substringBefore("?page=")
        val firstDocument = client.get(chapterBase).asJsoup()
        val pageCount = firstDocument.select("div.nav select option").size.coerceAtLeast(1)
        val firstUrl = pageImage(firstDocument)
        val restUrls = coroutineScope {
            (2..pageCount).map { page ->
                async { pageImage(client.get("$chapterBase?page=$page").asJsoup()) }
            }.awaitAll()
        }
        return (listOf(firstUrl) + restUrls)
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }
}
