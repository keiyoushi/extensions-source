package eu.kanade.tachiyomi.extension.id.cosmicscansid

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import java.util.Locale
import java.util.concurrent.TimeUnit

class CosmicScansID : MangaThemesia("CosmicScans.id", "https://cosmic1.co", "id", "/semua-komik") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4, TimeUnit.SECONDS)
        .build()

    override val hasProjectPage = true
    override val projectPageString = "/semua-komik"

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl + if (page > 1) "/page/$page" else "", headers)

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("page/$page/")
            .addQueryParameter("s", query)

        filters.forEach { filter ->
            when (filter) {
                // if site has project page, default value "hasProjectPage" = false
                is ProjectFilter -> {
                    if (filter.selectedValue() == "project-filter-on") {
                        url.setPathSegment(0, projectPageString.substring(1))
                    }
                }
                else -> { /* Do Nothing */ }
            }
        }
        return GET(url.build())
    }

    override fun searchMangaSelector() = ".bixbox:not(.hothome):has(.hpage) .utao .uta .imgu, .bixbox:not(.hothome) .listupd .bs .bsx"

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Separator(),
            Filter.Header("$name Project List page"),
            ProjectFilter(intl["project_filter_title"], projectFilterOptions),
            OrderByFilter(intl["order_by_filter_title"], orderByFilterOptions),
        )
        return FilterList(filters)
    }

    // manga details
    override val seriesDescriptionSelector = ".entry-content[itemprop=description] :not(a,p:has(a))"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.selectFirst(seriesDetailsSelector)?.let { seriesDetails ->
            title = seriesDetails.selectFirst(seriesTitleSelector)!!.text()
            artist = seriesDetails.selectFirst(seriesArtistSelector)?.ownText().removeEmptyPlaceholder()
            author = seriesDetails.selectFirst(seriesAuthorSelector)?.ownText().removeEmptyPlaceholder()
            description = seriesDetails.select(seriesDescriptionSelector).joinToString("\n") { it.text() }.trim()
            // Add alternative name to manga description
            val altName = seriesDetails.selectFirst(seriesAltNameSelector)?.ownText().takeIf { it.isNullOrBlank().not() }
            altName?.let {
                description = "$description\n\n$altNamePrefix$altName".trim()
            }
            val genres = seriesDetails.select(seriesGenreSelector).map { it.text() }.toMutableList()
            // Add series type (manga/manhwa/manhua/other) to genre
            seriesDetails.selectFirst(seriesTypeSelector)?.ownText().takeIf { it.isNullOrBlank().not() }?.let { genres.add(it) }
            genre = genres.map { genre ->
                genre.lowercase(Locale.forLanguageTag(lang)).replaceFirstChar { char ->
                    if (char.isLowerCase()) {
                        char.titlecase(Locale.forLanguageTag(lang))
                    } else {
                        char.toString()
                    }
                }
            }
                .joinToString { it.trim() }

            status = seriesDetails.selectFirst(seriesStatusSelector)?.text().parseStatus()
            val thumbnail = seriesDetails.select(seriesThumbnailSelector)
            if (thumbnail.isNotEmpty()) {
                thumbnail_url = seriesDetails.select(seriesThumbnailSelector).imgAttr()
            }
        }
    }

    // pages
    override val pageSelector = "div#readerarea img:not(noscript img)"
}
