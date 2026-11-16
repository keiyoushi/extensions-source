package eu.kanade.tachiyomi.extension.ja.mangahotweb

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaHotWeb : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = getWorkList("$baseUrl/ranking/".toHttpUrl())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/search/".toHttpUrl().newBuilder().addQueryParameter("keyword", query).build()
        } else {
            val genreId = filters.firstInstanceOrNull<GenreFilter>()?.genreId ?: return getPopularManga(page)
            "$baseUrl/genre/".toHttpUrl().newBuilder().addQueryParameter("genre_id", genreId).build()
        }
        return getWorkList(url)
    }

    // Ranking, genre and search pages list all their works at once
    private suspend fun getWorkList(url: HttpUrl): MangasPage {
        val mangas = client.get(url, Fetch.DOCUMENT).asJsoup().select("div.list_box1 a.parent[href*=work_code]").map { card ->
            SManga.create().apply {
                this.url = card.absUrl("href").toHttpUrl().queryParameter("work_code")!!
                title = card.selectFirst(".work_name")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val workCode = url.queryParameter("work_code")
            ?: url.queryParameter("story_id")?.let { getWorkCode(it) }
            ?: return null
        val manga = SManga.create().apply { this.url = workCode }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // An episode's reader links to its work, and a locked episode redirects to it
    private suspend fun getWorkCode(storyId: String): String? {
        val document = client.get(viewUrl(storyId), Fetch.DOCUMENT).asJsoup()
        return document.location().toHttpUrl().queryParameter("work_code")
            ?: document.selectFirst("a[href*=work_code]")?.absUrl("href")?.toHttpUrl()?.queryParameter("work_code")
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/works/detail.php?work_code=${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) getDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) getChapters(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun getDetails(manga: SManga): SManga {
        val work = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup().selectFirst("div.work_box")!!
        val creators = work.selectFirst(".author_name")?.text().orEmpty()
            .split(CREATOR_SEPARATOR_REGEX)
            .filter { it.isNotBlank() }
            .map { part -> CREATOR_REGEX.matchEntire(part.trim())?.destructured?.let { (role, name) -> role to name } ?: ("" to part.trim()) }
        return SManga.create().apply {
            url = manga.url
            title = work.selectFirst(".work_name")!!.text()
            thumbnail_url = work.selectFirst("> img")?.absUrl("src")
            author = creators.filter { it.first !in ARTIST_ROLES }.map { it.second }.distinct().joinToString().ifEmpty { null }
            artist = creators.filter { it.first in ARTIST_ROLES }.map { it.second }.distinct().joinToString().ifEmpty { null }
            description = MarkdownDescription.Builder()
                .description(work.selectFirst(".catch_phrase"))
                .description(work.selectFirst(".more_off .summary"))
                .build()
        }
    }

    // The work page shows the first episodes; its "show all" button asks for the full list
    private suspend fun getChapters(manga: SManga): List<SChapter> {
        val workUrl = getMangaUrl(manga)
        val url = workUrl.toHttpUrl().newBuilder().addQueryParameter("mode", "ajax_stories").build()
        return client.get(url, Fetch.XHR.from(workUrl)).parseAs<StoriesDto>().stories(baseUrl).map { story ->
            // Free episodes link to their reader; the others open the site's ad or points dialog
            val locked = !story.attr("href").contains("free.php")
            SChapter.create().apply {
                this.url = story.id().removePrefix("STORY")
                name = (if (locked) "🔒 " else "") + story.selectFirst(".episode_name")!!.text()
                if (locked) memo = buildJsonObject { put(LOCKED, true) }
            }
        }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/works/free.php?story_id=${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo[LOCKED]?.booleanOrNull == true) return emptyList()
        val viewUrl = viewUrl(chapter.url)
        val script = client.get(viewUrl, Fetch.DOCUMENT).asJsoup().selectFirst("script:containsData(all_page)")!!.data()
        val pageCount = PAGE_COUNT_REGEX.find(script)!!.groupValues[1].toInt()
        // The reader asks for the files around a page, five at a time
        val files = coroutineScope {
            (1..pageCount step 5).map { pageNo -> async { getPageFiles(chapter.url, pageNo, viewUrl) } }.awaitAll()
        }.fold(sortedMapOf<Int, String>()) { all, window -> all.apply { putAll(window) } }
        return files.values.mapIndexed { i, file -> Page(i, imageUrl = "$baseUrl/app_img$file#$PUZZLE_FRAGMENT") }
    }

    private suspend fun getPageFiles(storyId: String, pageNo: Int, viewUrl: String): Map<Int, String> {
        val body = FormBody.Builder()
            .add("story_id", storyId)
            .add("page_no", pageNo.toString())
            .build()
        return client.post("$baseUrl/works/fls.php", Fetch.XHR.from(viewUrl), body).parseAs<PageFilesDto>().files()
    }

    private fun viewUrl(storyId: String) = "$baseUrl/works/view.php?story_id=$storyId"

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    companion object {
        private const val LOCKED = "locked"
        private val ARTIST_ROLES = setOf("漫画", "作画")
        private val CREATOR_SEPARATOR_REGEX = Regex("[　、]")
        private val CREATOR_REGEX = Regex("""(.+?)[／：](.+)""")
        private val PAGE_COUNT_REGEX = Regex("""all_page\s*=\s*(\d+)""")
    }
}
