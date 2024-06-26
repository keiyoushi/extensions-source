package eu.kanade.tachiyomi.extension.ja.comicfuz

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    TagFilter(),
    Filter.Separator(),
    Filter.Header("Doesn't work with text search"),
)

class TagFilter : Filter.Select<String>("Tags", tags.map { it.name }.toTypedArray()) {
    val selected get() = when (state) {
        0 -> null
        else -> tags[state].id
    }
}

class Tag(
    val id: Int,
    val name: String,
)

private val tags = listOf(
    Tag(-1, ""),
    Tag(7, "日曜日"),
    Tag(12, "オリジナル"),
    Tag(38, "グルメ"),
    Tag(138, "FUZコミックス"),
    Tag(288, "広告で人気の作品"),
    Tag(462, "オリジナル作品の最新話が無料化！"),
    Tag(540, "ギャグ・コメディ"),
    Tag(552, "日常"),
    Tag(23, "学園"),
    Tag(26, "SF・ファンタジー"),
    Tag(29, "恋愛"),
    Tag(13, "男性向け"),
    Tag(549, "百合"),
    Tag(41, "お仕事・趣味"),
    Tag(56, "週刊漫画TIMES"),
    Tag(150, "芳文社コミックス"),
    Tag(537, "スポーツ"),
    Tag(68, "まんがタイムきららフォワード"),
    Tag(141, "まんがタイムKRコミックス"),
    Tag(291, "新規連載作品"),
    Tag(204, "まんがタイムオリジナル"),
    Tag(6, "土曜日"),
    Tag(1274, "6/3発売 FUZオリジナル作品新刊"),
    Tag(2, "火曜日"),
    Tag(14, "女性向け"),
    Tag(44, "バトル・アクション"),
    Tag(47, "ミステリー・サスペンス"),
    Tag(83, "BL"),
    Tag(32, "メディア化"),
    Tag(50, "歴史・時代"),
    Tag(20, "４コマ"),
    Tag(147, "まんがタイムコミックス"),
    Tag(5, "金曜日"),
    Tag(543, "異世界"),
    Tag(35, "ヒューマンドラマ"),
    Tag(65, "まんがタイムきららキャラット"),
    Tag(4, "木曜日"),
    Tag(59, "まんがタイムきらら"),
    Tag(153, "ラバココミックス"),
    Tag(201, "まんがタイム"),
    Tag(3, "水曜日"),
    Tag(62, "まんがタイムきららMAX"),
    Tag(17, "読切"),
    Tag(1, "月曜日"),
    Tag(74, "ゆるキャン△"),
    Tag(207, "コミックトレイル"),
    Tag(77, "城下町のダンデライオン"),
    Tag(156, "トレイルコミックス"),
    Tag(198, "まんがホーム"),
    Tag(71, "魔法少女まどか☆マギカ"),
    Tag(177, "花音コミックス"),
    Tag(1175, "価格改定対象作品"),
)
