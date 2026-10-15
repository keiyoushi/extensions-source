package eu.kanade.tachiyomi.extension.all.magicaltranslators

import eu.kanade.tachiyomi.multisrc.guya.Guya
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source

@Source
abstract class MagicalTranslators : Guya() {
    override fun filterMangas(mangasPage: MangasPage): MangasPage = when (lang) {
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
}
