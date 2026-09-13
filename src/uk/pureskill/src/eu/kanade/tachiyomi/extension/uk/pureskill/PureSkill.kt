package eu.kanade.tachiyomi.extension.uk.pureskill

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.array
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class PureSkill : KeiSource() {

    // =========================== Popular ============================
    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList()

    // =========================== Latest ============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = coroutineScope {
        val titlesDeferred = async {
            client.get("$baseUrl/data/catalog.js").use {
                it.body.string()
                    .substringAfter("window.CATALOG_DATA = ")
                    .substringBefore(";")
                    .parseAs<SearchResponse>()
                    .titles
            }
        }

        val latestDeferred = async {
            client.get("$baseUrl/data/last-updated.js").use {
                it.body.string()
                    .substringAfter("window.LAST_UPDATED = ")
                    .substringBefore(";")
                    .parseAs<Map<String, Int>>()
            }
        }

        val titles = titlesDeferred.await()
        val latest = latestDeferred.await()

        val manga = titles.map { it.toSManga(baseUrl) }.sortedByDescending { latest[it.url] }

        MangasPage(manga, false)
    }

    // =========================== Search ============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(query)

    // =========================== Popular/Search Utilities ============================
    private suspend fun getMangaList(query: String? = null): MangasPage {
        val titles = client.get("$baseUrl/data/catalog.js").use {
            it.body.string()
                .substringAfter("window.CATALOG_DATA = ")
                .substringBefore(";")
                .parseAs<SearchResponse>()
                .titles
        }

        val manga = if (!query.isNullOrBlank()) {
            titles.filter { it.title.contains(query, ignoreCase = true) }
        } else {
            titles
        }.map { it.toSManga(baseUrl) }

        return MangasPage(manga, false)
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.queryParameter("title")?.isNotBlank() == true) {
            val tmpManga = SManga.create().apply {
                this.url = url.queryParameter("title").toString()
            }

            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }

        return null
    }

    // =========================== Manga ============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/chapters?title=${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = manga.url
        val data = client.get("$baseUrl/cubari/$mangaUrl.json").parseAs<MangaFull>()

        val newManga = data.toSManga(mangaUrl)
        val newChapters = data.toSChapters(mangaUrl).asReversed()

        return SMangaUpdate(newManga, newChapters)
    }

    // =========================== Pages ============================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader?title=${chapter.memo["mangaId"]!!.string}&chapter=${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = chapter.memo["pages"]!!.array.mapIndexed { index, element ->
        Page(index, imageUrl = element.string)
    }
}
