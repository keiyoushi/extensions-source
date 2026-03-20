package eu.kanade.tachiyomi.extension.ja.alphapolis

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter :
    Filter.Group<FilterTag>(
        "カテゴリ",
        listOf(
            FilterTag("男性向け", "men"),
            FilterTag("女性向け", "women"),
            FilterTag("TL", "tl"),
            FilterTag("BL", "bl"),
        ),
    )

class LabelFilter :
    Filter.Group<FilterTag>(
        "レーベル",
        listOf(
            FilterTag("アルファポリス", "alphapolis"),
            FilterTag("レジーナ", "regina"),
            FilterTag("アルファノルン", "alphanorn"),
            FilterTag("エタニティ", "eternity"),
            FilterTag("ノーチェ", "noche"),
            FilterTag("アンダルシュ", "andarche"),
        ),
    )

class StatusFilter :
    Filter.Group<FilterTag>(
        "進行状況",
        listOf(
            FilterTag("連載中", "running"),
            FilterTag("完結", "finished"),
            FilterTag("休載中", "sleeping"),
        ),
    )

class RentalFilter :
    Filter.Group<FilterTag>(
        "レンタル",
        listOf(
            FilterTag("レンタルあり", "enable"),
            FilterTag("全話無料", "disable"),
        ),
    )

class DailyFreeFilter : Filter.CheckBox("毎日¥0")

class FilterTag(name: String, val value: String) : Filter.CheckBox(name)
