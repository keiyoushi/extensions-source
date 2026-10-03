package eu.kanade.tachiyomi.extension.ja.comicogyaaa

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class ComicOgyaaa : GigaViewer() {
    override val seriesListIds = listOf(
        "2550912965179775890",
        "2550912965179775891",
    )

    override fun getFilterOptions() = listOf(
        "作品一覧" to seriesListIds,
        "日常系" to listOf("Genre:2550912965179776778"),
        "ファンタジー" to listOf("Genre:2550912965179776774"),
        "ラブコメ" to listOf("Genre:2550912965179776763"),
        "ギャグ" to listOf("Genre:2550912965179776769"),
        "ヒューマンドラマ" to listOf("Genre:2550912965179776783"),
        "アクション" to listOf("Genre:2550912965179776779"),
        "ホラー・サスペンス" to listOf("Genre:2550912965179776784"),
    )
}
