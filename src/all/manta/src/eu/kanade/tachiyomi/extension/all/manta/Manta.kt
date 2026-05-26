package eu.kanade.tachiyomi.extension.all.manta

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val DOMAIN = "manta.net"

class Manta(
    override val lang: String,
) : HttpSource() {

    override val name = "Manta"

    override val baseUrl = "https://$DOMAIN/$lang"

    override val supportsLatest = false

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val cookies = client.cookieJar.loadForRequest(url)
            val token = cookies.find { it.name == "token" }?.value

            if (token != null) {
                val newRequest = request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(request)
            }
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", "https://$DOMAIN")
        .set("Accept-Language", lang)

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "https://$DOMAIN/manta/v1/search/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("lang", lang)
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            } else {
                val category = filters.firstInstanceOrNull<Category>()
                val selected = category?.second ?: ""
                if (selected.isNotEmpty()) {
                    val (key, value) = selected.split("=")
                    addQueryParameter(key, value)
                } else {
                    addQueryParameter("tagId", "288")
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MantaResponse<List<Series<Title>>>>()
        val mangas = result.data.map { it.toSManga(lang) }
        return MangasPage(mangas, false)
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsRequest(manga: SManga) = GET("https://$DOMAIN/front/v1/series/${manga.url}?lang=$lang", headers)

    override fun mangaDetailsParse(response: Response) = response.parseAs<MantaResponse<Series<Details>>>().data.toSManga(lang)

    // ============================ Chapter List ============================

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response) = response.parseAs<MantaResponse<Series<Details>>>().data.episodes!!.map {
        it.toSChapter(lang)
    }.reversed()

    // ============================= Page List ==============================

    override fun pageListRequest(chapter: SChapter) = GET("https://$DOMAIN/front/v1/episodes/${chapter.url}?lang=$lang", headers)

    override fun pageListParse(response: Response) = response.parseAs<MantaResponse<Episode>>().data.cutImages?.mapIndexed { idx, img ->
        Page(idx, "", img.toString())
    } ?: emptyList()

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList() = FilterList(
        Filter.Header(if (lang == "es") "Los filtros se ignoran al buscar" else "Filters are ignored when searching"),
        Filter.Separator(),
        Category(lang),
        GenreFilter(lang),
    )

    // ============================= Utilities ==============================

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/episodes/${chapter.url}"
}
