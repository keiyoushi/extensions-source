package eu.kanade.tachiyomi.extension.ja.comicdays

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import keiyoushi.annotation.Source

@Source
abstract class ComicDays : GigaViewer() {
    override val seriesListIds = listOf(
        "13932016480029138639",
        "13932016480029138640",
        "13932016480029138641",
        "13932016480029138642",
        "13932016480029138643",
        "13932016480029138644",
        "13932016480029138645",
        "13932016480029255192",
    )

    override fun getFilterOptions() = listOf(
        "連載作品一覧" to seriesListIds,
        "メディア化" to listOf("Genre:3269754496379892693"),
        "恋愛" to listOf("Genre:3269754496379892700"),
        "ラブコメ" to listOf("Genre:3269754496379892701"),
        "コメディ・ギャグ" to listOf("Genre:3269754496379892706"),
        "サスペンス" to listOf("Genre:3269754496379892707"),
        "アクション" to listOf("Genre:3269754496379892708"),
        "歴史・時代" to listOf("Genre:3269754496379892709"),
        "日常" to listOf("Genre:3269754496379892710"),
        "ヒューマンドラマ" to listOf("Genre:3269754496379892711"),
        "異世界・転生" to listOf("Genre:3269754496379892712"),
        "家族" to listOf("Genre:3269754496379892713"),
        "結婚・育児" to listOf("Genre:3269754496379892714"),
        "浮気・不倫" to listOf("Genre:3269754496379892715"),
        "ヤンキー・裏社会" to listOf("Genre:3269754496379892716"),
        "サバイバル" to listOf("Genre:3269754496379892717"),
        "お色気" to listOf("Genre:3269754496379892720"),
        "青春・スポーツ" to listOf("Genre:3269754496379892723"),
        "SF" to listOf("Genre:3269754496379892724"),
        "ショート・4コマ" to listOf("Genre:3269754496379892725"),
        "仕事" to listOf("Genre:3269754496379892726"),
        "医療" to listOf("Genre:3269754496379892727"),
        "教養・社会問題" to listOf("Genre:3269754496379892728"),
        "芸術" to listOf("Genre:3269754496379892737"),
        "グルメ" to listOf("Genre:3269754496379892740"),
        "軍事・警察" to listOf("Genre:3269754496379892741"),
        "実話・エッセイ" to listOf("Genre:3269754496379892747"),
        "動物・ねこ" to listOf("Genre:3269754496379892748"),
        "BL" to listOf("Genre:3269754496379892749"),
        "百合" to listOf("Genre:3269754496379892750"),
        "続編・スピンオフ" to listOf("Genre:3269754496379892753"),
    )
}
