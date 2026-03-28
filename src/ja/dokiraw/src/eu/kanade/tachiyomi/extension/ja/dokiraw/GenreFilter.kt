package eu.kanade.tachiyomi.extension.ja.dokiraw

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter :
    Filter.Select<String>(
        "Genre",
        arrayOf(
            "All", // 0
            "フルカラー",
            "Ecchi",
            "エロい",
            "コメディ",
            "ロマンス",
            "アクション",
            "スポーツ",
            "ファンタジー",
            "SF",
            "異世界",
            "心理的",
            "青年",
        ),
    )
