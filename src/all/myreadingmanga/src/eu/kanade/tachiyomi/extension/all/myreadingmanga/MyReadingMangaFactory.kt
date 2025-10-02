package eu.kanade.tachiyomi.extension.all.myreadingmanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MyReadingMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = languageList.map { MyReadingManga(it.tachiLang, it.siteLang, it.latestLang) }
}

private data class Source(val tachiLang: String, val siteLang: String, val latestLang: String = siteLang)

// These should all be valid. Add a language code and uncomment to enable
private val languageList = listOf(
    Source("ar", "Arabic"),
//    Source("", "Bahasa"),
    Source("id", "Bahasa Indonesia", "bahasa"),
//    Source("", "Bulgarian"),
    Source("zh_hk", "Cantonese"),
    Source("zh", "Chinese"),
    Source("zh_tw", "Chinese (Traditional)", "traditional-chinese"),
//    Source("", "Croatian"),
//    Source("", "Czech"),
//    Source("", "Danish"),
    Source("en", "English"),
//    Source("", "Filipino"),
//    Source("", "Finnish"),
//    Source("", "Flemish Dutch", "flemish-dutch"),
    Source("fr", "French"),
    Source("de", "German"),
//    Source("", "Greek"),
//    Source("", "Hebrew"),
//    Source("", "Hindi"),
//    Source("", "Hungarian"),
    Source("it", "Italian"),
    Source("ja", "Japanese", "jp"),
    Source("ko", "Korean"),
//    Source("", "Lithuanian"),
//    Source("", "Malay"),
//    Source("", "Norwegian Bokmål", "norwegian-bokmal"),
//    Source("", "Persian"),
//    Source("", "Polish"),
    Source("pt-BR", "Portuguese"),
//    Source("", "Romanian"),
    Source("ru", "Russian"),
//    Source("", "Slovak"),
    Source("es", "Spanish"),
//    Source("", "Swedish"),
//    Source("", "Thai"),
    Source("tr", "Turkish"),
    Source("vi", "Vietnamese"),
)
