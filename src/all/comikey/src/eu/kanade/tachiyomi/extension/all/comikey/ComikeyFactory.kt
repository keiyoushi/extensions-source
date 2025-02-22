package eu.kanade.tachiyomi.extension.all.comikey
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.SourceFactory

class ComikeyFactory : SourceFactory {
    override fun createSources() = listOf(
        Comikey("en"),
        Comikey("es"),
        Comikey("id"),
        Comikey("pt-BR"),
        Comikey("pt-BR", "Comikey Brasil", "https://br.comikey.com", defaultLanguage = "pt-BR"),
    )
}
