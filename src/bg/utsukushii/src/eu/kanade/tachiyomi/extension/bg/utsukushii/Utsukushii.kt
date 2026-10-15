package eu.kanade.tachiyomi.extension.bg.utsukushii

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import keiyoushi.annotation.Source

@Source
abstract class Utsukushii : MMRCMS() {
    override fun popularMangaUrl(page: Int) = "$baseUrl/manga-list"
}
