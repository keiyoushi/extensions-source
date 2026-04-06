package eu.kanade.tachiyomi.extension.all.uncensoredmanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class UncensoredManhwaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        UncensoredManhwaEN(),
        UncensoredManhwaALL(),
    )
}

class UncensoredManhwaEN : UncensoredManhwa("en") {
    override fun popularMangaParse(response: Response) = super.popularMangaParse(response).let { MangasPage(it.mangas.filterNot { m -> m.title.endsWith(" Raw") }, it.hasNextPage) }
    override fun searchMangaParse(response: Response) = super.searchMangaParse(response).let { MangasPage(it.mangas.filterNot { m -> m.title.endsWith(" Raw") }, it.hasNextPage) }
}

// Mix of English, Korean, Spanish, and possibly others. No way to tell which one
class UncensoredManhwaALL : UncensoredManhwa("all")

abstract class UncensoredManhwa(lang: String) :
    Madara(
        "Uncensored Manhwa",
        "https://uncensoredmanhwa.us",
        lang,
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es")),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
