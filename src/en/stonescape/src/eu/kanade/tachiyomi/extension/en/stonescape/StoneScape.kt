package eu.kanade.tachiyomi.extension.en.stonescape

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import java.text.SimpleDateFormat
import java.util.Locale

class StoneScape : Madara("StoneScape", "https://stonescape.xyz", "en", SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)) {
    override val mangaSubString = "series"

    // Fix for site returning 500 error while still providing valid HTML
    override val client = super.client.newBuilder().addInterceptor { chain ->
        val res = chain.proceed(chain.request())
        if (res.code == 500) res.newBuilder().code(200).build() else res
    }.build()

    // Redirecting Browse/Latest to the correct URL
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manhwaseries/page/$page/", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manhwaseries/page/$page/?m_orderby=latest", headers)

    // Selectors for listing and details
    override fun popularMangaSelector() = "div.page-item-detail.manga"
    override val mangaDetailsSelectorAuthor = ".manga-authors a"
    override val mangaDetailsSelectorDescription = "div.manga-summary"
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"
}
