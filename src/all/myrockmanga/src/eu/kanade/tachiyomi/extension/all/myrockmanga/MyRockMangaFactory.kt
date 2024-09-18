package eu.kanade.tachiyomi.extension.all.myrockmanga

import eu.kanade.tachiyomi.multisrc.otakusanctuary.OtakuSanctuary
import eu.kanade.tachiyomi.source.SourceFactory

class MyRockMangaFactory : SourceFactory {
    override fun createSources() = listOf(
        OtakuSanctuary("MyRockManga", "https://myrockmanga.com", "all"),
        OtakuSanctuary("MyRockManga", "https://myrockmanga.com", "vi"),
        OtakuSanctuary("MyRockManga", "https://myrockmanga.com", "en"),
        OtakuSanctuary("MyRockManga", "https://myrockmanga.com", "it"),
        OtakuSanctuary("MyRockManga", "https://myrockmanga.com", "es"),
    )
}
