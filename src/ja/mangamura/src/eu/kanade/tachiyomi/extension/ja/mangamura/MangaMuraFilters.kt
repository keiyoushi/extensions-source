package eu.kanade.tachiyomi.extension.ja.mangamura

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader.UriMultiSelectFilter
import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader.UriPartFilter

class TypeFilter : UriPartFilter(
    "タイプ",
    "type",
    arrayOf(
        "全て" to "all",
        "Raw Manga" to "Raw Manga",
        "BLコミック" to "BLコミック",
        "TLコミック" to "TLコミック",
        "オトナコミック" to "オトナコミック",
        "女性マンガ" to "女性マンガ",
        "少女マンガ" to "少女マンガ",
        "少年マンガ" to "少年マンガ",
        "青年マンガ" to "青年マンガ",
    ),
)

class StatusFilter : UriPartFilter(
    "地位",
    "status",
    arrayOf(
        "全て" to "all",
        "Publishing" to "Publishing",
        "Finished" to "Finished",
    ),
)

class LanguageFilter : UriPartFilter(
    "言語",
    "language",
    arrayOf(
        "全て" to "all",
        "Japanese" to "ja",
        "English" to "en",
    ),
)

class SortFilter : UriPartFilter(
    "選別",
    "sort",
    arrayOf(
        "デフォルト" to "default",
        "最新の更新" to "latest-updated",
        "最も見られました" to "most-viewed",
        "Title [A-Z]" to "title-az",
        "Title [Z-A]" to "title-za",
    ),
)

class GenreFilter : UriMultiSelectFilter(
    "ジャンル",
    "genre[]",
    arrayOf(
        "アクション" to "55",
        "エッチ" to "15706",
        "コメディ" to "91",
        "ドラマ" to "56",
        "ハーレム" to "20",
        "ファンタジー" to "1",
        "冒険" to "54",
        "悪魔" to "6820",
        "武道" to "1064",
        "歴史的" to "9600",
        "警察・特殊部隊" to "6089",
        "車･バイク" to "4329",
        "音楽" to "473",
        "魔法" to "1416",
    ),
)
