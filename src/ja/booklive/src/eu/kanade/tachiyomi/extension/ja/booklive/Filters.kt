package eu.kanade.tachiyomi.extension.ja.booklive

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Builder

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(name: String, vals: List<Pair<String, String>>) : Filter.Group<CheckBoxFilter>(name, vals.map { CheckBoxFilter(it.first, it.second) }) {
    val values: List<String>
        get() = state.filter { it.state }.map { it.value }
}

fun Builder.addFilter(param: String, value: String?) = value?.takeIf { it.isNotEmpty() }?.let { addQueryParameter(param, it) }

class SortFilter :
    SelectFilter(
        "並び替え",
        arrayOf(
            "一致順" to "t3",
            "人気順" to "t1",
            "新着順" to "t2",
            "価格安い順" to "t4",
            "価格高い順" to "t5",
            "評価高い順" to "t6",
        ),
    )

class CategoryFilter :
    SelectFilter(
        "カテゴリ",
        arrayOf(
            "すべて" to "",
            "少年・青年マンガ" to "C",
            "少女・女性マンガ" to "CF",
            "ラノベ" to "L",
            "小説・文芸" to "B",
            "ビジネス・実用" to "J",
            "雑誌" to "M",
            "写真集" to "P",
            "アダルト" to "AD",
            "TL" to "TL",
            "BL" to "BL",
        ),
    )

class GenreFilter :
    SelectFilter(
        "ジャンル",
        arrayOf(
            "すべて" to "",
            "少年マンガ" to "6",
            "青年マンガ" to "5",
            "少年マンガ誌" to "3058",
            "青年マンガ誌" to "3059",
            "少女マンガ" to "1",
            "女性マンガ" to "2",
            "少女マンガ誌" to "3060",
            "女性マンガ誌" to "3061",
            "BLマンガ" to "3",
            "BL小説" to "1008",
            "BL誌" to "3094",
            "TLマンガ" to "7",
            "TL小説" to "1006",
            "TL誌" to "3093",
            "男性向けライトノベル" to "14",
            "女性向けライトノベル" to "3062",
            "小説" to "10",
            "歴史・時代" to "12",
            "SF・ファンタジー" to "13",
            "ノンフィクション" to "17",
            "エッセイ・紀行" to "22",
            "ハーレクイン・ロマンス小説" to "15",
            "児童書" to "1040",
            "文芸誌" to "1057",
            "社会・政治" to "1023",
            "ビジネス・経済" to "16",
            "IT・コンピュータ" to "1029",
            "趣味・実用" to "18",
            "スポーツ・アウトドア" to "1031",
            "暮らし・健康・美容" to "1036",
            "旅行ガイド・旅行会話" to "1039",
            "雑学・エンタメ" to "19",
            "学術・語学" to "20",
            "ニュース・ビジネス・総合" to "26",
            "趣味・スポーツ・トレンド" to "29",
            "男性誌・女性誌" to "27",
            "女性タレント" to "23",
            "男性タレント" to "34",
            "動物" to "33",
            "風景、その他" to "32",
            "アダルトマンガ" to "4",
            "官能小説" to "1007",
            "アダルト誌" to "3092",
        ),
    )

class ReviewFilter :
    SelectFilter(
        "評価",
        arrayOf(
            "すべての評価" to "",
            "5.0" to "5",
            "4.5以上" to "4.5",
            "4.0以上" to "4",
            "3.5以上" to "3.5",
            "3.0以上" to "3",
            "2.5以上" to "2.5",
            "2.0以上" to "2",
            "1.5以上" to "1.5",
            "1.0以上" to "1",
            "0.5以上" to "0.5",
        ),
    )

class DiscountFilter :
    CheckBoxGroup(
        "おトク",
        listOf(
            "無料あり" to "free",
            "値引き価格（￥0～）" to "dc",
            "Ptバック対象" to "ptback",
        ),
    )

class ReleaseFilter :
    CheckBoxGroup(
        "発売状況",
        listOf(
            "新刊（１ヶ月以内）" to "new",
            "発売予定" to "skd",
        ),
    )

class PublishFilter :
    CheckBoxGroup(
        "刊行状況",
        listOf(
            "完結" to "comp",
        ),
    )

class VolumeFilter :
    CheckBoxGroup(
        "全巻数",
        listOf(
            "5巻以下" to "le5",
            "6~10巻" to "le10",
            "11巻以上" to "ge11",
        ),
    )

class ExcludeFilter :
    CheckBoxGroup(
        "除外設定",
        listOf(
            "期間限定無料作品を除外" to "exlimf",
            "試し読み増量作品を除外" to "extrinc",
            "単話・分冊を除外" to "exfasc",
            "合本を除外" to "excomb",
            "タテヨミを除外" to "extateyomi",
        ),
    )
