package eu.kanade.tachiyomi.extension.en.ninehentai

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.util.Calendar

@Source
abstract class NineHentai : KeiSource() {

    // The API's image_server still points old galleries at the dead i.9hentai.com host
    private val imageBaseUrl: String
        get() = "https://i.${baseUrl.toHttpUrl().host}/images"

    private fun coverUrl(id: Int) = "$imageBaseUrl/$id/cover.jpg"

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int): MangasPage = search(page = page, sort = 1)

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = search(page = page)

    // ============================== Search ===============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null

        return fetchMangaById(id).let { it.toSManga(coverUrl(it.id)) }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.startsWith("id:")) {
            val id = query.substringAfter("id:").toIntOrNull() ?: return MangasPage(emptyList(), false)
            return MangasPage(listOf(fetchMangaById(id).let { it.toSManga(coverUrl(it.id)) }), false)
        }

        val sort = filters.firstInstanceOrNull<SortFilter>()?.state ?: 0
        val minPages = filters.firstInstanceOrNull<MinPagesFilter>()?.state?.toIntOrNull() ?: 0
        val maxPages = filters.firstInstanceOrNull<MaxPagesFilter>()?.state?.toIntOrNull() ?: 2000

        val includedTags = mutableListOf<Tag>()
        val excludedTags = mutableListOf<Tag>()

        filters.firstInstanceOrNull<IncludedFilter>()?.state?.takeIf { it.isNotBlank() }?.let { includedTags += getTags(it, 1) }
        filters.firstInstanceOrNull<ExcludedFilter>()?.state?.takeIf { it.isNotBlank() }?.let { excludedTags += getTags(it, 1) }
        filters.firstInstanceOrNull<GroupFilter>()?.state?.takeIf { it.isNotBlank() }?.let { includedTags += getTags(it, 2) }
        filters.firstInstanceOrNull<ParodyFilter>()?.state?.takeIf { it.isNotBlank() }?.let { includedTags += getTags(it, 3) }
        filters.firstInstanceOrNull<ArtistFilter>()?.state?.takeIf { it.isNotBlank() }?.let { includedTags += getTags(it, 4) }
        filters.firstInstanceOrNull<CharacterFilter>()?.state?.takeIf { it.isNotBlank() }?.let { includedTags += getTags(it, 5) }
        filters.firstInstanceOrNull<CategoryFilter>()?.state?.takeIf { it.isNotBlank() }?.let { includedTags += getTags(it, 6) }

        return search(
            searchText = query,
            page = page,
            sort = sort,
            range = listOf(minPages, maxPages),
            includedTags = includedTags,
            excludedTags = excludedTags,
        )
    }

    // ============================== Details ==============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val path = response.request.url.encodedPath
        val document = response.asJsoup()

        document.selectFirst("div#bigcontainer")?.let { info ->
            manga.apply {
                title = info.select("h1").text()
                thumbnail_url = manga.url.substringAfter("/g/").substringBefore("/").toIntOrNull()?.let(::coverUrl) ?: thumbnail_url
                status = SManga.COMPLETED
                artist = info.selectTextOrNull("div.field-name:contains(Artist:) a.tag")
                author = info.selectTextOrNull("div.field-name:contains(Group:) a.tag") ?: "Unknown circle"
                genre = info.selectTextOrNull("div.field-name:contains(Tag:) a.tag")

                description = buildString {
                    info.selectTextOrNull("h2")?.let { append("Alternative Title: ", it, "\n\n") }
                    info.selectTextOrNull("div#info > div:contains(pages)")?.let { append("Pages: ", it, "\n\n") }
                    info.selectTextOrNull("div.field-name:contains(Parody:) a.tag")?.let { append("Parody: ", it, "\n\n") }
                    info.selectTextOrNull("div.field-name:contains(Category:) a.tag")?.let { append("Category: ", it, "\n\n") }
                    info.selectTextOrNull("div.field-name:contains(Language:) a.tag")?.let { append("Language: ", it) }
                }.trim()
            }
        }

        val time = document.select("div#info div time").text()
        val chapter = SChapter.create().apply {
            name = "Chapter"
            date_upload = parseChapterDate(time)
            url = path
        }

        return SMangaUpdate(manga, listOf(chapter))
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangaId = chapter.url.substringAfter("/g/").substringBefore("/").toInt()
        val imageUrl = "$imageBaseUrl/$mangaId"
        var totalPages = fetchMangaById(mangaId).totalPage

        client.get("$imageUrl/preview/${totalPages}t.jpg", ensureSuccess = false).use { response ->
            if (response.code == 404) totalPages--
        }

        return (1..totalPages).map {
            Page(it - 1, imageUrl = "$imageUrl/$it.jpg")
        }
    }

    // ============================== Filters ==============================
    private class SortFilter :
        Filter.Select<String>(
            "Sort by",
            arrayOf("Newest", "Popular Right now", "Most Fapped", "Most Viewed", "By Title"),
        )
    private class MinPagesFilter : Filter.Text("Minimum Pages")
    private class MaxPagesFilter : Filter.Text("Maximum Pages")
    private class IncludedFilter : Filter.Text("Included Tags")
    private class ExcludedFilter : Filter.Text("Excluded Tags")
    private class ArtistFilter : Filter.Text("Artist")
    private class GroupFilter : Filter.Text("Group")
    private class ParodyFilter : Filter.Text("Parody")
    private class CharacterFilter : Filter.Text("Character")
    private class CategoryFilter : Filter.Text("Category")

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Search by id with \"id:\" in front of query"),
        Filter.Separator(),
        SortFilter(),
        MinPagesFilter(),
        MaxPagesFilter(),
        IncludedFilter(),
        ExcludedFilter(),
        ArtistFilter(),
        GroupFilter(),
        ParodyFilter(),
        CharacterFilter(),
        CategoryFilter(),
    )

    // ============================= Utilities =============================
    private suspend fun search(
        searchText: String = "",
        page: Int,
        sort: Int = 0,
        range: List<Int> = listOf(0, 2000),
        includedTags: List<Tag> = emptyList(),
        excludedTags: List<Tag> = emptyList(),
    ): MangasPage {
        val searchRequest = SearchRequest(
            text = searchText,
            page = page - 1, // Source starts counting from 0, not 1
            sort = sort,
            pages = Range(range),
            tag = Items(
                items = TagArrays(
                    included = includedTags,
                    excluded = excludedTags,
                ),
            ),
        )
        val payload = SearchRequestPayload(search = searchRequest)
        val searchResponse = client.post("$baseUrl$SEARCH_URL", payload.toJsonRequestBody())
            .parseAs<SearchResponse>()
        val mangas = searchResponse.results.map { it.toSManga(coverUrl(it.id)) }

        return MangasPage(mangas, searchResponse.totalCount > page)
    }

    private suspend fun fetchMangaById(id: Int): Manga = client.post("$baseUrl$MANGA_URL", IdRequest(id).toJsonRequestBody())
        .parseAs<SingleMangaResponse>()
        .results

    private fun Element.selectTextOrNull(selector: String): String? {
        val list = this.select(selector)
        return if (list.isEmpty()) {
            null
        } else {
            list.joinToString { it.text() }
        }
    }

    private fun parseChapterDate(date: String): Long {
        val dateStringSplit = date.split(" ")
        val value = dateStringSplit.getOrNull(0)?.toIntOrNull() ?: return 0L

        return when (dateStringSplit.getOrNull(1)?.removeSuffix("s")) {
            "sec" -> Calendar.getInstance().apply { add(Calendar.SECOND, -value) }.timeInMillis
            "min" -> Calendar.getInstance().apply { add(Calendar.MINUTE, -value) }.timeInMillis
            "hour" -> Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -value) }.timeInMillis
            "day" -> Calendar.getInstance().apply { add(Calendar.DATE, -value) }.timeInMillis
            "week" -> Calendar.getInstance().apply { add(Calendar.DATE, -value * 7) }.timeInMillis
            "month" -> Calendar.getInstance().apply { add(Calendar.MONTH, -value) }.timeInMillis
            "year" -> Calendar.getInstance().apply { add(Calendar.YEAR, -value) }.timeInMillis
            else -> 0L
        }
    }

    private suspend fun getTags(queries: String, type: Int): List<Tag> = queries.split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { query ->
            val request = TagRequest(query, type)
            client.post("$baseUrl$TAG_URL", request.toJsonRequestBody())
                .parseAs<TagResponse>()
                .results
                .firstOrNull()
        }

    companion object {
        private const val SEARCH_URL = "/api/getBook"
        private const val MANGA_URL = "/api/getBookByID"
        private const val TAG_URL = "/api/getTag"
    }
}
