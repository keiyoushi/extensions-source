package eu.kanade.tachiyomi.extension.en.thegirlfromrandomchattingmangaonline

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs

@Source
abstract class TheGirlFromRandomChattingMangaOnline : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(listOf(createManga()), false)

    override fun getMangaUrl(manga: SManga) = baseUrl

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = MangasPage(
        if ("the girl from random chatting".contains(query.lowercase())) {
            listOf(createManga())
        } else {
            emptyList()
        },
        false,
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) createManga() else manga
        val updatedChapters = if (fetchChapters) {
            client.get("$baseUrl/wp-json/mg/v1/chapters-list").parseAs<ChaptersListDto>().map(ChapterDto::toSChapter)
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("p > noscript > img")
        .map { img -> img.attr("src") }
        .mapIndexed { i, src -> Page(i, imageUrl = src) }

    private suspend fun createManga(): SManga {
        val mainPage = client.get(baseUrl).asJsoup()

        return SManga.create().apply {
            url = "the-girl-from-random-chatting"
            title = "The Girl from Random Chatting"
            thumbnail_url =
                mainPage
                    .selectFirst("figure.wp-block-gallery figure.wp-block-image:last-child noscript img")
                    ?.attr("src")
            artist = "Eun Hyuk, Park"
            author = "Eun Hyuk, Park"
            status = SManga.COMPLETED
            description =
                "If you lived through – or are still living through – high school, you can relate to Joon-Woo. An outcast and a loner, his only joy comes from the hours he spends on his phone, randomly chatting with strangers. It’s all weird and meaningless, until Joon-Woo strikes gold – as he’s matched in a private chat with a pretty young girl his age. Jackpot! But when he discovers that this same pretty girl is actually his classmate Seung Ah, things get a little too real for a guy who’s never even remotely been kissed.\n(sourced from Webtoon)"
            genre = "Action, Drama, Comedy, Romance, Slice of Life, Shounen, Harem"

            // Source has already uploaded all chapters of the completed manga
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE

            // Details are provided with the first request so this prevents refetching them by themselves in fetchMangaUpdate
            initialized = true
        }
    }
}
