package eu.kanade.tachiyomi.extension.tr.mangakulesi

import eu.kanade.tachiyomi.multisrc.uzaymanga.UzayManga
import keiyoushi.annotation.Source

@Source
abstract class MangaKulesi : UzayManga() {

    override val cdnUrl: String
        get() = "$baseUrl/upload"
}
