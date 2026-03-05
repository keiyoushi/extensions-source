package eu.kanade.tachiyomi.extension.all.kodokustudio

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.SourceFactory

class KodokuStudio(
    private val language: String,
) : Madara(
    "Kodoku Studio",
    "https://kodokustudio.com",
    language,
) {
    override val mangaSubString: String = "manhua"

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true
}

class KodokuStudioFactory : SourceFactory {
    override fun createSources() = listOf(
        KodokuStudio("ar"),
        KodokuStudio("en"),
        KodokuStudio("pt"),
        KodokuStudio("ru"),
        KodokuStudio("vi"),
        KodokuStudio("zh"),
    )
}
