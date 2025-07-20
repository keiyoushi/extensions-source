package eu.kanade.tachiyomi.extension.es.carteldemanhwas

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class CarteldeManhwas : MangaThemesia(
    "Cartel de Manhwas",
    "https://carteldemanhwas.net",
    "es",
    mangaUrlDirectory = "/proyectos",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {

    override fun searchMangaSelector() = ".utao .uta .imgu:not(:has(span.novelabel)), " +
        ".listupd .bs .bsx:not(:has(span.novelabel)), " +
        ".listo .bs .bsx:not(:has(span.novelabel))"

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }
}
