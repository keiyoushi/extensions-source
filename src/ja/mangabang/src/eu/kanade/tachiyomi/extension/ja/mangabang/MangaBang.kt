package eu.kanade.tachiyomi.extension.ja.mangabang

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewerAlt

class MangaBang :
    ComiciViewerAlt(
        "MangaBang Comics",
        "https://comics.manga-bang.com",
        "ja",
        "https://comics.manga-bang.com/api",
    ) {
    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("更新順", "/series/list/up"),
        Pair("新作順", "/series/list/new"),
        Pair("完結", "/category/manga/complete"),
    )
}
