package eu.kanade.tachiyomi.extension.id.softkomik

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Softkomik : HttpSource() {
    override val name = "Softkomik"
    override val baseUrl = "https://softkomik.com"
    override val lang = "id"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageInterceptor)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ======================== Popular ========================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/komik/library".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "popular")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ======================== Latest ========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/komik/library".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "newKomik")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // ======================== Search ========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/komik/list".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .addQueryParameter("page", page.toString())
        } else {
            "$baseUrl/komik/library".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
        }

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> url.addQueryParameter("status", filter.selected)
                is TypeFilter -> url.addQueryParameter("type", filter.selected)
                is GenreFilter -> url.addQueryParameter("genre", filter.selected)
                is SortFilter -> url.addQueryParameter("sortBy", filter.selected)
                is MinChapterFilter -> url.addQueryParameter("min", filter.state)
                else -> {}
            }
        }

        if (query.isNotEmpty()) {
            url.setQueryParameter("sortBy", "")
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val libData = response.extractNextJs<LibDataDto>()
            ?: throw Exception("Could not find library data")

        val mangas = libData.data.map { manga ->
            SManga.create().apply {
                setUrlWithoutDomain(manga.title_slug)
                title = manga.title
                thumbnail_url = "$coverUrl/${manga.gambar.removePrefix("/")}"
            }
        }
        return MangasPage(mangas, libData.page < libData.maxPage)
    }

    // ======================== Details ========================
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = response.extractNextJs<MangaDetailsDto>()
            ?: throw Exception("Could not find manga details")

        val slug = response.request.url.pathSegments.lastOrNull()!!
        return SManga.create().apply {
            setUrlWithoutDomain(slug)
            title = manga.title
            author = manga.author
            description = manga.sinopsis
            genre = manga.Genre?.joinToString()
            status = when (manga.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "tamat" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = "$coverUrl/${manga.gambar.removePrefix("/")}"
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    // ======================== Chapters ========================
    override fun chapterListRequest(manga: SManga): Request {
        val url = "$apiUrl/komik/${manga.url}/chapter?limit=9999999"
        return GET(url, apiHeaders())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<ChapterListDto>()
        val slug = response.request.url.pathSegments[1]
        return dto.chapter.map { chapter ->
            val chapterNum = chapter.chapter.toFloatOrNull() ?: -1f
            val displayNum = formatChapterDisplay(chapter.chapter)
            SChapter.create().apply {
                url = "/$slug/chapter/${chapter.chapter}"
                name = "Chapter $displayNum"
                chapter_number = chapterNum
            }
        }.sortedByDescending { it.chapter_number }
    }

    private fun formatChapterDisplay(chapterStr: String): String {
        val floatVal = chapterStr.toFloatOrNull() ?: return chapterStr
        return if (floatVal == floatVal.toLong().toFloat()) {
            floatVal.toLong().toString()
        } else {
            floatVal.toString().trimEnd('0').trimEnd('.')
        }
    }

    // ======================== Pages ========================
    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<ChapterPageDataDto>()
            ?: throw Exception("Could not find chapter data")

        val imageSrc = if (data.imageSrc.isEmpty()) {
            val slug = response.request.url.pathSegments[0]
            val chapter = response.request.url.pathSegments[2]
            val url = "$apiUrl/komik/$slug/chapter/$chapter/img/${data._id}"
            client.newCall(GET(url, apiHeaders())).execute().use {
                it.parseAs<ChapterPageImagesDto>().imageSrc
            }
        } else {
            data.imageSrc
        }

        if (imageSrc.isEmpty()) {
            throw Exception("No pages found")
        }

        val imageBaseUrl = if (data.storageInter2) cdnUrls[2] else cdnUrls[0]

        return imageSrc.mapIndexed { i, img ->
            Page(i, imageUrl = "$imageBaseUrl/${img.removePrefix("/")}")
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Referer", "$baseUrl/")
            .set("Origin", baseUrl)
            .build()
        return GET(page.imageUrl!!, newHeaders)
    }

    // ============================= Utilities ==============================

    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.isSuccessful || !request.url.encodedPath.contains("img-file")) {
            return response
        }

        val imgPath = request.url.toString().substringAfter("img-file/")
        val otherHosts = cdnUrls.filter { !request.url.toString().contains(it) }

        var latestResponse = response
        for (newHost in otherHosts) {
            latestResponse.close()
            val newUrl = "$newHost/img-file/$imgPath".toHttpUrl()
            latestResponse = chain.proceed(request.newBuilder().url(newUrl).build())
            if (latestResponse.isSuccessful) return latestResponse
        }

        return latestResponse
    }

    private fun apiHeaders(): Headers {
        val exp = System.currentTimeMillis() + 300_000
        val token = Base64.encodeToString("{\"exp\":$exp}".toByteArray(), Base64.NO_WRAP)
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec("Bt9kCNItFPPHWCsHjz7LwNB7".toByteArray(), "HmacSHA256")
        mac.init(secretKey)
        val sign = mac.doFinal(token.toByteArray()).joinToString("") { "%02x".format(it) }

        return headersBuilder()
            .add("X-Token", token)
            .add("X-Sign", sign)
            .build()
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Filter tidak bisa digabungkan dengan pencarian teks."),
        Filter.Separator(),
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
        MinChapterFilter(),
    )

    private val apiUrl = "https://v2.softdevices.my.id"
    private val coverUrl = "https://cover.softdevices.my.id/softkomik-cover"
    private val cdnUrls = listOf(
        "https://f1.softkomik.com/file/softkomik-image",
        "https://img.softdevices.my.id/softkomik-image",
        "https://image.softkomik.com/softkomik",
    )
}
