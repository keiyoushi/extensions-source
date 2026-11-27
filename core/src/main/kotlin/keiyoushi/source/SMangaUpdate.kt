package keiyoushi.source

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/**
 * Updated details and chapters of a manga, returned by [KeiSource.fetchMangaUpdate].
 *
 * Mirrors the callback-based `SMangaUpdate` of extensions-lib 1.7. [KeiSource] runs [manga] and [chapters]
 * concurrently.
 *
 * @property manga Returns the manga's details.
 * @property chapters Returns the manga's chapters.
 */
class SMangaUpdate(
    val manga: suspend () -> SManga,
    val chapters: suspend () -> List<SChapter>,
) {
    constructor(manga: SManga, chapters: List<SChapter>) : this({ manga }, { chapters })
    constructor(manga: SManga, chapters: suspend () -> List<SChapter>) : this({ manga }, chapters)
}
