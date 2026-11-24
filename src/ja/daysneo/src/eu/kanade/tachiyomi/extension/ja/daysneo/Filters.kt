package eu.kanade.tachiyomi.extension.ja.daysneo

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    Filter.Header("ランキングは検索語と他の条件が空のときに使われます"),
    RankingFilter(),
    Filter.Separator(),
    StatusFilter(),
    GenreFilter(),
    ThemeFilter(),
)

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class RankingFilter :
    SelectFilter(
        "ランキング",
        listOf(
            "累計" to "all",
            "年間" to "yearly",
            "月間" to "monthly",
            "週間" to "weekly",
            "新着" to "new",
            "オススメ" to "fuatured",
        ),
    )

class StatusFilter :
    SelectFilter(
        "状態",
        listOf(
            "すべて" to "",
            "連載中" to "0",
            "完結済み" to "1",
        ),
    )

class GenreFilter :
    SelectFilter(
        "カテゴリー",
        listOf(
            "すべて" to "",
            "ファンタジー" to "1",
            "恋愛・ラブコメ" to "2",
            "アクション" to "3",
            "スポーツ" to "4",
            "ヤンキー／喧嘩" to "5",
            "ホラー" to "6",
            "ミステリー／サスペンス" to "7",
            "歴史" to "8",
            "SF" to "9",
            "ギャグ" to "10",
            "エッセイコミック" to "11",
            "ノンフィクション" to "12",
            "職業もの" to "13",
            "BL" to "14",
            "百合" to "16",
            "日常系" to "17",
            "その他" to "19",
        ),
    )

class Theme(name: String, val id: String) : Filter.CheckBox(name)

class ThemeFilter :
    Filter.Group<Theme>(
        "タグ（すべてを含む）",
        listOf(
            Theme("HIKE UP", "46"),
            Theme("青年誌志望", "1"),
            Theme("女性誌志望", "2"),
            Theme("少年誌志望", "13"),
            Theme("少女漫画志望", "15"),
            Theme("漫画歴浅め", "3"),
            Theme("デビュー済み", "4"),
            Theme("平成生まれ", "5"),
            Theme("未成年", "14"),
            Theme("プロ志望!!", "6"),
            Theme("アドバイス希望", "7"),
            Theme("アシスタント希望", "8"),
            Theme("原作者志望", "9"),
            Theme("作画志望", "10"),
            Theme("NEO初投稿", "12"),
            Theme("GL・百合", "17"),
            Theme("BL", "18"),
            Theme("タテ読み", "19"),
            Theme("４コマ", "21"),
        ),
    )
