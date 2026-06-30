package eu.kanade.tachiyomi.extension.th.sodsaime

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class Sodsaime : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("th"))
}
