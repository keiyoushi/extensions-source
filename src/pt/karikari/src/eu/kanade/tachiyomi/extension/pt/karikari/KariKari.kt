package eu.kanade.tachiyomi.extension.pt.karikari

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class KariKari : KeiSource() {

    private val apiHeaders
        get() = headersBuilder()
            .set("apikey", API_KEY)
            .set("Authorization", "Bearer $API_KEY")
            .build()

    override suspend fun getPopularManga(page: Int) = fetchWorks(page, POPULAR_ORDER)

    override suspend fun getLatestUpdates(page: Int) = fetchWorks(page, LATEST_ORDER)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = fetchWorks(page, filters.firstInstance<SortFilter>().order, query, filters)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = when (url.pathSegments.first()) {
            "obra" -> url.pathSegments.getOrNull(1)
            // The reader links back to its work, also when it asks for an account instead of showing the pages
            "ler" ->
                client.get(url, Fetch.DOCUMENT).asJsoup()
                    .selectFirst("a[href^='/obra/']")?.attr("href")?.substringAfter("/obra/")
            else -> null
        } ?: return null
        val work = client.get("$baseUrl/obra/$slug", Fetch.DOCUMENT).asJsoup().extractNextJs<WorkInfoDto>()!!
        return work.obra.toSManga().takeUnless { work.ehNovel }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val workPage = fetchWorkPage(manga.url)
        return SMangaUpdate(workPage.obra.toSManga(), workPage.toSChapterList())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // Chapters of +18 works without an account that confirmed its age, and paid chapters, come without pages
        val pages = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
            .extractNextJs<ReaderDto> { it is JsonObject && (it["capitulo"] as? JsonObject)?.containsKey("capitulos_paginas") == true }
            ?.pages.orEmpty()
        if (pages.isEmpty()) throw Exception("Log in to the site in WebView to read this chapter: +18 works need an account that confirmed its age, and paid chapters must be unlocked")
        return pages.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        GenreFilter(),
        StatusFilter(),
        AgeRatingFilter(),
    )

    // Lists come from the site's Supabase database, queried like its category pages do, with the site's public key
    private suspend fun fetchWorks(page: Int, order: String, query: String = "", filters: FilterList = FilterList()): MangasPage {
        val url = "$API_URL/obras".toHttpUrl().newBuilder()
            .addQueryParameter("select", "slug,titulo,capa_vertical_path")
            .addQueryParameter("publico", "eq.true")
            .addQueryParameter("nao_listado", "eq.false")
            // Novels are text
            .addQueryParameter("tipo", "eq.webcomic")
            .apply {
                if (query.isNotBlank()) addQueryParameter("titulo", "ilike.*${query.trim()}*")
                filters.filterIsInstance<UrlFilter>().forEach { it.addTo(this) }
            }
            // The id keeps the order stable from one page to the next
            .addQueryParameter("order", "$order,id")
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .build()
        val works = client.get(url, apiHeaders, Fetch.XHR).parseAs<List<WorkDto>>()
        return MangasPage(works.map { it.toSManga() }, works.size == PAGE_SIZE)
    }

    private suspend fun fetchWorkPage(path: String): WorkPageDto = client.get(baseUrl + path, Fetch.DOCUMENT).asJsoup().extractNextJs<WorkPageDto>()!!

    companion object {
        private const val API_URL = "https://mlkrbaaowgpamvpxiqrw.supabase.co/rest/v1"
        private const val API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1sa3JiYWFvd2dwYW12cHhpcXJ3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY3MzAzMDgsImV4cCI6MjA5MjMwNjMwOH0.AY77uVkjUYCh51o9T3DyXz1v-02sN2Kp3bRcur_JVjY"
        private const val PAGE_SIZE = 24
        private const val POPULAR_ORDER = "visualizacoes.desc"
        private const val LATEST_ORDER = "atualizado_em.desc.nullslast"
    }
}
