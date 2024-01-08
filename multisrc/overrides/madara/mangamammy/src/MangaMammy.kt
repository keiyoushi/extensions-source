package eu.kanade.tachiyomi.extension.ru.mangamammy

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class MangaMammy : Madara(
    "Manga Mammy",
    "https://mangamammy.ru",
    "ru",
    dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT),
) {
    override val useNewChapterEndpoint = true

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
