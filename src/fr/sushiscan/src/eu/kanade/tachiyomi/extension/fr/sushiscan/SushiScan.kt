package eu.kanade.tachiyomi.extension.fr.sushiscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class SushiScan : MangaThemesia() {
    override val mangaUrlDirectory = "/catalogue"
    override val datePattern = "dd MMMM yyyy"

    private val preferences = getPreferences()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2, 1.seconds)

    override val altNamePrefix = "Nom alternatif : "
    override val seriesAuthorSelector = ".infotable tr:contains(Auteur) td:last-child"
    override val seriesStatusSelector = ".infotable tr:contains(Statut) td:last-child"

    override suspend fun getPopularManga(page: Int) = searchMangaParse(client.get("$baseUrl/catalogue/?page=$page&order=popular").asJsoup())
    override suspend fun getLatestUpdates(page: Int) = searchMangaParse(client.get("$baseUrl/catalogue/?page=$page&order=update").asJsoup())

    override fun searchMangaUrl(page: Int, query: String) = "$baseUrl/page/$page".toHttpUrl().newBuilder()
        .addQueryParameter("s", query)
}
