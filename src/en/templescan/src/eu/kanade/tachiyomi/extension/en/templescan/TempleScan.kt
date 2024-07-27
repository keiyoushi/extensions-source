package eu.kanade.tachiyomi.extension.en.templescan

import eu.kanade.tachiyomi.multisrc.mangaesp.MangaEsp
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Response

class TempleScan : MangaEsp(
    "Temple Scan",
    "https://templescan.net",
    "en",
    apiBaseUrl = "https://api.templescan.com",
) {
    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override fun popularMangaRequest(page: Int) = GET("$apiBaseUrl/api/topSeries", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = json.decodeFromStream<TopSeriesResponse>(response.body.byteStream())

        val entries = (data.mensualRes + data.weekRes + data.dayRes)
            .distinctBy { it.series_slug }
            .map { it.toSManga() }

        return MangasPage(entries, false)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = json.decodeFromStream<List<BrowseSeries>>(response.body.byteStream())

        return MangasPage(data.map { it.toSManga() }, false)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(allComics)")?.data()
            ?: throw Exception(intl["comics_list_error"])

        val comicList = script.unescape()
            .substringAfter("""allComics":""")
            .substringBeforeLast("}]]")
            .let { json.decodeFromString<List<BrowseSeries>>(it) }

        



    }
}
