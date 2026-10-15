package eu.kanade.tachiyomi.extension.en.megatokyo

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Source
abstract class Megatokyo : KeiSource() {

    override val supportsLatest = false

    private val dateParser = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

    override fun OkHttpClient.Builder.configureClient() = ignoreAllSSLErrors()

    @SuppressLint("CustomX509TrustManager")
    private fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
        val naiveTrustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        }

        val insecureSocketFactory = SSLContext.getInstance("TLSv1.2").apply {
            init(null, arrayOf<TrustManager>(naiveTrustManager), SecureRandom())
        }.socketFactory

        sslSocketFactory(insecureSocketFactory, naiveTrustManager)
        hostnameVerifier { _, _ -> true }
        return this
    }

    private fun createManga() = SManga.create().apply {
        setUrlWithoutDomain("/archive.php?list_by=date")
        title = "Megatokyo"
        artist = "Fred Gallagher"
        author = "Fred Gallagher"
        status = SManga.ONGOING
        description = "Relax, we understand j00"
        thumbnail_url = "https://i.ibb.co/yWQM1gY/megatokyo.png"
    }

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(createManga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) createManga() else manga
        if (!fetchChapters) return SMangaUpdate(updatedManga, chapters)

        val document = client.get(getMangaUrl(manga)).asJsoup()
        val chapterList = document.select("div.content h2:contains(Comics by Date) + div ul li a[name]")
            .map { element ->
                SChapter.create().apply {
                    url = element.attr("href")
                    chapter_number = url.substringAfterLast("/").toFloatOrNull() ?: -1f
                    name = element.text()
                    date_upload = dateParser.tryParseDate(element.attr("title").replace(ordinalRegex, "$1"))
                }
            }.reversed()

        return SMangaUpdate(updatedManga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#strip img").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("src"))
        }
    }

    companion object {
        private val ordinalRegex = "(\\d+)(st|nd|rd|th)".toRegex()
    }
}
