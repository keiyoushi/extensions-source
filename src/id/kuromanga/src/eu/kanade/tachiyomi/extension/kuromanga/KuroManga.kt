package eu.kanade.tachiyomi.extension.id.kuromanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class KuroManga :
    MangaThemesia(
        "Kuro Manga",
        "https://kuromanga.me",
        "id",
        dateFormat = KMdateFormat,
    ) {

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage {
        val page = super.searchMangaParse(response)

        val mangas = page.mangas.filterNot { manga ->
            manga.url.contains("/novel/")
        }

        return MangasPage(mangas, page.hasNextPage)
    }

    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()
}

private val KMdateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id")).apply {
    timeZone = TimeZone.getTimeZone("Asia/Jakarta")
}
