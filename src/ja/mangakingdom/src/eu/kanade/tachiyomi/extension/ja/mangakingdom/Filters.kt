package eu.kanade.tachiyomi.extension.ja.mangakingdom

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class CheckBoxItem(name: String, val id: String) : Filter.CheckBox(name)

class SortFilter :
    SelectFilter(
        "並べ替え",
        arrayOf(
            Pair("人気順", "popular"),
            Pair("新着順", "new"),
            Pair("まんがレポの多い順", "report_num"),
            Pair("連載時期の新しい順", "publish"),
            Pair("50音順", "kana"),
        ),
    )

class FinishedFlagFilter :
    SelectFilter(
        "完結形式",
        arrayOf(
            Pair("指定しない", "0"),
            Pair("完結のみ", "1"),
            Pair("未完結のみ", "2"),
        ),
    )

class FreeCampaignFilter :
    SelectFilter(
        "無料キャンペーン",
        arrayOf(
            Pair("指定しない", ""),
            Pair("じっくり試し読み", "1"),
            Pair("期間限定", "2"),
            Pair("1冊以上無料", "3"),
            Pair("2冊以上無料", "4"),
            Pair("3冊以上無料", "5"),
        ),
    )

class MangaRepoNumFilter :
    SelectFilter(
        "まんがレポ",
        arrayOf(
            Pair("指定しない", ""),
            Pair("10件以上", "10"),
            Pair("50件以上", "50"),
            Pair("100件以上", "100"),
        ),
    )

class DistributionFilter :
    SelectFilter(
        "配信形式",
        arrayOf(
            Pair("指定しない", "0"),
            Pair("巻配信有り", "1"),
            Pair("コマ配信有り", "2"),
        ),
    )

class DiscountFilter : Filter.CheckBox("割引あり")
class WithoutSexyTitleFilter : Filter.CheckBox("オトナ・TL・BL作品を除く")

class PointFvMaxFilter : Filter.Text("コマ最大価格 (pt)")
class PointPvMaxFilter : Filter.Text("巻最大価格 (pt)")
class VolumeFvMinFilter : Filter.Text("コマ最小話数")
class VolumeFvMaxFilter : Filter.Text("コマ最大話数")
class VolumePvMinFilter : Filter.Text("巻最小数")
class VolumePvMaxFilter : Filter.Text("巻最大数")

class CategoryFilter :
    Filter.Group<CheckBoxItem>(
        "カテゴリ",
        listOf(
            CheckBoxItem("女性", "34"),
            CheckBoxItem("少女", "3"),
            CheckBoxItem("青年", "1"),
            CheckBoxItem("少年", "2"),
            CheckBoxItem("オトナ", "104"),
            CheckBoxItem("ＴＬ", "31"),
            CheckBoxItem("ＢＬ", "20"),
        ),
    )

class GenreFilter :
    Filter.Group<CheckBoxItem>(
        "ジャンル",
        listOf(
            CheckBoxItem("恋愛", "7"),
            CheckBoxItem("ヒューマンドラマ", "35"),
            CheckBoxItem("サスペンス・ミステリー", "15"),
            CheckBoxItem("ホラー", "9"),
            CheckBoxItem("ギャグ・コメディ", "14"),
            CheckBoxItem("職業・ビジネス", "4"),
            CheckBoxItem("医療・病院系", "49"),
            CheckBoxItem("ネオン", "59"),
            CheckBoxItem("グルメ", "11"),
            CheckBoxItem("歴史・時代劇", "8"),
            CheckBoxItem("アクション・アドベンチャー", "13"),
            CheckBoxItem("SF・ファンタジー", "17"),
            CheckBoxItem("ヤンキー・任侠", "5"),
            CheckBoxItem("ギャンブル", "16"),
            CheckBoxItem("スポーツ", "12"),
        ),
    )

class KeywordFilter :
    Filter.Group<CheckBoxItem>(
        "キーワード",
        listOf(
            CheckBoxItem("出産・育児", "42"),
            CheckBoxItem("くらし・生活", "26"),
            CheckBoxItem("感動", "23"),
            CheckBoxItem("純愛", "560"),
            CheckBoxItem("ラブコメ", "47"),
            CheckBoxItem("日常", "579"),
            CheckBoxItem("ツンデレ", "573"),
            CheckBoxItem("学園", "110"),
            CheckBoxItem("三角関係", "557"),
            CheckBoxItem("オフィス", "111"),
            CheckBoxItem("愛憎劇", "63"),
            CheckBoxItem("復讐", "334"),
            CheckBoxItem("ミステリー", "60"),
            CheckBoxItem("教育", "550"),
            CheckBoxItem("4コマ", "170"),
            CheckBoxItem("エッセイ", "171"),
            CheckBoxItem("映画化", "597"),
            CheckBoxItem("ドラマ化", "578"),
            CheckBoxItem("アニメ化", "57"),
        ),
    )

class MagazineFilter :
    Filter.Group<CheckBoxItem>(
        "掲載誌",
        listOf(
            CheckBoxItem("週刊少年サンデー", "1"),
            CheckBoxItem("週刊少年ジャンプ", "43"),
            CheckBoxItem("週刊少年マガジン", "82"),
            CheckBoxItem("ビッグコミックオリジナル", "27"),
            CheckBoxItem("ウルトラジャンプ", "62"),
            CheckBoxItem("モーニング", "106"),
            CheckBoxItem("ベツコミ", "16"),
            CheckBoxItem("マーガレット", "51"),
            CheckBoxItem("デザート", "94"),
            CheckBoxItem("プチコミック", "37"),
            CheckBoxItem("FEEL YOUNG", "469"),
            CheckBoxItem("Kiss", "119"),
        ),
    )
