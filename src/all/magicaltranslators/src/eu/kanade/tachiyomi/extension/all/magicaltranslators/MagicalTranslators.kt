package eu.kanade.tachiyomi.extension.all.magicaltranslators

import eu.kanade.tachiyomi.multisrc.guya.Guya
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import okhttp3.Response

@Source
abstract class MagicalTranslators : Guya() {
    private fun filterMangasPage(mangasPage: MangasPage): MangasPage = when (lang) {
        "en" -> mangasPage.copy(
            mangas = mangasPage.mangas.filterNot { it.url.endsWith("-ES") || it.url.endsWith("-PL") },
        )
        "es" -> mangasPage.copy(
            mangas = mangasPage.mangas.filter { it.url.endsWith("-ES") },
        )
        "pl" -> mangasPage.copy(
            mangas = mangasPage.mangas.filter { it.url.endsWith("-PL") },
        )
        else -> throw IllegalArgumentException("Unknown language: $lang")
    }

    override fun popularMangaParse(response: Response): MangasPage = filterMangasPage(super.popularMangaParse(response))

    override fun latestUpdatesParse(response: Response): MangasPage = filterMangasPage(super.latestUpdatesParse(response))

    override fun searchMangaParseWithSlug(response: Response, slug: String): MangasPage = filterMangasPage(super.searchMangaParseWithSlug(response, slug))

    override fun searchMangaParse(response: Response, query: String): MangasPage = filterMangasPage(super.searchMangaParse(response, query))
}
