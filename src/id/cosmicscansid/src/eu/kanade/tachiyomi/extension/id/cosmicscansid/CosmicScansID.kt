package eu.kanade.tachiyomi.extension.id.cosmicscansid

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.select.Elements
import kotlin.time.Duration.Companion.seconds

class CosmicScansID : MangaThemesia(
    "CosmicScans.id",
    "https://cosmic345.co",
    "id",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4.seconds)
        .build()

    override val hasProjectPage = true

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isBlank()) {
            return super.searchMangaRequest(page, query, filters)
        }

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("page/$page/")
            .addQueryParameter("s", query)

        return GET(url.build())
    }

    override fun searchMangaSelector() = ".bixbox:not(.hothome):has(.hpage) .utao .uta .imgu, .bixbox:not(.hothome) .listupd .bs .bsx"

    // manga details
    override val seriesDescriptionSelector = ".entry-content[itemprop=description] :not(a,p:has(a))"
    override fun Elements.imgAttr(): String = this.first()?.imgAttr() ?: ""

    // pages
    override val pageSelector = "div#readerarea img:not(noscript img):not([alt=''])"
}
