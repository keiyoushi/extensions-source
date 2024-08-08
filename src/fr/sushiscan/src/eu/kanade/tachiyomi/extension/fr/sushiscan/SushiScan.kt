package eu.kanade.tachiyomi.extension.fr.sushiscan

import android.app.Application
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class SushiScan :
    MangaThemesia(
        "Sushi-Scan",
        "https://sushiscan.net",
        "fr",
        mangaUrlDirectory = "/catalogue",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.FRENCH),
    ),
    ConfigurableSource {

    private val preferences = Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }

    override val client: OkHttpClient = super.client.newBuilder()
        .setRandomUserAgent(
            preferences.getPrefUAType(),
            preferences.getPrefCustomUA(),
        )
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .addCustomUA()
        .set("Referer", "$baseUrl$mangaUrlDirectory")

    override val altNamePrefix = "Nom alternatif : "
    override val seriesAuthorSelector = ".infotable tr:contains(Auteur) td:last-child"
    override val seriesStatusSelector = ".infotable tr:contains(Statut) td:last-child"
    override fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        this.contains("En Cours", ignoreCase = true) -> SManga.ONGOING
        this.contains("Terminé", ignoreCase = true) -> SManga.COMPLETED
        this.contains("Abandonné", ignoreCase = true) -> SManga.CANCELLED
        this.contains("En Pause", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/page/$page".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(document: Document): SManga =
        super.mangaDetailsParse(document).apply {
            status = document.select(seriesStatusSelector).text().parseStatus()
        }

    override fun pageListParse(document: Document): List<Page> {
        val scriptContent = document.selectFirst("script:containsData(ts_reader)")?.data()
            ?: return super.pageListParse(document)
        val jsonString = scriptContent.substringAfter("ts_reader.run(").substringBefore(");")
        val tsReader = json.decodeFromString<TSReader>(jsonString)
        val imageUrls = tsReader.sources.firstOrNull()?.images ?: return emptyList()
        return imageUrls.mapIndexed { index, imageUrl -> Page(index, document.location(), imageUrl.replace("http://", "https://")) }
    }

    private fun Headers.Builder.addCustomUA(): Headers.Builder {
        preferences.getPrefCustomUA()
            .takeIf { !it.isNullOrBlank() }
            ?.let { set("User-Agent", it) }
        return this
    }

    @Serializable
    data class TSReader(
        val sources: List<ReaderImageSource>,
    )

    @Serializable
    data class ReaderImageSource(
        val source: String,
        val images: List<String>,
    )
}
