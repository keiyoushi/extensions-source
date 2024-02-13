package eu.kanade.tachiyomi.extension.en.mysticalmerries

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET

class MysticalMerries : Madara("Mystical Merries", "https://mysticalmerries.com", "en") {
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/genre/manhwa/page/$page/?m_orderby=trending", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/genre/manhwa/page/$page/?m_orderby=latest", headers)
    override fun popularMangaNextPageSelector(): String? = "div.nav-previous"
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
}
