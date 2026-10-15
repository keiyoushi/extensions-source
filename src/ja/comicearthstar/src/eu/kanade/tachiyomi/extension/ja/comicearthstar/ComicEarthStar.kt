package eu.kanade.tachiyomi.extension.ja.comicearthstar

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class ComicEarthStar : GigaViewer() {
    override val seriesListIds = listOf("14079602755185553136", "14079602755185553138", "14079602755185553142")

    override fun getFilterOptions() = listOf(
        "すべて" to seriesListIds,
        "連載中" to listOf("14079602755185553136"),
        "連載終了" to listOf("14079602755185553138"),
        "読切作品" to listOf("14079602755185553142"),
        "コミカライズ" to listOf("Genre:12207421983825514342"),
        "オリジナル" to listOf("Genre:12207421983825514347"),
        "青年" to listOf("Genre:12207421983825514353"),
        "女性" to listOf("Genre:12207421983825514356"),
        "アニメ化" to listOf("Genre:12207421983825514357"),
        "ファンタジー" to listOf("Genre:12207421983825514361"),
        "アクション・バトル" to listOf("Genre:12207421983825514365"),
        "恋愛" to listOf("Genre:12207421983825514369"),
        "ホラー・ミステリー" to listOf("Genre:12207421983825514370"),
        "日常" to listOf("Genre:12207421983825514371"),
        "歴史・時代" to listOf("Genre:12207421983825514374"),
        "スポーツ" to listOf("Genre:12207421983825514379"),
        "グルメ" to listOf("Genre:12207421983825514385"),
        "開発・開拓" to listOf("Genre:12207421983825514389"),
        "動物" to listOf("Genre:12207421983825514390"),
    )
}
