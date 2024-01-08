package eu.kanade.tachiyomi.extension.all.comico

import eu.kanade.tachiyomi.source.SourceFactory

class ComicoFactory : SourceFactory {
    open class PocketComics(langCode: String) :
        Comico("https://www.pocketcomics.com", "POCKET COMICS", langCode)

    class ComicoJP : Comico("https://www.comico.jp", "コミコ", "ja-JP")

    class ComicoKR : Comico("https://www.comico.kr", "코미코", "ko-KR")

    override fun createSources() = listOf(
        PocketComics("en-US"),
        PocketComics("zh-TW"),
        ComicoJP(),
        ComicoKR(),
    )
}
