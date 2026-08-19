package eu.kanade.tachiyomi.extension.en.thegirlfromrandomchattingmangaonline

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import java.lang.UnsupportedOperationException

@Source
abstract class TheGirlFromRandomChattingMangaOnline : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(
        listOf(
            SManga.create().apply {
                url = "the-girl-from-random-chatting"
                title = "The Girl from Random Chatting"
                thumbnail_url =
                    client.get(baseUrl).asJsoup()
                        .selectFirst("figure.wp-block-gallery figure.wp-block-image:last-child noscript img")!!
                        .attr("src")
                artist = "Eun Hyuk, Park"
                author = "Eun Hyuk, Park"
                status = SManga.COMPLETED
                description =
                    "If you lived through – or are still living through – high school, you can relate to Joon-Woo. An outcast and a loner, his only joy comes from the hours he spends on his phone, randomly chatting with strangers. It’s all weird and meaningless, until Joon-Woo strikes gold – as he’s matched in a private chat with a pretty young girl his age. Jackpot! But when he discovers that this same pretty girl is actually his classmate Seung Ah, things get a little too real for a guy who’s never even remotely been kissed.\n(sourced from Webtoon)"
                genre = "Drama"
            },
        ),
        false,
    )

    override fun getMangaUrl(manga: SManga) = baseUrl

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = MangasPage(emptyList(), false)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = throw UnsupportedOperationException()

    override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException()
}
