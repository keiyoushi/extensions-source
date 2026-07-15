package eu.kanade.tachiyomi.extension.en.hentai4free

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import okhttp3.Request

@Source
abstract class Hentai4Free : Madara() {
    override val useNewChapterEndpoint = true
    override val mangaSubString = "hentai"

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(
        page,
        "",
        FilterList(
            listOf(
                OrderByFilter(
                    "",
                    listOf(
                        Pair("", ""),
                        Pair("", "views"),
                    ),
                    1,
                ),
            ),
        ),
    )

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(
        page,
        "",
        FilterList(
            listOf(
                OrderByFilter(
                    "",
                    listOf(
                        Pair("", ""),
                        Pair("", "latest"),
                    ),
                    1,
                ),
            ),
        ),
    )
}
