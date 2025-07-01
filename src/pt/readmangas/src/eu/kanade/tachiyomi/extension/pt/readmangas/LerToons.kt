package eu.kanade.tachiyomi.extension.pt.readmangas

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class LerToons() : HttpSource() {

    override val name = "Ler Toons"

    override val baseUrl = "https://lertoons.com"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override val versionId = 3

    // =========================== Popular ================================

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList())
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // =========================== Latest ===================================

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // =========================== Search =================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchDto>()
        val mangas = dto.mangas.map(MangaDto::toSManga)
        return MangasPage(mangas, hasNextPage = dto.hasNextPage())
    }

    // =========================== Details =================================

    override fun mangaDetailsParse(response: Response) = response.toDto<MangaDetailsDto>().toSManga()

    // =========================== Chapter =================================

    override fun chapterListParse(response: Response) = response.toDto<MangaDetailsDto>().toSChapterList()

    // =========================== Pages ===================================

    override fun pageListParse(response: Response): List<Page> = response.toDto<PageDto>().toPageList()

    override fun imageUrlParse(response: Response) = ""

    // =========================== Utilities ===============================

    inline fun <reified T> Response.toDto(): T {
        val jsonString = asJsoup().selectFirst("[data-page]")!!.attr("data-page")
        return jsonString.parseAs<T>()
    }

    @SuppressLint("SimpleDateFormat")
    companion object {
        const val CDN_URL = "https://cdn.lertoons.com"
    }
}
