package eu.kanade.tachiyomi.extension.th.makimaaaaa

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Makimaaaaa : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th"))
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(ประเภท) a"
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(สถานะ) i"
}
