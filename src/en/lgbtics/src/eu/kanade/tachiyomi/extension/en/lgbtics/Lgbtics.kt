package eu.kanade.tachiyomi.extension.en.lgbtics

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.util.Locale

private val countSuffixRegex = Regex("""\s*\(\d+\)$""")

@Source
abstract class Lgbtics : MadaraNoAjax() {
    override val mangaSubString = "comic"

    override val supportsPostId = false

    override val orderByFilterOptions = listOf(
        intl["order_by_filter_relevance"] to "",
        intl["order_by_filter_latest"] to "latest",
        intl["order_by_filter_az"] to "alphabet",
        intl["order_by_filter_rating"] to "rating",
        intl["order_by_filter_trending"] to "trending",
        intl["order_by_filter_views"] to "views",
    )

    override suspend fun fetchFilterData(): JsonElement {
        val (genres, parodies) = coroutineScope {
            val g = async { tagPage("all-genres2", "comic-genre") }
            val p = async { tagPage("all-parodies", "comic-tag") }
            g.await() to p.await()
        }
        if (genres.isEmpty() && parodies.isEmpty()) throw IllegalStateException("No tags parsed")
        val data = FilterDataDto(genres, parodies)
        return data.toJsonElement()
    }

    private suspend fun tagPage(pagePath: String, basePath: String): List<TagDto> {
        val document = client.get("$baseUrl/$pagePath/").asJsoup()
        val prefix = "$baseUrl/$basePath/"
        return document.select("a[href^=\"$prefix\"]").mapNotNull { a ->
            val slug = a.absUrl("href").removePrefix(prefix).removeSuffix("/")
            val name = a.textOrNull()?.replace(countSuffixRegex, "") ?: return@mapNotNull null
            if (slug.isEmpty()) return@mapNotNull null
            TagDto(name, slug)
        }.distinctBy(TagDto::slug).sortedBy { it.name.lowercase(Locale.US) }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val fetched = data?.parseAs<FilterDataDto>()
        val genres = fetched?.genres.orEmpty()
        val parodies = fetched?.parodies.orEmpty()
        val defaultSortIndex = orderByFilterOptions.indexOfFirst { it.second == "latest" }.coerceAtLeast(0)
        return FilterList(
            buildList {
                add(LgbticsSortFilter(intl["order_by_filter_title"], orderByFilterOptions, defaultSortIndex))
                if (genres.isNotEmpty()) add(LgbticsGenreFilter(intl["genre_filter_title"], genres))
                add(LgbticsStatusFilter(intl["status_filter_title"]))
                add(LgbticsAuthorFilter())
                add(LgbticsArtistFilter())
                if (parodies.isNotEmpty()) add(LgbticsParodyFilter("Parodies", parodies))
            },
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sortKey = filters.firstInstanceOrNull<LgbticsSortFilter>()?.key().orEmpty()
        val genres = filters.firstInstanceOrNull<LgbticsGenreFilter>()
            ?.state?.filter { it.state }?.map(LgbticsGenreCheckBox::slug).orEmpty()
        val status = filters.firstInstanceOrNull<LgbticsStatusFilter>()
            ?.state?.filter { it.state }?.map(LgbticsStatusTag::slug).orEmpty()
        val author = filters.firstInstanceOrNull<LgbticsAuthorFilter>()?.state.orEmpty().trim()
        val artist = filters.firstInstanceOrNull<LgbticsArtistFilter>()?.state.orEmpty().trim()
        val parodySlug = filters.firstInstanceOrNull<LgbticsParodyFilter>()?.slug()

        if (parodySlug != null) return archivePage(page, sortKey, "/comic-tag/$parodySlug/")

        if (query.isBlank() && genres.isEmpty() && status.isEmpty() && author.isBlank() && artist.isBlank()) {
            return archivePage(page, sortKey.ifBlank { "latest" })
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page/")
            addQueryParameter(searchQueryParameter, query)
            addQueryParameter("post_type", "wp-manga")
            genres.forEach { addQueryParameter("genre[]", it) }
            status.forEach { addQueryParameter("status[]", it) }
            if (author.isNotBlank()) addQueryParameter("author", author)
            if (artist.isNotBlank()) addQueryParameter("artist", artist)
            if (sortKey.isNotBlank()) addQueryParameter(orderQueryParameter, sortKey)
        }.build()

        val document = client.get(url).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst(nextPageSelector()) != null)
    }

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga {
        val manga = super.parseDetails(document, id, preserveUrl)
        document.selectFirst("div.post-total-rating span.score")?.text()?.toFloatOrNull()?.let { rating ->
            val formatted = "%.1f".format(Locale.US, rating).removeSuffix(".0")
            manga.description =
                listOfNotNull("Rating: $formatted/5", manga.description).joinToString("\n\n")
        }
        return manga
    }
}
