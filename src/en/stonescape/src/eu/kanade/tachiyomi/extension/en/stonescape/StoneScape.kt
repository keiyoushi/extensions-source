package eu.kanade.tachiyomi.extension.en.stonescape

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import java.text.SimpleDateFormat
import java.util.Locale

class StoneScape : Madara("StoneScape", "https://stonescape.xyz", "en", SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)) {
    override val mangaSubString = "series"

    // Fix for the HTTP 500 error mentioned in issue #13343
    override val client = super.client.newBuilder().addInterceptor { chain ->
        val res = chain.proceed(chain.request())
        if (res.code == 500) res.newBuilder().code(200).build() else res
    }.build()

    // Redirect to the correct Comics listing page
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manhwaseries/page/$page/", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manhwaseries/page/$page/?m_orderby=latest", headers)

    // Selectors matched to your provided HTML
    override fun popularMangaSelector() = "div.page-item-detail.manga"
    override val mangaDetailsSelectorAuthor = ".author.meta a"
    override val mangaDetailsSelectorDescription = ".manga-summary"
    
    // Specifically target the link with the text to avoid empty thumbnail links
    override val chapterUrlSelector = "li.wp-manga-chapter > a"
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"
}
