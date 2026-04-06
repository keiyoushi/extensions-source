package eu.kanade.tachiyomi.extension.all.manhwa18uncensored

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Manhwa18UncensoredFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Manhwa18UncensoredEN(),
        Manhwa18UncensoredALL(),
    )
}

class Manhwa18UncensoredEN : Manhwa18Uncensored("en") {
    override fun popularMangaParse(response: Response) = super.popularMangaParse(response).let { MangasPage(it.mangas.filterNot { m -> m.title.endsWith(" Raw") }, it.hasNextPage) }
    override fun searchMangaParse(response: Response) = super.searchMangaParse(response).let { MangasPage(it.mangas.filterNot { m -> m.title.endsWith(" Raw") }, it.hasNextPage) }
}

// Mix of English, Korean, Spanish, and possibly others. No way to tell which one
class Manhwa18UncensoredALL : Manhwa18Uncensored("all")

abstract class Manhwa18Uncensored(lang: String) :
    Madara(
        "Manhwa 18 Uncensored",
        "https://manhwa18uncensored.com",
        lang,
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es")),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
