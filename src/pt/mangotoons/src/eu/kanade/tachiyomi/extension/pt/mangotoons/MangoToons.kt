package eu.kanade.tachiyomi.extension.pt.mangotoons

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangoToons : KeiSource() {

    private val apiUrl = "https://api.mangotoons.com/api/v1"

    // the page manifest answers 429 (Retry-After: 10) from about 12 requests a minute
    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 5.seconds) { it.encodedPath.endsWith("/manifest") }
        .rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage = mangaList(page) { addQueryParameter("ordenar", "populares") }

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaList(page) { addQueryParameter("ordenar", "recentes") }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = mangaList(page) {
        query.takeIf { it.isNotBlank() }?.let { addQueryParameter("busca", it) }
    }

    private suspend fun mangaList(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", PAGE_LIMIT.toString())
            .apply(params)
            .build()

        return client.get(url).parseAs<MangaListDto>().toMangasPage(baseUrl)
    }

    // details and chapters come from the same endpoint
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get("$apiUrl/obras/${manga.url.mangaId()}").parseAs<MangaDto>()

        return SMangaUpdate(dto.toSManga(baseUrl), dto.chapterList())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangaId = chapter.url.mangaId()
        val number = chapter.url.chapterNumber()

        return client.get("$apiUrl/leitor/obras/$mangaId/capitulos/$number/manifest")
            .parseAs<ManifestDto>()
            .toPageList()
    }

    // the library stores /obra/{id} and /obra/{id}/capitulo/{numero}; older entries may carry ?slug=
    private fun String.mangaId(): String = substringAfter("/obra/").substringBefore("/").substringBefore("?")

    private fun String.chapterNumber(): String = substringBefore("?").substringAfterLast("/")

    companion object {
        private const val PAGE_LIMIT = 24
    }
}
