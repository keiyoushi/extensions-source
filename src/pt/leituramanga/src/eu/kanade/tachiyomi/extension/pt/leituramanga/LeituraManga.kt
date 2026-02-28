package eu.kanade.tachiyomi.extension.pt.leituramanga

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.cryptoaes.CryptoAES
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.Objects

class LeituraManga : HttpSource() {

    override val name = "Leitura Mangá"

    override val baseUrl = "https://leituramanga.net"

    private val apiUrl = "https://api.leituramanga.net"

    private val cdnUrl = "https://cdn.leituramanga.net"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 403) {
                throw IOException("Abra primeiramente algum mangá ou qualquer capítulo na WebiView")
            }
            response
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ================= Popular ==================
    override fun popularMangaRequest(page: Int) = GET("$apiUrl/api/manga/get-tops?limit=200", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaResponseDto<PopularDataDto>>()
        val mangas = result.data.topView.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, false)
    }

    // ================= Latest ==================

    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/api/manga/?sort=time&limit=24&isHome=true&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<MangaResponseDto<MangaListDto>>()
        val mangas = result.data.data.map { it.toSManga(cdnUrl) }
        val hasNext = result.data.pagination.let { it.page < it.totalPage }
        return MangasPage(mangas, hasNext)
    }
    // ================= Search ==================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/api/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "24")
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("keyword", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    if (filter.selectedValue.isNotEmpty()) {
                        url.addQueryParameter("genre", filter.selectedValue)
                    }
                }

                is StatusFilter -> {
                    if (filter.selectedValue.isNotEmpty()) {
                        url.addQueryParameter("status", filter.selectedValue)
                    }
                }

                is SortFilter -> {
                    url.addQueryParameter("sort", filter.selectedValue)
                }

                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    override fun getFilterList() = FilterList(
        GenreFilter(),
        StatusFilter(),
        SortFilter(),
    )
    // ================= Details ==================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst("h2:contains(Sinopse) +div p")?.text()
        author = document.selectFirst("h2:contains(Informações) +div p:contains(Autor)")?.text()?.substringAfter(":")
        genre = document.select("h2 + div > a[href*=genre]").joinToString { it.text() }

        status = when (document.selectFirst("h2:contains(Informações) +div p:contains(Status)")?.text()?.substringAfter(":")?.trim()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        setUrlWithoutDomain(document.location())
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url
    // ================= Chapters ==================

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val (mangaId, slug) = response.getMangaIdAndSlug()

        if (setOf(mangaId, slug).any(Objects::isNull)) return emptyList()

        val request = GET("$apiUrl/api/chapter/get-by-manga-id?mangaId=$mangaId&page=1&limit=9999", headers)

        val result = client.newCall(request).execute().parseAs<MangaResponseDto<ChapterListDto>>()

        return result.data.data.map { it.toSChapter(slug!!) }
    }

    private fun Response.getMangaIdAndSlug(): Pair<String?, String?> {
        val document = asJsoup()
        val script = document.selectFirst("script:containsData(mangaId)")?.data() ?: return Pair(null, null)
        return MANGA_ID_REGEX.find(script)?.groupValues?.last() to MANGA_SLUG_REGEX.find(script)?.groupValues?.last()
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url
    // ================= Pages ==================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val password = buildString {
            document.selectFirst("[name=apple-mobile-web-app-id]")
                ?.attr("content")
                ?.let(::append)

            document.select("script").mapNotNull(Element::data)
                .firstOrNull { MANGA_ID_REGEX.containsMatchIn(it) }
                ?.let { AES_KEY_PART_REGEX.find(it)?.groupValues?.last() }
                ?.let(::append)

            document.selectFirst(".chapter-reading-container")
                ?.attr("style")
                ?.substringAfter("'")
                ?.substringBeforeLast("'")
                ?.let(::append)
        }

        val script = QuickJs.create().use {
            it.evaluate(
                """
                globalThis.self = globalThis;
                ${document.select("script:containsData(self.__next_f)").joinToString("\n", transform = Element::data)}
                self.__next_f.map(it => it[it.length - 1]).join('')
                """.trimIndent(),
            ) as String
        }

        val content = ENCRYPTED_CONTENT_REGEX.find(script)?.groupValues?.last()
            ?.let { CryptoAES.decrypt(it, password) }
            ?: return emptyList()

        return content.parseAs<List<ImageDto>>().mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl(cdnUrl))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val MANGA_ID_REGEX = """mangaId[^:]+[^"]+"([^")]+)\\"""".toRegex()
        private val MANGA_SLUG_REGEX = """mangaSlug[^:]+[^"]+"([^"]+)\\""".toRegex()
        private val AES_KEY_PART_REGEX = """"id[^:]+[^"]+"([^"]+)\\""".toRegex()

        // 'U2FsdGVkX1' is the Base64 representation of the 'Salted__' prefix.
        // It indicates the data was encrypted using CryptoJS
        private val ENCRYPTED_CONTENT_REGEX = """U2FsdGVkX1[^=]+=+""".toRegex()
    }
}
