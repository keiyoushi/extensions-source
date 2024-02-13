package eu.kanade.tachiyomi.extension.es.carteldemanhwas

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class CarteldeManhwas : MangaThemesia(
    "Cartel de Manhwas",
    "https://carteldemanhwas.com",
    "es",
    mangaUrlDirectory = "/series",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val hasProjectPage = true
    override val projectPageString = "/proyectos"

    override fun searchMangaSelector() = ".utao .uta .imgu:not(:has(span.novelabel)), " +
        ".listupd .bs .bsx:not(:has(span.novelabel)), " +
        ".listo .bs .bsx:not(:has(span.novelabel))"

    private class StatusFilter : SelectFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
        ),
    )
}
