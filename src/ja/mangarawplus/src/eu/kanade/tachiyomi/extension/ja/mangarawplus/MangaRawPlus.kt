package eu.kanade.tachiyomi.extension.ja.mangarawplus

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaRawPlus : Madara("MANGARAW+", "https://mangarawplus.org", "ja") {
    override val mangaSubString = "sp"
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
