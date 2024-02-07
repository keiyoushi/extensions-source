package eu.kanade.tachiyomi.extension.ja.hachiraw

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(state: Int = 2) : SelectFilter(
    "Sort By",
    listOf(
        Selection("最新", "lastest"),
        Selection("A-Z", "name"),
        Selection("最も多くのビュー", "views"),
    ),
    state,
)

// https://hachiraw.net/list-manga
// copy([...document.querySelectorAll("#TypeShow a")].map((e) => `Genre("${e.textContent.trim()}", "${e.href.split("/").slice(-1)}"),`).join("\n"))
class GenreFilter(state: Int = 0) : SelectFilter(
    "Genres",
    listOf(
        // TODO: Is this the correct translation for "All"/"Everything"?
        Selection("すべて", ""),
        Selection("アクション", "action"),
        Selection("メカ", "mecha"),
        Selection("オルタナティブワールド", "alternative-world"),
        Selection("神秘", "mystery"),
        Selection("アダルト", "adult"),
        Selection("ワンショット", "one-shot"),
        Selection("アニメ", "anime"),
        Selection("心理的", "psychological"),
        Selection("コメディ", "comedy"),
        Selection("ロマンス", "romance"),
        Selection("漫画", "comic"),
        Selection("学校生活", "school-life"),
        Selection("同人誌", "doujinshi"),
        Selection("SF", "sci-fi"),
        Selection("ドラマ", "drama"),
        Selection("青年", "seinen"),
        Selection("エッチ", "Ecchi"),
        Selection("少女", "Shoujo"),
        Selection("ファンタジー", "Fantasy"),
        Selection("少女愛", "shojou-ai"),
        Selection("ジェンダーベンダー", "Gender-Bender"),
        Selection("少年", "Shounen"),
        Selection("ハーレム", "Harem"),
        Selection("少年愛", "shounen-ai"),
        Selection("歴史的", "historical"),
        Selection("人生のひとこま", "slice-of-life"),
        Selection("ホラー", "Horror"),
        Selection("汚い", "Smut"),
        Selection("じょうせい", "Josei"),
        Selection("スポーツ", "Sports"),
        Selection("ライブアクション", "live-action"),
        Selection("超自然的な", "supernatural"),
        Selection("マンファ", "Manhua"),
        Selection("悲劇", "Tragedy"),
        Selection("医学", "Medical"),
        Selection("冒険", "Adventure"),
        Selection("武道", "Martial-art"),
        Selection("やおい", "Yaoi"),
        Selection("成熟した", "Mature"),
        Selection("異世界", "Isekai"),
        Selection("魔法", "Magic"),
        Selection("ロリコン", "Lolicon"),
        Selection("ワンショット", "Oneshot"),
        Selection("該当なし", "N-A"),
        Selection("食べ物", "Food"),
        Selection("ゲーム", "Game"),
        Selection("戦争", "War"),
        Selection("エルフ", "Elves"),
        Selection("武道", "martial-arts"),
        Selection("少女愛", "shoujo-ai"),
        Selection("更新中", "updating"),
        Selection("百合", "yuri"),
        Selection("ショタコン", "shotacon"),
        Selection("SMARTOON", "SMARTOON"),
    ),
    state,
)

open class SelectFilter(name: String, val items: List<Selection>, state: Int = 0) : Filter.Select<String>(
    name,
    items.map { it.name }.toTypedArray(),
    state,
)

data class Selection(
    val name: String,
    val id: String,
)
