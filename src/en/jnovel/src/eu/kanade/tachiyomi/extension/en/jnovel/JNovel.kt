package eu.kanade.tachiyomi.extension.en.jnovel

import android.net.Uri
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

class JNovel : HttpSource() {
    override val name = "J-Novel"
    override val baseUrl = "https://j-novel.club"
    override val lang = "en"
    override val supportsLatest = false

    private val viewerUrl = "https://labs.j-novel.club/embed/v2"

    private inline fun <reified T> Response.parseAsProto(): T = ProtoBuf.decodeFromByteArray(body.bytes())
    private inline fun <reified T : Any> T.toRequestBodyProto(): RequestBody = ProtoBuf.encodeToByteArray(this).toRequestBody("application/protobuf".toMediaType())

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("type", "manga")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val seriesElements = document.select("div.fkd1bc")

        val mangas = seriesElements.mapNotNull {
            SManga.create().apply {
                title = it.selectFirst("h2")!!.text()
                url = it.selectFirst("a[href^='/series/']")!!.attr("href")
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }

        val nextButton = document.selectFirst("div.button:has(div.text:contains(Next))")
        val hasNextPage = nextButton != null && !nextButton.classNames().contains("disabled")

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("type", "manga")

        if (query.isNotEmpty()) {
            url.addQueryParameter("search", query)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("meta[property='og:title']")!!.attr("content")
            description = document.selectFirst("meta[name='description']")?.attr("content")
            thumbnail_url = document.selectFirst("meta[property='og:image']")?.absUrl("content")

            val authors = document.select("meta[property='book:author']")
                .mapNotNull {
                    it.attr("content")
                        .substringAfter("search=")
                        .substringAfter(":")
                        .replace("\"", "")
                        .trim()
                        .takeIf { name -> name.isNotEmpty() }
                }
            author = authors.distinct().joinToString()
            genre = document.select("meta[property='book:tag']").joinToString { it.attr("content") }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        val volumeElements = document.select("div.f1vdb00x")
        volumeElements.forEach {
            val volName = it.selectFirst("h2 a")?.text() ?: "Volume"
            val partLinks = it.select("div.f12k8ro3 a")

            partLinks.forEach { link ->
                val url = link.attr("href")
                val partName = link.text()
                val title = "$volName $partName"

                chapters.add(
                    SChapter.create().apply {
                        name = title
                        this.url = url
                    },
                )
            }
        }
        return chapters.reversed().distinctBy { it.url }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val embedUrl = document.selectFirst("iframe[src^='$viewerUrl']")!!.absUrl("src")
        val embedUrlRequest = GET(embedUrl, headers)
        val embedUrlResponse = client.newCall(embedUrlRequest).execute()
        val embedDocument = embedUrlResponse.asJsoup()
        val manifestUrlStr = embedDocument.body().absUrl("data-e4p-manifest")
        val manifestRequest = GET(manifestUrlStr, headers)
        val manifestResponse = client.newCall(manifestRequest).execute()
        val ticket = manifestResponse.parseAsProto<E4PQSTicket>()
        TODO()
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
