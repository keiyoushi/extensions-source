package eu.kanade.tachiyomi.extension.zh.hcomic

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.int
import keiyoushi.utils.long
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class HComic : KeiSource() {

    private val imgUrl = "https://h-comic.link/api"

    // Popular (Random)
    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$baseUrl/random/__data.json").parseAsMangaList(0)
        return MangasPage(result.first.map { it.toSManga(imgUrl) }, true)
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = client.get("$baseUrl/__data.json?page=$page").parseAsMangaList(page)
        return MangasPage(result.first.map { it.toSManga(imgUrl) }, result.second)
    }

    // Search
    override fun getFilterList(data: JsonElement?) = FilterList(RandomFilter(), TagGroup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tags = filters.firstInstance<TagGroup>().state.filter { it.state }.joinToString(",") { it.value }
        val url = "${baseUrl + filters[0]}/__data.json".toHttpUrl().newBuilder()
            .addQueryParameter("tag", tags)
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
        val result = client.get(url.build()).parseAsMangaList(page)
        return MangasPage(result.first.map { it.toSManga(imgUrl) }, result.second)
    }

    // Manga & Chapter
    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val sManga = if (fetchDetails) {
            client.get("${getMangaUrl(manga)}/__data.json").parseAsManga().toSManga(imgUrl)
        } else {
            manga
        }

        val sChapter = SChapter.create().apply {
            url = sManga.url
            name = sManga.title
            date_upload = sManga.memo["timestamp"]!!.long
            scanlator = sManga.memo["category"]!!.string
            memo = sManga.memo
        }

        return SMangaUpdate(sManga, listOf(sChapter))
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pageCount = chapter.memo["numPages"]!!.int
        val mediaId = chapter.memo["mediaId"]!!.string
        return List(pageCount) { Page(it, imageUrl = "$imgUrl/$mediaId/pages/${it + 1}") }
    }
}
