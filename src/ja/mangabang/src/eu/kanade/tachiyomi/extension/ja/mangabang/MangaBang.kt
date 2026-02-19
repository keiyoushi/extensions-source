package eu.kanade.tachiyomi.extension.ja.mangabang

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer

class MangaBang :
    ComiciViewer(
        "MangaBang Comics",
        "https://comics.manga-bang.com",
        "ja",
    ) {
    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("完結", "/category/manga?type=完結"),
        Pair("連載", "/category/manga?type=連載中"),
    )
}
