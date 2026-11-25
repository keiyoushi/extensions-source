package eu.kanade.tachiyomi.extension.all.sxypix

import android.util.LruCache
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.call
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.text.Normalizer
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

@Source
abstract class SxyPix : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { it.host == baseUrl.toHttpUrl().host }
        .addInterceptor(::resignInterceptor)

    // The homepage's list
    override suspend fun getPopularManga(page: Int) = getListing(siteUrl("x", "trend", "week"), page)

    override suspend fun getLatestUpdates(page: Int) = getListing(siteUrl("x", "latest", "all"), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SortFilter>().value
        if (query.isBlank()) return getListing(siteUrl("x", sort, filters.firstInstance<PeriodFilter>().value), page)
        val key = searchKey(query).ifEmpty { return MangasPage(emptyList(), false) }
        return getListing(siteUrl("s", key, sort), page)
    }

    // The site's search box: punctuation to spaces, words joined by "-"; the site only indexes ASCII keys
    private fun searchKey(query: String) = Normalizer.normalize(query, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        .replace(SEARCH_PUNCTUATION, " ").trim().split(WHITESPACE).joinToString("-")

    private fun siteUrl(vararg segments: String): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        segments.forEach(::addPathSegment)
    }.build()

    private suspend fun getListing(url: HttpUrl, page: Int): MangasPage {
        val document = try {
            client.get(url.newBuilder().addPathSegment(page.toString()).build(), Fetch.DOCUMENT).asJsoup()
        } catch (e: HttpException) {
            // A search key the site can't index (non-Latin letters) answers 404
            if (e.code != 404 || url.pathSegments[0] != "s") throw e
            return MangasPage(emptyList(), false)
        }
        val mangas = document.select("a:has(meta[itemprop=caption])").map { link ->
            SManga.create().apply {
                this.url = link.absUrl("href").toHttpUrl().pathSegments[1]
                title = link.selectFirst("meta[itemprop=caption]")!!.attr("content")
                thumbnail_url = link.selectFirst("img.gall_cover")?.attrOrNull("abs:data-src")
            }
        }
        // Page links continue past the last page; the page script holds the result count
        val (total, perPage) = document.select("script").firstNotNullOf { PAGINATION_REGEX.find(it.data()) }.destructured
        return MangasPage(mangas, page * perPage.toInt() < total.toInt())
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!url.isSiteUrl()) return MangasPage(emptyList(), false)
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        return when {
            // /s/<key>[/<sort>], /x/<sort>/<period>, /u/<user>[/<sort>[/<period>]]
            segments.size >= 2 && segments[0] == "s" -> getListing(siteUrl("s", segments[1], segments.getOrElse(2) { "latest" }), page)
            segments.size >= 3 && segments[0] == "x" -> getListing(siteUrl("x", segments[1], segments[2]), page)
            segments.size >= 2 && segments[0] == "u" -> getListing(siteUrl("u", segments[1], segments.getOrElse(2) { "latest" }, segments.getOrElse(3) { "all" }), page)
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (!url.isSiteUrl() || segments.size < 2 || segments[0] != "w") return null
        val manga = SManga.create().apply { this.url = segments[1] }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga()
    }

    private fun HttpUrl.isSiteUrl() = host.removePrefix("www.") == baseUrl.toHttpUrl().host.removePrefix("www.")

    override fun getMangaUrl(manga: SManga) = siteUrl("w", manga.url).toString()

    override fun getChapterUrl(chapter: SChapter) = siteUrl("w", chapter.url).toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        document.selectFirst(".gnf")?.let { throw Exception(it.text()) }
        val titleElement = document.selectFirst("b.gall_title")!!
        val models = titleElement.select("a.ps_link").map { it.attr("data-subkey") to it.absUrl("href") }.distinctBy { it.first }
        val hashtags = titleElement.select("a.hash_link").map { it.text().removePrefix("#") to it.absUrl("href") }
        val uploader = document.selectFirst(".gall_cp a[href^=/u/]")?.let { link ->
            link.selectFirst(".ip_uname")?.text()?.let { it to link.absUrl("href") }
        }
        val stats = document.selectFirst(".gall_ipp")
        val grid = document.selectFirst(".gallgrid")!!
        val imageHost = grid.attr("data-x")
        val uploaderId = grid.attr("data-aid")
        // Image urls are signed and expire: a fresh, larger one of the same cover photo
        val cover = if (fetchDetails) {
            val pictures = client.call(galleryRequest(imageHost, uploaderId, manga.url)).parseAs<GalleryDto>().pictures()
            val coverId = manga.thumbnail_url?.toHttpUrlOrNull()?.pathSegments?.last()?.substringBefore('.')
            pictures.firstOrNull { it.id == coverId } ?: pictures.firstOrNull()
        } else {
            null
        }

        val details = SManga.create().apply {
            url = manga.url
            title = titleElement.text()
            author = models.joinToString { it.first }.ifEmpty { null }
            genre = hashtags.joinToString { it.first }.ifEmpty { null }
            description = MarkdownDescription.Builder()
                .info(
                    stats?.selectFirst(".ip_count b")?.text()?.let { "$it pix" },
                    stats?.selectFirst(".ip_views b")?.text()?.let { "$it views" },
                    stats?.selectFirst(".like b")?.text()?.let { "$it likes" },
                )
                .links(models, "Models")
                .links(hashtags, "Tags")
                .links(listOfNotNull(uploader), "Uploaded by")
                .build()
            thumbnail_url = cover?.mediumUrl ?: manga.thumbnail_url
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Gallery"
            date_upload = DATE_FORMAT.tryParseDate(DATE_REGEX.find(document.title())?.groupValues?.get(1))
            memo = buildJsonObject {
                put(IMAGE_HOST, imageHost)
                put(UPLOADER_ID, uploaderId)
            }
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val request = galleryRequest(chapter.memo[IMAGE_HOST]!!.string, chapter.memo[UPLOADER_ID]!!.string, chapter.url)
        return client.call(request).parseAs<GalleryDto>().pictures().mapIndexed { index, picture ->
            Page(index, imageUrl = picture.originalUrl)
        }
    }

    // The server builds the image host and path from x and aid
    private fun galleryRequest(imageHost: String, uploaderId: String, galleryId: String): Request {
        val body = FormBody.Builder()
            .add("x", imageHost)
            .add("aid", uploaderId)
            .add("ghash", galleryId)
            .build()
        return Request.Builder()
            .url(siteUrl("php", "gall.php"))
            .headers(headers)
            .post(body)
            .fetch(Fetch.XHR.from(siteUrl("w", galleryId)))
            .build()
    }

    private class Picture(val id: String, val originalUrl: String, val mediumUrl: String) {
        // Path segment 2 is the signature's expiry, in epoch seconds
        val isExpired get() = Instant.fromEpochSeconds(originalUrl.toHttpUrl().pathSegments[2].toLong()) <= Clock.System.now()
    }

    // Each block: the original the site's viewer opens (data-src), the grid's medium size (img data-src)
    private fun GalleryDto.pictures() = blocks.map { html ->
        val block = Jsoup.parseBodyFragment(html, baseUrl).selectFirst(".gall_pix_el")!!
        Picture(block.attr("data-photoid"), block.attrOrNull("abs:data-src")!!, block.selectFirst("img")!!.attrOrNull("abs:data-src")!!)
    }

    // Latest fresh pictures per gallery, so one re-sign serves every expired page of it
    private val resigned = LruCache<String, Map<String, Picture>>(5)

    // Signed image urls expire, and the app caches page lists and covers: sign them again
    private fun resignInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        // /<pixi|pixiw>/<token>/<expiry>/<size>/<aid>/<ghash>/<photo>.<ext>
        val segments = request.url.pathSegments
        if (response.code != 403 || segments.size != 7 || !segments[0].startsWith("pixi")) return response
        response.close()
        val (size, uploaderId, galleryId) = segments.subList(3, 6)
        val photoId = segments[6].substringBefore('.')
        val stale = request.url.toString()
        return try {
            val picture = synchronized(resigned) {
                resigned.get(galleryId)?.get(photoId)?.takeIf { !it.isExpired && it.originalUrl != stale && it.mediumUrl != stale }
                    ?: run {
                        val galleryRequest = galleryRequest(request.url.host.substringBefore('.'), uploaderId, galleryId)
                        val pictures = client.newCall(galleryRequest).execute().use { gallery ->
                            if (!gallery.isSuccessful) throw IOException("HTTP error ${gallery.code}")
                            gallery.parseAs<GalleryDto>().pictures().associateBy { it.id }
                        }
                        resigned.put(galleryId, pictures)
                        pictures[photoId]
                    }
            } ?: throw IOException("Picture removed from the gallery")
            chain.proceed(request.newBuilder().url(if (size == "o") picture.originalUrl else picture.mediumUrl).build())
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        PeriodFilter(),
    )

    companion object {
        private const val IMAGE_HOST = "x"
        private const val UPLOADER_ID = "aid"
        private val SEARCH_PUNCTUATION = Regex("""[`~!@#$%^&*()_|+\-=?;:'",.<>{}\[\]\\/]""")
        private val COMBINING_MARKS = Regex("""\p{Mn}+""")
        private val WHITESPACE = Regex("""\s+""")
        private val PAGINATION_REGEX = Regex("""\btotal:\s*(\d+),\s*gpp:\s*(\d+)""")
        private val DATE_REGEX = Regex("""\((\d{2}\.\d{2}\.\d{4})\) - [^-]+$""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
    }
}
