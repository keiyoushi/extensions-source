package eu.kanade.tachiyomi.extension.ja.mangamura

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader

class MangaMura : MangaReader(
    "Manga Mura",
    "https://mangamura.net",
    "ja",
) {
    override val chapterIdSelect = "ja-chaps"

    override fun getAjaxUrl(id: String): String {
        return "$baseUrl/json/chapter?mode=vertical&id=$id"
    }
}
