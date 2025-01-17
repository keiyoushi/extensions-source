package eu.kanade.tachiyomi.extension.en.babelwuxia

import eu.kanade.tachiyomi.multisrc.madara.Madara

class BabelWuxia : Madara("Babel Wuxia", "https://babelwuxia.com", "en") {
    // moved from MangaThemesia
    override val versionId = 2
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
