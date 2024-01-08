package eu.kanade.tachiyomi.extension.en.readallmanga

import eu.kanade.tachiyomi.multisrc.readallcomics.ReadAllComics
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document

class ReadAllManga : ReadAllComics("ReadAllManga", "https://readallmanga.com", "en") {

    override fun searchType() = "manga"

    override fun popularMangaTitleSelector() = "div > center"

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            genre = document.select(mangaDetailsGenreSelector()).text()
                .split("–").joinToString { it.trim() }
        }
    }

    override fun mangaDetailsDescriptionSelector() = ".b > span"
    override fun mangaDetailsGenreSelector() = ".b > p > strong:nth-child(8)"
    override fun mangaDetailsAuthorSelector() = ".b > p > strong:nth-child(5)"
}
