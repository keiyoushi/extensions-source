package eu.kanade.tachiyomi.extension.en.transfusions

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Transfusions : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(listOf(createManga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val manga = createManga().takeIf { it.title.contains(query.trim(), ignoreCase = true) }
        return MangasPage(listOfNotNull(manga), false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = createManga().takeIf { url.host == baseUrl.toHttpUrl().host }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = SMangaUpdate(
        if (fetchDetails) createManga() else manga,
        if (fetchChapters) fetchChapterList() else chapters,
    )

    private fun createManga() = SManga.create().apply {
        url = "/comic/archive"
        title = name
        author = AUTHOR
        artist = AUTHOR
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        description = DESCRIPTION
        thumbnail_url = "$baseUrl/uploads/1537402414-2_joa.png"
    }

    // A chapter is one of the archive's storylines: the books, their chapters (nested under them), the hiatus and the epilogue
    private suspend fun fetchChapterList(): List<SChapter> {
        val parents = mutableListOf<Pair<Int, String>>()
        val storylines = fetchArchive().map { element ->
            val indent = element.attr("style").substringAfter("margin-left:").substringBefore("px").trim().toIntOrNull() ?: 0
            val link = element.selectFirst(".cc-storyline-header a")!!
            parents.removeAll { (parentIndent, _) -> parentIndent >= indent }
            val name = (parents.map { it.second } + link.text()).joinToString(" - ")
            parents += indent to link.text()
            name to link.absUrl("href")
        }
        // The archive lists a book before its chapters even when the book's own page comes after them (Book 1 ends
        // with one), so the storylines are put in reading order by the date of their first page.
        val dated = coroutineScope {
            storylines.map { (name, url) -> async { Triple(name, url, firstPageDate(url)) } }.awaitAll()
        }.sortedBy { it.third }
        return dated.mapIndexed { index, (name, url, date) ->
            SChapter.create().apply {
                setUrlWithoutDomain(url)
                this.name = name
                chapter_number = index + 1f
                date_upload = date
                // Empty for the last one, which goes on to the last page of the comic
                memo = buildJsonObject { put(NEXT_STORYLINE, dated.getOrNull(index + 1)?.second.orEmpty()) }
            }
        }.reversed()
    }

    private suspend fun fetchArchive() = client.get("$baseUrl/comic/archive", Fetch.DOCUMENT).asJsoup().select(".cc-storyline-contain")

    private suspend fun firstPageDate(url: String): Long {
        val posted = client.get(url, Fetch.DOCUMENT).asJsoup().selectFirst(".cc-publishtime")?.text()?.removePrefix("Posted ")
        return DATE_FORMAT.tryParseDateTime(posted)
    }

    // The site has no list of a storyline's pages: each page only links to the next one, so the pages are read one by
    // one from the storyline's first page until the first page of the next storyline.
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // Chapters saved without the next storyline stop at the first page of any other storyline
        val ends = chapter.memo[NEXT_STORYLINE]?.stringOrNull?.let(::setOf)
            ?: fetchArchive().map { it.selectFirst(".cc-storyline-header a")!!.absUrl("href") }.toSet()
        val images = mutableListOf<String>()
        var url: String? = getChapterUrl(chapter)
        var count = 0
        while (url != null && count++ < MAX_PAGES) {
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            document.select("#cc-comic").mapTo(images) { it.absUrl("src") }
            url = document.selectFirst("a.cc-next")?.absUrl("href")?.takeUnless { it in ends }
        }
        return images.mapIndexed { index, image -> Page(index, imageUrl = image) }
    }

    companion object {
        private const val NEXT_STORYLINE = "next_storyline"
        private const val MAX_PAGES = 500
        private const val AUTHOR = "Anni K."
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm", Locale.US)
        private const val DESCRIPTION = "Transfusions is a love story between Dylan, a media student, and a young, " +
            "arrogant vampire called Joa. Their relationship develops from threats and bickering into a gentle but " +
            "passionate romance. The story has two parts. The first part, Book 1, focuses on Joa and Dylan's " +
            "relationship. Book 2 has more vampire action."
    }
}
