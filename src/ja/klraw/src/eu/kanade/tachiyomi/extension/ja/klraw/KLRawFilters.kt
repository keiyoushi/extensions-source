package eu.kanade.tachiyomi.extension.ja.klraw

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader.UriPartFilter

class TypeFilter :
    UriPartFilter(
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

class StatusFilter :
    UriPartFilter(
        "地位",
        "status",
        arrayOf(
            Pair("全て", "all"),
            Pair("Publishing", "Publishing"),
            Pair("Finished", "Finished"),
        ),
    )

class LanguageFilter :
    UriPartFilter(
        "言語",
        "language",
        arrayOf(
            Pair("全て", "all"),
            Pair("Japanese", "ja"),
            Pair("English", "en"),
        ),
    )
