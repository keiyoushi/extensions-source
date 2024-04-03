package eu.kanade.tachiyomi.extension.de.toomicstop

import eu.kanade.tachiyomi.multisrc.hotcomics.HotComics
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response

class ToomicsTop : HotComics(
    "Toomics.Top",
    "de",
    "https://toomics.top",
) {
    override fun searchMangaParse(response: Response): MangasPage {
        val mangasPage = super.searchMangaParse(response)
        mangasPage.mangas.apply {
            for (i in indices) {
                this[i].url = this[i].url.replace(urlIdRegex, ".html")
            }
        }

        return mangasPage
    }

    private val urlIdRegex = Regex("""(/\w+).html""")

    override val browseList = listOf(
        Pair("Home", "en"),
        Pair("Weekly", "en/weekly"),
        Pair("New", "en/new"),
        Pair("Genre: All", "en/genres"),
        Pair("Genre: Romantik", "en/genres/Romantik"),
        Pair("Genre: Drama", "en/genres/Drama"),
        Pair("Genre: BL", "en/genres/BL"),
        Pair("Genre: Action", "en/genres/Action"),
        Pair("Genre: Schulleben", "en/genres/Schulleben"),
        Pair("Genre: Fantasy", "en/genres/Fantasy"),
        Pair("Genre: Comedy", "en/genres/Comedy"),
        Pair("Genre: Historisch", "en/genres/Historisch"),
        Pair("Genre: Sci-Fi", "en/genres/Sci-Fi"),
        Pair("Genre: Thriller", "en/genres/Thriller"),
        Pair("Genre: Horror", "en/genres/Horror"),
        Pair("Genre: Sport", "en/genres/Sport"),
        Pair("Genre: GL", "en/genres/GL"),
    )
}
