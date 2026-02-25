package eu.kanade.tachiyomi.extension.en.stonescape

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class StoneScape : Madara("StoneScape", "https://stonescape.xyz", "en", SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)) {
    override val mangaSubString = "series"

    override val client: OkHttpClient = super.client.newBuilder().addInterceptor { chain ->
        val res = chain.proceed(chain.request())
        val url = res.request.url.toString()
        if (res.code == 500 && (url.contains("/$mangaSubString/") || url.contains("/manhwaseries/"))) {
            res.newBuilder().code(200).build()
        } else res
    }.build()

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manhwaseries/page/$page/", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manhwaseries/page/$page/?m_orderby=latest", headers)

    override fun popularMangaSelector() = "div.page-item-detail.manga"
    override val mangaDetailsSelectorAuthor = ".author.meta a"
    override val mangaDetailsSelectorDescription = ".manga-summary"

    override val chapterUrlSelector = "li > a"
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"
}
