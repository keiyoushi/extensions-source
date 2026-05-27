package eu.kanade.tachiyomi.extension.ja.amebamanga

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Builder

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

internal fun Builder.addFilter(param: String, filter: SelectFilter) = filter.value.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }

internal fun Builder.addFilter(filter: CategoryFilter) = filter.state.filter { it.state }.forEach { addQueryParameter("category_id", it.id) }

internal fun Builder.addFilter(param: String, filter: Filter.CheckBox, value: String) {
    if (filter.state) addQueryParameter(param, value)
}

internal fun Builder.addFilter(filter: VolumeFilter) {
    filter.value.takeIf { it.isNotBlank() }?.let {
        val (direction, count) = it.split(":")
        val param = if (direction == "to") "max_book_vol_to" else "max_book_vol_from"
        addQueryParameter(param, count)
    }
}

class SortFilter :
    SelectFilter(
        "並び替え",
        arrayOf(
            Pair("人気順", "monthly_spend"),
            Pair("新着順", "new"),
            Pair("関連度順", "score"),
            Pair("あいうえお順", "title_initial"),
            Pair("評価高い順", "title_review"),
            Pair("評価多い順", "title_review_count"),

        ),
    )

class GenreFilter :
    SelectFilter(
        "ジャンル",
        arrayOf(
            Pair("すべて", ""),
            Pair("少女", "3"),
            Pair("女性", "5"),
            Pair("少年", "2"),
            Pair("青年", "4"),
            Pair("TL", "8"),
            Pair("BL", "9"),
            Pair("メンズ", "7"),
            Pair("ライトノベル", "11"),
            Pair("小説", "12"),
            Pair("文芸", "13"),
            Pair("ビジネス", "14"),
            Pair("実用", "15"),
            Pair("雑誌", "16"),
            Pair("写真集", "17"),
        ),
    )

class PublisherFilter :
    SelectFilter(
        "出版社",
        arrayOf(
            Pair("すべて", ""),
            Pair("KADOKAWA", "1223"),
            Pair("小学館", "90"),
            Pair("講談社", "12"),
            Pair("集英社", "111"),
            Pair("白泉社", "118"),
            Pair("双葉社", "14"),
            Pair("ぶんか社", "95"),
            Pair("秋田書店", "34"),
            Pair("新潮社", "288"),
            Pair("スクウェア・エニックス", "110"),
            Pair("リブレ", "160"),
            Pair("モバイル・メディアリサーチ", "109"),
            Pair("スクリーモ", "314"),
            Pair("SBクリエイティブ", "3238"),
            Pair("SBクリエイティブ/GA文庫", "3239"),
        ),
    )

class MagazineFilter :
    SelectFilter(
        "掲載誌",
        arrayOf(
            Pair("すべて", ""),
            Pair("プチコミック", "160"),
            Pair("Cheese!", "164"),
            Pair("デザート", "8"),
            Pair("マーガレット", "284"),
            Pair("別冊フレンド", "14"),
            Pair("Betsucomi", "530"),
            Pair("花とゆめ", "339"),
            Pair("裏サンデー女子部", "1080"),
            Pair("少年ジャンプ+", "608"),
            Pair("週刊少年ジャンプ", "280"),
            Pair("週刊少年マガジン", "7"),
            Pair("少年サンデー", "432"),
            Pair("モーニング", "11"),
            Pair("ビッグスピリッツ", "536"),
        ),
    )

class CheckBox(name: String, val id: String) : Filter.CheckBox(name)

class CategoryFilter :
    Filter.Group<CheckBox>(
        "カテゴリ",
        listOf(
            CheckBox("総合", "general"),
            CheckBox("恋愛", "love"),
            CheckBox("人間ドラマ", "drama"),
            CheckBox("エッセイ", "essay"),
            CheckBox("TL", "tl"),
            CheckBox("ラブストーリー", "mens_love"),
            CheckBox("グルメ", "gourmet"),
            CheckBox("異世界", "isekai"),
            CheckBox("推理・サスペンス", "mystery"),
            CheckBox("ホラー", "horror"),
            CheckBox("日常", "daily_life"),
            CheckBox("ギャグ・コメディ", "comedy"),
            CheckBox("アングラ・裏社会", "underground"),
            CheckBox("バトル", "battle"),
            CheckBox("SF", "sf"),
            CheckBox("カルチャー", "culture"),
            CheckBox("スポーツ", "sport"),
            CheckBox("動物・ペット", "animal"),
            CheckBox("歴史", "history"),
            CheckBox("医療", "medical"),
            CheckBox("冒険", "adventure"),
            CheckBox("社会派", "social_issues"),
            CheckBox("レディコミ", "ladies"),
            CheckBox("BL", "bl"),
            CheckBox("グリム童話", "grimm"),
            CheckBox("メンズ", "adult"),
            CheckBox("ロマンス", "romance"),
            CheckBox("ミリタリー", "military"),
        ),
    )

class ReviewRatingFilter :
    SelectFilter(
        "レビュー評価",
        arrayOf(
            Pair("すべて", ""),
            Pair("4.5以上", "4.5"),
            Pair("4.0以上", "4"),
            Pair("3.5以上", "3.5"),
            Pair("3.0以上", "3"),
        ),
    )

class VolumeFilter :
    SelectFilter(
        "巻数",
        arrayOf(
            Pair("すべて", ""),
            Pair("1巻のみ", "to:1"),
            Pair("5巻以下", "to:5"),
            Pair("10巻以下", "to:10"),
            Pair("10巻以上", "from:10"),
        ),
    )

class FreeFilter : Filter.CheckBox("無料")
class DiscountFilter : Filter.CheckBox("割引")
class CompletedFilter : Filter.CheckBox("完結")
class AnimatedFilter : Filter.CheckBox("アニメ化")
class LiveActionFilter : Filter.CheckBox("実写化")
class HasSerialFilter : Filter.CheckBox("無料連載で読める")
class ReleasedThisMonthFilter : Filter.CheckBox("1ヶ月以内に販売した作品")
