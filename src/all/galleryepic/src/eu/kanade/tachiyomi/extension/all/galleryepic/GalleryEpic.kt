package eu.kanade.tachiyomi.extension.all.galleryepic

import android.graphics.Bitmap
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.imagedecoder.ImageDecoder
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.nodes.Document
import java.io.IOException
import kotlin.time.Instant

@Source
abstract class GalleryEpic : KeiSource() {

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = cosplayPage(page, "/$lang/cosplays/$page")

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = cosplayPage(page, "/$lang/cosplays/$page")

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val keyword = query.trim().takeIf { it.isNotEmpty() }
        val model = filters.firstInstanceOrNull<ModelFilter>()?.state?.trim()?.takeIf { it.isNotEmpty() }
        val cosplayer = filters.firstInstanceOrNull<CosplayerFilter>()?.state?.trim()?.takeIf { it.isNotEmpty() }

        if (filters.firstInstanceOrNull<ContentFilter>()?.album == true) {
            val name = keyword ?: model
            if (name != null) return modelPage(name, page)
            return albumPage(page, "/$lang/albums/$page")
        }

        val coser = keyword ?: cosplayer
        if (coser != null) return coserPage(coser, page)

        filters.firstInstanceOrNull<SeriesFilter>()?.parodyId?.let { parodyId ->
            return parodyPage(parodyId, page)
        }

        return cosplayPage(page, "/$lang/cosplays/$page")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val host = baseUrl.toHttpUrl().host
        if (url.host != host && url.host != "www.$host") return null

        val segments = url.pathSegments
        if (segments.size != 3 || segments[0] !in listOf("en", "zh") || segments[1] != "cosplay" || segments[2].toIntOrNull() == null) {
            return null
        }

        val manga = SManga.create().apply { this.url = url.encodedPath }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga().apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = galleryDto(getMangaUrl(manga))

        val chapter = SChapter.create().apply {
            name = "Chapter"
            url = manga.url
            chapter_number = 1f
            date_upload = Instant.tryParse(dto.createdAt)
        }

