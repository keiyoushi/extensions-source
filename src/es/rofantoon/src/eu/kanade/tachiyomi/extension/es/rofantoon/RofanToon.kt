package eu.kanade.tachiyomi.extension.es.rofantoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class RofanToon : MangaThemesia(
    "Rofan Toon",
    "https://rofantoon.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val hasProjectPage = false

    override val projectPageString = "/proyectos"
}
