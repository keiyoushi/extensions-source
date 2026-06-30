package eu.kanade.tachiyomi.extension.en.mangareadorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaReadOrg : Madara() {
    override val dateFormat = SimpleDateFormat("dd.MM.yyy", Locale.US)
}
