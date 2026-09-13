package eu.kanade.tachiyomi.multisrc.madara

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody

private const val PAGE_SIZE = 25

abstract class Madara : MadaraBase() {
    private enum class BrowseMode {
        Popular,
        Latest,
        Search,
    }

    override suspend fun getPopularManga(page: Int) = ajaxList(page, BrowseMode.Popular)
    override suspend fun getLatestUpdates(page: Int) = ajaxList(page, BrowseMode.Latest)
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = ajaxList(page, BrowseMode.Search, query, filters)
    override fun getHomeUrl() = "$baseUrl/$mangaSubString/?m_orderby=views"

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data.genreRoutes()
        return FilterList(
            buildList {
                add(TextFilter(intl["author_filter_title"], "wp-manga-author"))
                add(TextFilter(intl["artist_filter_title"], "wp-manga-artist"))
                add(TextFilter(intl["year_filter_title"], "wp-manga-release"))
                add(StatusFilter(intl["status_filter_title"], statusFilterOptions))
                add(SortFilter(intl["order_by_filter_title"], orderByFilterOptions))
                add(AdultFilter(intl["adult_content_filter_title"], adultFilterOptions))
                if (genres.isNotEmpty()) {
                    add(Filter.Separator())
                    add(Filter.Header(intl["genre_filter_header"]))
                    add(GenreConditionFilter(intl["genre_condition_filter_title"], genreConditionFilterOptions))
                    add(GenreList(intl["genre_filter_title"], genres))
                }
            },
        )
    }

    private suspend fun ajaxList(page: Int, mode: BrowseMode, query: String = "", filters: FilterList = FilterList()): MangasPage {
        val body = FormBody.Builder().apply {
            add("action", "madara_load_more")
            add("page", (page - 1).toString())
            add("template", "madara-core/content/content-archive")
            add("vars[paged]", "1")
            add("vars[template]", "archive")
            add("vars[posts_per_page]", PAGE_SIZE.toString())
            add("vars[post_type]", "wp-manga")
            add("vars[post_status]", "publish")
            add("vars[manga_archives_item_layout]", "big_thumbnail")
            if (filterNonMangaItems) {
                add("vars[meta_query][0][key]", "_wp_manga_chapter_type")
                add("vars[meta_query][0][value]", "manga")
            }
            when (mode) {
                BrowseMode.Popular -> sort("_wp_manga_views")
                BrowseMode.Latest -> sort("_latest_update")
                BrowseMode.Search -> addFilters(query, filters, if (filterNonMangaItems) 1 else 0)
            }
        }.build()
        val mangas = parseArchive(client.post("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, body).asJsoup())
        return MangasPage(mangas, mangas.size == PAGE_SIZE)
    }

    private fun FormBody.Builder.sort(key: String) {
        add("vars[orderby]", "meta_value_num")
        add("vars[meta_key]", key)
        add("vars[order]", "DESC")
    }

    private fun FormBody.Builder.addFilters(query: String, filters: FilterList, initialMetaQueryIndex: Int) {
        if (query.isNotBlank()) add("vars[s]", query)
        var metaQueryIndex = initialMetaQueryIndex
        var taxonomyQueryIndex = 0
        val genres = filters.firstInstanceOrNull<GenreList>()?.state?.filter { it.state }?.map { it.slug }.orEmpty()
        filters.forEach { filter ->
            when (filter) {
                is TextFilter -> if (filter.state.isNotBlank()) {
                    add("vars[tax_query][$taxonomyQueryIndex][taxonomy]", filter.taxonomy)
                    add("vars[tax_query][$taxonomyQueryIndex][field]", "name")
                    add("vars[tax_query][$taxonomyQueryIndex][terms]", filter.state)
                    taxonomyQueryIndex++
                }
                is StatusFilter -> filter.state.filter { it.state }.map { it.slug }.takeIf(List<String>::isNotEmpty)?.let { states ->
                    add("vars[meta_query][$metaQueryIndex][key]", "_wp_manga_status")
                    add("vars[meta_query][$metaQueryIndex][compare]", "IN")
                    states.forEachIndexed { i, state -> add("vars[meta_query][$metaQueryIndex][value][$i]", state) }
                    metaQueryIndex++
                }
                is SortFilter -> when (filter.key()) {
                    "latest" -> sort("_latest_update")
                    "alphabet" -> {
                        add("vars[orderby]", "post_title")
                        add("vars[order]", "ASC")
                    }
                    "rating" -> {
                        add("vars[meta_query][query_average_reviews][key]", "_manga_avarage_reviews")
                        add("vars[meta_query][query_average_reviews][compare]", "EXISTS")
                        add("vars[meta_query][query_total_reviews][key]", "_manga_total_votes")
                        add("vars[meta_query][query_total_reviews][compare]", "EXISTS")
                        add("vars[orderby][query_average_reviews]", "DESC")
                        add("vars[orderby][query_total_reviews]", "DESC")
                    }
                    "trending" -> sort("_wp_manga_week_views_value")
                    "views" -> sort("_wp_manga_views")
                    "new-manga" -> {
                        add("vars[orderby]", "date")
                        add("vars[order]", "DESC")
                    }
                }
                is AdultFilter -> if (filter.state != 0) {
                    add("vars[meta_query][$metaQueryIndex][key]", "manga_adult_content")
                    add("vars[meta_query][$metaQueryIndex][compare]", if (filter.state == 1) "not exists" else "exists")
                    metaQueryIndex++
                }
                is GenreConditionFilter -> if (filter.state == 1 && genres.isNotEmpty()) add("vars[tax_query][$taxonomyQueryIndex][operation]", "AND")
                is GenreList -> if (genres.isNotEmpty()) {
                    add("vars[tax_query][$taxonomyQueryIndex][taxonomy]", "wp-manga-genre")
                    add("vars[tax_query][$taxonomyQueryIndex][field]", "slug")
                    genres.forEachIndexed { i, slug -> add("vars[tax_query][$taxonomyQueryIndex][terms][$i]", slug) }
                }
                else -> Unit
            }
        }
    }

    override suspend fun fetchRelatedMangaList(id: String, genres: List<GenreRoute>): List<SManga> {
        val body = FormBody.Builder().apply {
            add("action", "madara_load_more")
            add("page", "0")
            add("template", "madara-core/content/content-archive")
            add("vars[posts_per_page]", PAGE_SIZE.toString())
            add("vars[template]", "archive")
            add("vars[post_type]", "wp-manga")
            add("vars[post_status]", "publish")
            add("vars[orderby]", "rand")
            add("vars[sidebar]", "right")
            add("vars[manga_archives_item_layout]", "big_thumbnail")
            add("vars[post__not_in][0]", id)
            add("vars[tax_query][0][taxonomy]", "wp-manga-genre")
            add("vars[tax_query][0][field]", "slug")
            genres.forEachIndexed { i, genre -> add("vars[tax_query][0][terms][$i]", genre.slug) }
        }.build()
        return parseArchive(client.post("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, body).asJsoup())
    }
}
