package eu.kanade.tachiyomi.extension.all.leagueoflegends

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import kotlin.time.Instant

@Source
abstract class LOLUniverse : KeiSource() {

    private val siteLang: String
        get() = baseUrl.substringAfter("leagueoflegends.com/").substringBefore("/comic/")

    override val supportsLatest = false

    override fun Headers.Builder.configureHeaders() = set("Origin", UNIVERSE_URL).set("Referer", "$UNIVERSE_URL/")

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$MEEPS_URL/$siteLang/comics/index.json")
        .parseAs<LOLHub>()
        .mapNotNull {
            SManga.create().apply {
                title = it.title ?: return@mapNotNull null
                url = it.toString()
                description = it.description!!.clean()
                thumbnail_url = it.background.toString()
                genre = it.subtitle ?: it.champions?.joinToString()
            }
        }.run { MangasPage(this, false) }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getPopularManga(page).filter(query)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        if ('/' !in manga.url) {
            val issues = client.get("$MEEPS_URL/$siteLang/comics/${manga.url}/index.json").parseAs<LOLIssues>()
            val updatedChapters = coroutineScope {
                issues.map {
                    async {
                        SChapter.create().apply {
                            name = it.title!!
                            url = it.toString()
                            chapter_number = it.index ?: -1f
                            date_upload = fetchDate()
                        }
                    }
                }.awaitAll()
            }
            return SMangaUpdate(manga, updatedChapters)
        }

        val chapter = SChapter.create().apply {
            url = manga.url
            name = "One Shot"
            chapter_number = 0f
            date_upload = fetchDate()
        }
        return SMangaUpdate(manga, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = fetchPages(chapter).mapIndexed { idx, img ->
        Page(idx, imageUrl = img.toString())
    }

    private suspend fun fetchPages(chapter: SChapter) = client.get("$COMICS_URL/$siteLang/${chapter.url}/index.json").parseAs<LOLPages>()

    // The chapter date is only available in the page list
    private suspend fun SChapter.fetchDate() = Instant.tryParse(fetchPages(this).date)

    private fun String.clean() = replace("</p> ", "</p>").replace("</p>", "\n").replace("<p>", "")

    private fun MangasPage.filter(query: String) = copy(
        mangas.filter {
            it.title.contains(query, true) ||
                it.genre?.contains(query, true) ?: false
        },
    )

    companion object {
        private const val UNIVERSE_URL = "https://universe.leagueoflegends.com"

        private const val MEEPS_URL = "https://universe-meeps.leagueoflegends.com/v1"

        private const val COMICS_URL = "https://universe-comics.leagueoflegends.com/comics"
    }
}
