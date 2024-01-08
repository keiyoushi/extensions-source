package eu.kanade.tachiyomi.extension.all.asurascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class AsuraScansTr : MangaThemesia(
    "Asura Scans",
    "https://armoniscans.com",
    "tr",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale("tr")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 3, TimeUnit.SECONDS)
        .build()

    override val seriesArtistSelector = ".fmed b:contains(Çizer)+span"
    override val seriesAuthorSelector = ".fmed b:contains(Yazar)+span"
    override val seriesStatusSelector = ".imptdt:contains(Durum) i"
    override val seriesTypeSelector = ".imptdt:contains(Tür) a"

    override val altNamePrefix: String = "Alternatif isim: "

    override fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        this.contains("Devam Ediyor", ignoreCase = true) -> SManga.ONGOING
        this.contains("Tamamlandı", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    override fun pageListParse(document: Document): List<Page> {
        val scriptContent = document.selectFirst("script:containsData(ts_reader)")?.data()
            ?: return super.pageListParse(document)
        val jsonString = scriptContent.substringAfter("ts_reader.run(").substringBefore(");")
        val tsReader = json.decodeFromString<TSReader>(jsonString)
        val imageUrls = tsReader.sources.firstOrNull()?.images ?: return emptyList()
        return imageUrls.mapIndexed { index, imageUrl -> Page(index, document.location(), imageUrl) }
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
