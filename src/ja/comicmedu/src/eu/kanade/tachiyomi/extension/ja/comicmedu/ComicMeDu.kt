package eu.kanade.tachiyomi.extension.ja.comicmedu

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer

class ComicMeDu :
    ComiciViewer(
        "Comic MeDu",
        "https://comic-medu.com",
        "ja",
    ) {
    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("読み切り", "/category/manga?type=読み切り"),
        Pair("完結", "/category/manga?type=完結"),
        Pair("連載", "/category/manga?type=連載中"),
    )
}
