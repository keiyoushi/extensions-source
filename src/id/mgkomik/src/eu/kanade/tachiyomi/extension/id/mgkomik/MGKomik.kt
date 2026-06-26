package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.network.rateLimit
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MGKomik :
    Madara(
        "MG Komik",
        "https://id.mgkomik.cc",
        "id",
        SimpleDateFormat("dd MMM yy", Locale.US),
    ) {

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val mangaSubString = "komik"
    override val chapterUrlSuffix = ""

    override fun headersBuilder() = super.headersBuilder().apply {
        set("Sec-CH-UA-Model", "\"\"")
    }

    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val isAjax = path.contains("admin-ajax.php") ||
                path.contains("wp-json") ||
                path.endsWith("/ajax/chapters")
            if (isAjax) {
                chain.proceed(
                    request.newBuilder()
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Site", "same-origin")
                        .header("Origin", baseUrl)
                        .header("Priority", "u=1, i")
                        .removeHeader("Sec-Fetch-User")
                        .removeHeader("Upgrade-Insecure-Requests")
                        .build(),
                )
            } else {
                chain.proceed(request)
            }
        }
        .rateLimit(3)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/$mangaSubString${if (page > 1) "/page/$page/" else "/"}?m_orderby=trending"
        return GET(url, headers)
    }

    override val mangaDetailsSelectorDescription = "div.description-summary div.summary__content p"

    override fun parseGenres(document: Document): List<Genre> = document.select("div.checkbox-group div.checkbox")
        .mapNotNull { cb ->
            val label = cb.selectFirst("label")?.text() ?: return@mapNotNull null
            val value = cb.selectFirst("input[type=checkbox]")?.`val`() ?: return@mapNotNull null
            if (value.matches(Regex("""^\d+[kKmM]?$"""))) return@mapNotNull null
            Genre(label, value)
        }

    // PROJECT FILTER
    class ProjectFilter : Filter.CheckBox(" Project Only", false)

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }
        val base = super.getFilterList().list.toMutableList()
        base.add(0, ProjectFilter())
        base.add(1, Filter.Separator())
        return FilterList(base)
    }

    override fun searchLoadMoreRequest(page: Int, query: String, filters: FilterList): Request {
        val projectChecked = filters.filterIsInstance<ProjectFilter>().firstOrNull()?.state == true
        if (!projectChecked) return super.searchLoadMoreRequest(page, query, filters)

        val taxQueryIdx = filters.count { filter ->
            when (filter) {
                is AuthorFilter -> filter.state.isNotBlank()
                is ArtistFilter -> filter.state.isNotBlank()
                is YearFilter -> filter.state.isNotBlank()
                is GenreList -> filter.state.any { it.state }
                else -> false
            }
        }

        val superRequest = super.searchLoadMoreRequest(page, query, filters)
        val oldBody = superRequest.body as FormBody

        val newBody = FormBody.Builder().apply {
            for (i in 0 until oldBody.size) add(oldBody.name(i), oldBody.value(i))
            add("vars[tax_query][$taxQueryIdx][taxonomy]", "wp-manga-tag")
            add("vars[tax_query][$taxQueryIdx][field]", "slug")
            add("vars[tax_query][$taxQueryIdx][terms][0]", "project")
        }.build()

        return superRequest.newBuilder().post(newBody).build()
    }
}
