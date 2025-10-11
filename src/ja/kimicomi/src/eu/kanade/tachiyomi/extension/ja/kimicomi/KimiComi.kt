package eu.kanade.tachiyomi.extension.ja.kimicomi

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer

class KimiComi : ComiciViewer(
    "KimiComi",
    "https://kimicomi.com",
    "ja",
) {
    override fun getFilterOptions(): List<Pair<String, String>> = listOf(
        Pair("ランキング", "/ranking/manga"),
        Pair("読み切り", "/category/manga?type=読み切り"),
        Pair("完結", "/category/manga?type=完結"),
        Pair("月曜日", "/category/manga?type=連載中&day=月"),
        Pair("火曜日", "/category/manga?type=連載中&day=火"),
        Pair("水曜日", "/category/manga?type=連載中&day=水"),
        Pair("木曜日", "/category/manga?type=連載中&day=木"),
        Pair("金曜日", "/category/manga?type=連載中&day=金"),
        Pair("土曜日", "/category/manga?type=連載中&day=土"),
        Pair("日曜日", "/category/manga?type=連載中&day=日"),
    )
}
