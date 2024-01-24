package eu.kanade.tachiyomi.extension.pt.tsukimangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class TsukiMangas : HttpSource() {

    override val name = "Tsuki Mangás"

    override val baseUrl = "https://tsuki-mangas.com"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client by lazy {
        network.client.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), 2)
            .build()
    }

    private val json: Json by injectLazy()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun popularMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val path = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/obra/$path"))
                .asObservableSuccess()
                .map(::searchMangaByPathParse)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaByPathParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response)
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        throw UnsupportedOperationException()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        throw UnsupportedOperationException()
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        throw UnsupportedOperationException()
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        throw UnsupportedOperationException()
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    // ============================= Utilities ==============================
    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }

    companion object {
        const val PREFIX_SEARCH = "path:"
    }
}
