package eu.kanade.tachiyomi.extension.id.melokomik

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MELOKOMIK : MangaThemesia("MELOKOMIK", "https://melokomik.xyz", "id", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))) {

    override val hasProjectPage = true
}
