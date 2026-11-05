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
import org.jsoup.nodes.Element

@Source
abstract class Mangahot : Madara() {

    override val genreDirectory = "genre"

    override val seriesTypeSelector = ".post-content_item:has(h5:matchesOwn(^\\s*Type\\s*$)) .summary-content"

    override val statusFilterOptions = listOf(
        intl["status_filter_ongoing"] to "on-going",
        intl["status_filter_completed"] to "end",
        intl["status_filter_canceled"] to "canceled",
        intl["status_filter_on_hold"] to "on-hold",
        "Upcoming" to "upcoming",
    )

    override fun parseArchive(document: Document): List<SManga> = document.select(archiveSelector()).mapNotNull { element ->
        val id = element.attr("data-post-id").ifBlank { element.postId().orEmpty() }
        archiveManga(element, id)
    }

    // Live genre labels include counts like "Action (159)"; strip them.
    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>()?.map { GenreDto(it.name.replace(COUNTS_SUFFIX, ""), it.slug) }.orEmpty()
        return FilterList(
            UriPartFilter(intl["order_by_filter_title"], "m_orderby", orderByFilterOptions),
            StatusFilter(intl["status_filter_title"], statusFilterOptions),
            Filter.Separator(),
            GenreList(intl["genre_filter_title"], genres),
            MatchAllGenresFilter(),
            UriPartFilter("Content Rating", "adult", CONTENT_RATING_OPTIONS),
            Filter.Separator(),
            TextFilter(intl["author_filter_title"], "author"),
            TextFilter(intl["artist_filter_title"], "artist"),
            TextFilter(intl["year_filter_title"], "release"),
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genresSelected = filters.firstInstanceOrNull<GenreList>()?.state?.any { it.state } == true
        val builder = baseUrl.toHttpUrl().newBuilder()
            .encodedPath(if (page > 1) "/page/$page/" else "/")
            .addQueryParameter("s", query)
            .addQueryParameter("post_type", "wp-manga")
        filters.forEach { filter ->
            when (filter) {
                is TextFilter -> if (filter.state.isNotBlank()) builder.addQueryParameter(filter.param, filter.state)
                is StatusFilter -> filter.state.filter { it.state }.forEach { builder.addQueryParameter("status[]", it.slug) }
                is MatchAllGenresFilter -> if (filter.state && genresSelected) builder.addQueryParameter("op", "1")
                is UriPartFilter -> filter.key().takeIf(String::isNotEmpty)?.let { builder.addQueryParameter(filter.param, it) }
                is GenreList -> filter.state.filter { it.state }.forEach { builder.addQueryParameter("genre[]", it.slug) }
                else -> Unit
            }
        }
        val document = client.get(builder.build(), Fetch.DOCUMENT).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst("a.nextpostslink") != null)
    }

    // ============================ Manga Details ===========================

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        description = MarkdownDescription.Builder().apply {
            description(document.selectFirst(mangaDetailsSelectorDescription))
            rating(
                document.selectFirst("#averagerate")?.text()?.toDoubleOrNull(),
                max = 5,
                votes = document.selectFirst("#countrate")?.text()?.toIntOrNull(),
            )
            info(document.summaryRow("Release")?.text(), document.summaryRow("Type")?.textOrNull())
            // Alts are separated by ", "; names may contain their own commas, so splitting is approximate.
            altTitles(
                document.summaryRow("Alternative")?.ownText()?.takeIf { it.isNotEmpty() && !isUpdating(it) }?.split(", ").orEmpty(),
                label = "Associated Name(s)",
            )
        }.build()
    }

    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = url?.replace(THUMBNAIL_SIZE_SUFFIX, "$1")

    private fun Document.summaryRow(label: String): Element? = select(".post-content_item")
        .firstOrNull { it.selectFirst("h5")?.text() == label }
        ?.selectFirst(".summary-content")

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

    companion object {
        private val THUMBNAIL_SIZE_SUFFIX = Regex("""-\d+x\d+(\.\w+)$""")
        private val COUNTS_SUFFIX = Regex("""\s*\(\d+\)\s*$""")
        private val CONTENT_RATING_OPTIONS = listOf(
            "All (include 18+)" to "",
            "Exclude 18+" to "0",
            "Only 18+" to "1",
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
