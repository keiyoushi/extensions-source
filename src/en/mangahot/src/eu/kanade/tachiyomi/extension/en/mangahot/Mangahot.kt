package eu.kanade.tachiyomi.extension.en.mangahot

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class Mangahot : Madara() {

    override val genreDirectory = "genre"

    override val statusFilterOptions = listOf(
        intl["status_filter_ongoing"] to "on-going",
        intl["status_filter_completed"] to "end",
        intl["status_filter_canceled"] to "canceled",
        intl["status_filter_on_hold"] to "on-hold",
        "Upcoming" to "upcoming",
    )

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int) = listArchive("views", page)

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int) = listArchive("modified", page)

    private fun pagePath(page: Int) = if (page > 1) "/page/$page/" else "/"

    private suspend fun listArchive(order: String, page: Int): MangasPage {
        val document = client.get("$baseUrl${pagePath(page)}?s&post_type=wp-manga&m_orderby=$order", Fetch.DOCUMENT).asJsoup()
        return MangasPage(parseArchive(document), document.hasNextPage())
    }

    override fun parseArchive(document: Document): List<SManga> = document.select(archiveSelector()).mapNotNull { element ->
        val id = element.attr("data-post-id").takeIf(String::isNotBlank)
            ?: element.postId()?.takeIf(String::isNotBlank)
            ?: ""
        archiveManga(element, id)
    }

    private fun Document.hasNextPage() = selectFirst("a.nextpostslink") != null

    // =============================== Search ===============================

    // Live genre labels include counts like "Action (159)"; strip them.
    private fun genreList(data: JsonElement?) = data?.parseAs<List<GenreDto>>()?.map { GenreDto(it.name.replace(COUNTS_SUFFIX, ""), it.slug) }.orEmpty()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = genreList(data)
        return FilterList(
            buildList {
                add(SortFilter("Sort By", orderByFilterOptions))
                add(StatusFilter(intl["status_filter_title"], statusFilterOptions))
                add(Filter.Separator())
                add(GenreList(intl["genre_filter_title"], genres))
                add(MatchAllGenresFilter())
                add(ContentRatingFilter("Content Rating", CONTENT_RATING_OPTIONS))
                add(Filter.Separator())
                add(TextFilter(intl["author_filter_title"], "author"))
                add(TextFilter(intl["artist_filter_title"], "artist"))
                add(TextFilter(intl["year_filter_title"], "release"))
            },
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genresSelected = filters.firstInstanceOrNull<GenreList>()?.state?.any { it.state } == true
        val builder = baseUrl.toHttpUrl().newBuilder()
            .encodedPath(pagePath(page))
            .addQueryParameter("s", query)
            .addQueryParameter("post_type", "wp-manga")
        filters.forEach { filter ->
            when (filter) {
                is TextFilter -> if (filter.state.isNotBlank()) builder.addQueryParameter(filter.param, filter.state)
                is StatusFilter -> filter.state.filter { it.state }.forEach { builder.addQueryParameter("status[]", it.slug) }
                is SortFilter -> filter.key().takeIf(String::isNotEmpty)?.let { key -> builder.addQueryParameter("m_orderby", if (key == "latest") "modified" else key) }
                is MatchAllGenresFilter -> if (filter.state && genresSelected) builder.addQueryParameter("op", "1")
                is ContentRatingFilter -> when (filter.key()) {
                    CONTENT_RATING_EXCLUDE -> builder.addQueryParameter("adult", "0")
                    CONTENT_RATING_ONLY -> builder.addQueryParameter("adult", "1")
                }
                is GenreList -> filter.state.filter { it.state }.forEach { builder.addQueryParameter("genre[]", it.slug) }
                else -> Unit
            }
        }
        val document = client.get(builder.build(), Fetch.DOCUMENT).asJsoup()
        return MangasPage(parseArchive(document), document.hasNextPage())
    }

    // ============================ Manga Details ===========================

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga {
        val manga = super.parseDetails(document, id, preserveUrl)
        val ratingValue = document.selectFirst("#averagerate")?.text()?.toDoubleOrNull()
        val ratingCount = document.selectFirst("#countrate")?.text()?.toIntOrNull()
        val type = document.selectFirst(seriesTypeSelector)?.textOrNull()
        val release = document.selectFirst(".post-content_item:contains(Release) .summary-content")?.text()
        // Alts are separated by ", "; names may contain their own commas, so splitting is approximate.
        val altTitles = document.selectFirst(altNameSelector)?.ownText()?.takeIf { it.isNotEmpty() && !isUpdating(it) }
            ?.split(", ")?.map(String::trim)?.filter(String::isNotEmpty)
        manga.description = MarkdownDescription.Builder().apply {
            description(document.selectFirst(mangaDetailsSelectorDescription))
            rating(ratingValue, max = 5, votes = ratingCount)
            info(release, type)
            altTitles(altTitles ?: emptyList(), label = "Associated Name(s)")
        }.build()
        return manga
    }

    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = url?.replace(THUMBNAIL_SIZE_SUFFIX, "$1")

    // ============================== Chapters ==============================

    override suspend fun fetchChapters(mangaPath: String, id: String, mangaPage: Document?): List<SChapter> {
        val document = mangaPage ?: error("Manga page is required")
        val chapters = parseChapterList(document, mangaPath).toMutableList()
        val button = document.selectFirst(".chapter-readmore") ?: return chapters
        val total = button.attr("data-total").toIntOrNull() ?: return chapters
        val postId = button.attr("data-manga-id").ifBlank { id }
        if (postId.isBlank()) return chapters
        var offset = button.attr("data-offset").toIntOrNull() ?: chapters.size
        val seen = chapters.mapTo(mutableSetOf(), SChapter::url)
        while (chapters.size < total) {
            val body = FormBody.Builder()
                .add("action", "wp-manga-get-chapters")
                .add("manga_id", postId)
                .add("offset", offset.toString())
                .build()
            val html = runCatching {
                client.post("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, Fetch.XHR.from(mangaPath), body)
                    .parseAs<ChaptersResponseDto>()
                    .data?.html?.takeIf(String::isNotBlank)
            }.getOrNull() ?: break
            val more = parseChapterList(html.asJsoup(baseUrl), mangaPath)
            val unseen = more.filter { it.url !in seen }
            if (unseen.isEmpty()) break
            chapters += unseen
            seen += unseen.map(SChapter::url)
            offset += more.size
        }
        return chapters
    }

    // ============================== Utilities ===============================

    companion object {
        private val THUMBNAIL_SIZE_SUFFIX = Regex("""-\d+x\d+(\.\w+)$""")
        private val COUNTS_SUFFIX = Regex("""\s*\(\d+\)\s*$""")
        private const val CONTENT_RATING_ALL = "all"
        private const val CONTENT_RATING_EXCLUDE = "exclude"
        private const val CONTENT_RATING_ONLY = "only"
        private val CONTENT_RATING_OPTIONS = listOf(
            "All (include 18+)" to CONTENT_RATING_ALL,
            "Exclude 18+" to CONTENT_RATING_EXCLUDE,
            "Only 18+" to CONTENT_RATING_ONLY,
        )
    }
}

@Serializable
private class ChaptersResponseDto(
    val data: ChaptersDataDto? = null,
)

@Serializable
private class ChaptersDataDto(
    val html: String? = null,
)
