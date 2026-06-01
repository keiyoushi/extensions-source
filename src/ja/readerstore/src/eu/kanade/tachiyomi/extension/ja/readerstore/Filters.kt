package eu.kanade.tachiyomi.extension.ja.readerstore

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Builder

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class GroupedCheckBox(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(displayName: String, options: Array<Pair<String, String>>) : Filter.Group<GroupedCheckBox>(displayName, options.map { GroupedCheckBox(it.first, it.second) }) {
    val checked get() = state.filter { it.state }.map { it.value }
}

fun Builder.addFilter(param: String, filter: SelectFilter?) = filter?.value?.takeIf(String::isNotBlank)?.let { addQueryParameter(param, it) }

fun Builder.addFilter(param: String, filter: Filter.Text?) = filter?.state?.takeIf(String::isNotBlank)?.let { addQueryParameter(param, it.trim()) }

fun Builder.addFilter(param: String, filter: CheckBoxGroup?) = filter?.checked?.forEach { addQueryParameter(param, it) }

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            "一致度順" to "match",
            "人気順" to "popularRank",
            "新着順" to "newArrival",
            "高評価順" to "reviewScore",
            "価格の安い順" to "lowPrice",
        ),
    )

class ReleaseFilter :
    SelectFilter(
        "発売日",
        arrayOf(
            "指定なし" to "",
            "過去3日以内" to "3d",
            "過去7日以内" to "7d",
            "過去30日以内" to "30d",
            "過去60日以内" to "60d",
        ),
    )

class GenreFilter :
    CheckBoxGroup(
        "ジャンル",
        arrayOf(
            "コミック" to "A00002",
            "雑誌" to "A00001",
            "グラビア写真集" to "A00021",
            "人文・思想・歴史" to "A00017",
            "社会・政治・法律" to "A00018",
            "ビジネス・経済" to "A00013",
            "サイエンス・テクノロジー" to "A00019",
            "コンピュータ・情報" to "A00014",
            "くらし・家庭" to "A90098",
            "料理・酒" to "A90096",
            "ファッション・美容・ダイエット" to "A90097",
            "ホビー＆カルチャー" to "A90092",
            "スポーツ・アウトドア" to "A90088",
            "地図・ガイド" to "A00012",
            "エンターテイメント" to "A90085",
            "芸術・アート" to "A90002",
            "映画・音楽・演劇" to "A90086",
            "写真集" to "A90093",
            "教養" to "A90095",
            "医学・福祉" to "A00020",
            "教育・語学・参考書" to "A00016",
            "児童書" to "A00015",
            "ボーイズラブ" to "A00009",
            "ティーンズラブ" to "A00008",
            "アダルト" to "A00003",
        ),
    )

class SaleFilter :
    CheckBoxGroup(
        "おトクな対象作品",
        arrayOf(
            "セール" to "discount",
            "ポイント還元" to "rewardPoints",
            "期間限定無料" to "limitedFree",
            "値引きセット" to "discountSet",
        ),
    )

class SaleStatusFilter :
    CheckBoxGroup(
        "販売状況",
        arrayOf(
            "予約受付中" to "reservation",
            "完結" to "completed",
        ),
    )

class ExcludeFilter :
    CheckBoxGroup(
        "除外設定",
        arrayOf(
            "合本版を除く" to "bound",
            "分冊版・単話を除く" to "separate",
            "期間限定無料版を除く" to "limitedFree",
        ),
    )

class PriceMinFilter : Filter.Text("最小価格 (円)")

class PriceMaxFilter : Filter.Text("最大価格 (円)")
