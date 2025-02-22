package eu.kanade.tachiyomi.extension.all.thelibraryofohara

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class TheLibraryOfOharaFactory : SourceFactory {
    override fun createSources(): List<Source> = languageList.map { TheLibraryOfOhara(it.tachiLang, it.siteLang) }
}

private data class Source(val tachiLang: String, val siteLang: String)

private val languageList = listOf(

    Source("id", "Indonesia"),
    Source("en", "English"),
    Source("es", "Spanish"),
    Source("it", "Italian"),
    Source("ar", "Arabic"),
    Source("fr", "French"),

)
