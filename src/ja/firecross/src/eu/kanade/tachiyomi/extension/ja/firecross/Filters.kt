package eu.kanade.tachiyomi.extension.ja.firecross

import eu.kanade.tachiyomi.source.model.Filter

class Label(name: String, val value: String) : Filter.CheckBox(name)

class LabelFilter :
    Filter.Group<Label>(
        "Labels",
        listOf(
            Label("HJ文庫 (Novel)", "1"),
            Label("HJノベルス (Novel)", "2"),
            Label("コミックファイア (Manga)", "3"),
            Label("HJコミックス (Manga)", "4"),
        ),
    )
