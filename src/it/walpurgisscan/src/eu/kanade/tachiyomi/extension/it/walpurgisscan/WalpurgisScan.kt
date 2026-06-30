package eu.kanade.tachiyomi.extension.it.walpurgisscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class WalpurgisScan : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale("it"))
}