        return SMangaUpdate(dto.toSManga(manga, lang), listOf(chapter))
    }

    // ============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val dto = galleryDto(getChapterUrl(chapter))

        return dto.images.mapIndexed { index, uuid ->
            Page(index, imageUrl = "$IMAGE_CDN/$uuid")
        }
    }

    // ============================== Filters ==============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/$lang/parodies", Fetch.DOCUMENT).asJsoup()

        return ParodyListDto(
            document.select("a[href^=\"/$lang/parody/\"]").mapNotNull { anchor ->
                val id = anchor.attr("href").substringAfterLast('/').toIntOrNull() ?: return@mapNotNull null
                ParodyFilterDto(id, anchor.selectFirst("p")?.ownTextOrNull() ?: return@mapNotNull null)
            },
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val parodies = data?.parseAs<ParodyListDto>()?.parodies.orEmpty().sortedBy { it.name.lowercase() }
        return FilterList(
            ContentFilter(),
            SeriesFilter(parodies),
            CosplayerFilter(),
            ModelFilter(),
        )
    }

    // ============================== Utilities ============================

    private suspend fun cosplayPage(page: Int, path: String): MangasPage {
        val document = client.get("$baseUrl$path", Fetch.DOCUMENT).asJsoup()

        val mangas = document.select("a[href^=\"/$lang/cosplay/\"]").mapNotNull { anchor ->
            val cover = anchor.selectFirst("img") ?: return@mapNotNull null
            SManga.create().apply {
                url = anchor.absUrl("href").removePrefix(baseUrl)
                title = cover.attr("alt")
                thumbnail_url = cover.absUrl("src")
            }
        }

        return MangasPage(mangas, hasNextPage(document, page))
    }

    private suspend fun coserPage(query: String, page: Int): MangasPage {
        val url = "$baseUrl/$lang/cosers/1".toHttpUrl().newBuilder()
            .addQueryParameter("coserName", query)
            .build()

        val cards = client.get(url, Fetch.DOCUMENT).asJsoup().select("a[href^=\"/$lang/coser/\"]")
        val match = cards.firstOrNull { it.selectFirst("h3")?.ownText() == query }
            ?: cards.firstOrNull()
            ?: return MangasPage(emptyList(), false)

        val path = match.absUrl("href").removePrefix(baseUrl).substringBeforeLast('/')
        return cosplayPage(page, "$path/$page")
    }

    private suspend fun parodyPage(parodyId: Int, page: Int): MangasPage {
        val path = "$lang/parody/$parodyId" + if (page > 1) "/$page" else ""
        val document = client.get("$baseUrl/$path", Fetch.DOCUMENT).asJsoup()

        val semaphore = Semaphore(3)
        val mangas = coroutineScope {
            document.select("a[href^=\"/$lang/cosplay/\"]").map { anchor ->
                async {
                    semaphore.withPermit {
                        val url = anchor.absUrl("href").removePrefix(baseUrl)
                        val cover = runCatching {
                            galleryDto("$baseUrl$url").let { dto ->
                                dto.cover.takeIf { it.isNotBlank() && it != "-" } ?: dto.images.firstOrNull()
                            }
                        }.getOrNull()
                        SManga.create().apply {
                            this.url = url
                            title = anchor.text()
                            thumbnail_url = cover?.let { "$IMAGE_CDN/$it" }
                        }
                    }
                }
            }.awaitAll()
        }

        val hasNextPage = document.select("a[href^=\"/$lang/parody/$parodyId/\"]").any { link ->
            val number = link.absUrl("href").substringAfterLast('/').toIntOrNull()
            number != null && number > page
        }

        return MangasPage(mangas, hasNextPage)
    }

    private suspend fun albumPage(page: Int, path: String): MangasPage {
        val document = client.get("$baseUrl$path", Fetch.DOCUMENT).asJsoup()

        val mangas = document.select("a[href^=\"/$lang/album/\"]").mapNotNull { anchor ->
            val cover = anchor.selectFirst("img") ?: return@mapNotNull null
            SManga.create().apply {
                url = anchor.absUrl("href").removePrefix(baseUrl)
                title = anchor.selectFirst("h3")?.ownTextOrNull() ?: cover.attr("alt")
                thumbnail_url = cover.absUrl("src")
            }
        }

        return MangasPage(mangas, hasNextPage(document, page))
    }

    private suspend fun modelPage(query: String, page: Int): MangasPage {
        val url = "$baseUrl/$lang/models/1".toHttpUrl().newBuilder()
            .addQueryParameter("modelName", query)
            .build()

        val cards = client.get(url, Fetch.DOCUMENT).asJsoup().select("a[href^=\"/$lang/model/\"]")
        val match = cards.firstOrNull { it.selectFirst("h3")?.ownText() == query }
            ?: cards.firstOrNull()
            ?: return MangasPage(emptyList(), false)

        val path = match.absUrl("href").removePrefix(baseUrl).substringBeforeLast('/')
        return albumPage(page, "$path/$page")
    }

    private fun hasNextPage(document: Document, page: Int) = document.select("a[data-slot=\"pagination-link\"]").any { link ->
        val number = link.absUrl("href").substringAfterLast('/').toIntOrNull()
        number != null && number > page
    }

    private suspend fun galleryDto(url: String): GalleryDto {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()

        return if ("/cosplay/" in url) {
            document.extractNextJs<CosplayDto> { it is JsonObject && "images" in it }
        } else {
            document.extractNextJs<AlbumDto> { it is JsonObject && "images" in it }
        } ?: throw IOException("Unable to parse gallery details")
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(avifInterceptor())

    private val hasNewDecoder by lazy { runCatching { Class.forName("ca.mpreg.imagedecoder.ImageDecoder") }.isSuccess }

    private fun avifInterceptor() = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (hasNewDecoder || !response.isSuccessful || response.header("Content-Type")?.startsWith("image/avif") != true) {
            return@Interceptor response
        }

        val bitmap = ImageDecoder.decode(response.body.bytes())

        val buffer = Buffer()
        bitmap.compress(Bitmap.CompressFormat.WEBP, 90, buffer.outputStream())
        bitmap.recycle()

        response.newBuilder().body(buffer.asResponseBody("image/webp".toMediaType())).build()
    }
}
