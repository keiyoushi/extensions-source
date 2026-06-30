package eu.kanade.tachiyomi.extension.id.sasangeyou

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Sasangeyou : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id"))

    override val hasProjectPage = false
}
