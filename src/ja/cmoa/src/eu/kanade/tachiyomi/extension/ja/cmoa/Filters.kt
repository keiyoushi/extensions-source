package eu.kanade.tachiyomi.extension.ja.cmoa

import eu.kanade.tachiyomi.source.model.Filter

class TitleFilter : Filter.Text("タイトル名")
class AuthorFilter : Filter.Text("作家名")
class MagazineFilter : Filter.Text("雑誌・レーベル")
class PublisherFilter : Filter.Text("出版社")
class TitleTagFilter : Filter.Text("作品タグ")

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class GenreFilter :
    SelectFilter(
        "ジャンル",
        arrayOf(
            Pair("すべてのジャンル", ""),
            Pair("少女マンガ", "20"),
            Pair("女性マンガ", "2"),
            Pair("少年マンガ", "12"),
            Pair("青年マンガ", "13"),
            Pair("BLマンガ", "24"),
            Pair("TLマンガ", "34"),
            Pair("ハーレクインコミックス", "21"),
            Pair("アダルトマンガ", "11"),
            Pair("マンガ雑誌", "28"),
            Pair("雑誌", "9925"),
            Pair("写真集", "9923"),
        ),
    )

class PriceFilter :
    SelectFilter(
        "価格",
        arrayOf(
            Pair("すべての価格", ""),
            Pair("～100pt", "0-100"),
            Pair("101pt～500pt", "101-500"),
            Pair("501pt～1,000pt", "501-1000"),
            Pair("1,001pt～", "1001-"),
        ),
    )

class ReviewFilter :
    SelectFilter(
        "レビュー評点",
        arrayOf(
            Pair("すべての評価", ""),
            Pair("4.0以上", "review_4"),
            Pair("3.0以上", "review_3"),
            Pair("2.0以上", "review_2"),
            Pair("1.0以上", "review_1"),
            Pair("レビュー無し", "review_nothing"),
        ),
    )

class SortFilter :
    SelectFilter(
        "並び替え",
        arrayOf(
            Pair("オススメ順", "10"),
            Pair("人気順", "14"),
            Pair("新着順", "16"),
            Pair("価格の安い順", "11"),
            Pair("価格の高い順", "12"),
            Pair("レビューの評価順", "13"),
            Pair("レビュー数の多い順", "15"),
        ),
    )

class FreeFilter : Filter.CheckBox("無料版あり")
class SampleFilter : Filter.CheckBox("立読み増量")
class CampaignFilter : Filter.CheckBox("値引き/還元")
class NewestFilter : Filter.CheckBox("新着/新刊")
class CompleteFilter : Filter.CheckBox("完結作品")
