package eu.kanade.tachiyomi.extension.pt.bryaoi

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class BRYaoi : KeiSource() {

    override val supportsLatest = false

    // ====================== Popular ===============================

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    // ====================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ====================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/yaoi/page/$page".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select(".listagem .item a").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h2")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
        return MangasPage(mangas, hasNextPage = document.selectFirst(".next.page-numbers") != null)
    }

    // ====================== Details ================================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
                .substringAfter("Ler").substringBeforeLast("Online")
                .trim()
            thumbnail_url = document.selectFirst(".serie-capa img")?.absUrl("src")
            description = document.select(".serie-texto p").joinToString("\n") { it.text() }
            genre = document.select(".serie-infos a").joinToString { it.text() }

            setUrlWithoutDomain(document.location())
        }

        // ====================== Chapters ================================

        val updatedChapters = document.select(".capitulos a").map { element ->
            SChapter.create().apply {
                name = element.text()
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }.reversed()

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // ====================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup().select("#images_all img").mapIndexed { index, element ->
        Page(index, imageUrl = element.absUrl("src"))
    }
}
