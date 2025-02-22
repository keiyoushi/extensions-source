package eu.kanade.tachiyomi.extension.all.simplyhentai
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.SourceFactory

class SimplyHentaiFactory : SourceFactory {
    override fun createSources() = listOf(
        SimplyHentai("en", "english"),
        SimplyHentai("ja", "japanese"),
        SimplyHentai("zh", "chinese"),
        SimplyHentai("ko", "korean"),
        SimplyHentai("es", "spanish"),
        SimplyHentai("ru", "russian"),
        SimplyHentai("fr", "french"),
        SimplyHentai("de", "german"),
        SimplyHentai("it", "italian"),
        SimplyHentai("pl", "polish"),
    )
}
