package eu.kanade.tachiyomi.extension.ja.rawdevartart

import eu.kanade.tachiyomi.multisrc.rawspa.RawSpa
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source

@Source
abstract class Rawdevartart : RawSpa() {
    override val mangaPath = "g"

    override val readerPath = "read"

    override val idPrefix = "ne"

    // Stored chapter urls are API paths: spa/manga/<id>/<number>
    override fun chapterUrl(mangaId: Int, number: String) = "spa/manga/$mangaId/$number"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/ne" + chapter.url.removePrefix("spa/manga/").replace("/", "/chapter-")
}
