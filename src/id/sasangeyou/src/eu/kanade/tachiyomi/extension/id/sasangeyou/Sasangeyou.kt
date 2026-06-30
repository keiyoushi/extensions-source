package eu.kanade.tachiyomi.extension.id.sasangeyou

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class Sasangeyou : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id"))

    override val hasProjectPage = false
}
