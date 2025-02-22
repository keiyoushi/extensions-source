package eu.kanade.tachiyomi.extension.all.eternalmangas
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.SourceFactory

class EternalMangasFactory : SourceFactory {
    override fun createSources() = listOf(
        EternalMangasES(),
        EternalMangasEN(),
        EternalMangasPTBR(),
    )
}

class EternalMangasES : EternalMangas("es", "es")
class EternalMangasEN : EternalMangas("en", "en")
class EternalMangasPTBR : EternalMangas("pt-BR", "pt")
