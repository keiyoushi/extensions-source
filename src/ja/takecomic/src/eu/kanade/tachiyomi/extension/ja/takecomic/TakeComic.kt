package eu.kanade.tachiyomi.extension.ja.takecomic

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewerAlt

class TakeComic :
    ComiciViewerAlt(
        "TakeComic",
        "https://takecomic.jp",
        "ja",
        "https://takecomic.jp/api",
    ) {
    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("更新順", "/series/list/up"),
        Pair("新作順", "/series/list/new"),
        Pair("完結", "/category/manga/complete"),
        Pair("月曜日", "/category/manga/day/1"),
        Pair("火曜日", "/category/manga/day/2"),
        Pair("水曜日", "/category/manga/day/3"),
        Pair("木曜日", "/category/manga/day/4"),
        Pair("金曜日", "/category/manga/day/5"),
        Pair("土曜日", "/category/manga/day/6"),
        Pair("日曜日", "/category/manga/day/7"),
        Pair("その他", "/category/manga/day/8"),
    )
}
