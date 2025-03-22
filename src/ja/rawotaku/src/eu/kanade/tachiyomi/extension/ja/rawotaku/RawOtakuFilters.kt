package eu.kanade.tachiyomi.extension.ja.rawotaku

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader.UriMultiSelectFilter
import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader.UriPartFilter

class TypeFilter : UriPartFilter(
    "タイプ",
    "type",
    arrayOf(
        Pair("全て", "all"),
        Pair("Raw Manga", "Raw Manga"),
        Pair("BLコミック", "BLコミック"),
        Pair("TLコミック", "TLコミック"),
        Pair("オトナコミック", "オトナコミック"),
        Pair("女性マンガ", "女性マンガ"),
        Pair("少女マンガ", "少女マンガ"),
        Pair("少年マンガ", "少年マンガ"),
        Pair("青年マンガ", "青年マンガ"),
    ),
)

class StatusFilter : UriPartFilter(
    "地位",
    "status",
    arrayOf(
        Pair("全て", "all"),
        Pair("Publishing", "Publishing"),
        Pair("Finished", "Finished"),
    ),
)

class LanguageFilter : UriPartFilter(
    "言語",
    "language",
    arrayOf(
        Pair("全て", "all"),
        Pair("Japanese", "ja"),
        Pair("English", "en"),
    ),
)

class GenreFilter : UriMultiSelectFilter(
    "ジャンル",
    "genre[]",
    arrayOf(
        Pair("アクション", "55"),
        Pair("エッチ", "15706"),
        Pair("コメディ", "91"),
        Pair("ドラマ", "56"),
        Pair("ハーレム", "20"),
        Pair("ファンタジー", "1"),
        Pair("冒険", "54"),
        Pair("悪魔", "6820"),
        Pair("武道", "1064"),
        Pair("歴史的", "9600"),
        Pair("警察・特殊部隊", "6089"),
        Pair("車･バイク", "4329"),
        Pair("音楽", "473"),
        Pair("魔法", "1416"),
    ),
)
