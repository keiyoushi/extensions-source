package eu.kanade.tachiyomi.extension.en.hentai4free

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class Hentai4Free : Madara("Hentai4Free", "https://hentai4free.net", "en") {
    override val useNewChapterEndpoint = true
    override val mangaSubString = "hentai"

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(
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

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(
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
