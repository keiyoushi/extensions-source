package eu.kanade.tachiyomi.extension.es.doujinslat

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class DoujinsLat : MangaThemesia(
    "Doujins.lat",
    "https://doujins.lat",
    "es",
    mangaUrlDirectory = "/comic",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es")),
) {
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(Tipo) a"

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            val excludeFields = listOf(
                "Estatus",
                "Tipo",
                // "Circle",
                "Artista",
                // "Parodia",
                "Posteado por",
                "Posteado",
                "Actualizado",
            )

            description = buildString {
                description.orEmpty()
                if (isNotEmpty()) append("\n\n")
                document.select(".tsinfo > .imptdt")
                    .map { Pair(it.ownText().removeSuffix(":"), it.selectFirst("> *")?.text()) }
                    .filterNot { it.second.isNullOrEmpty() }
                    .filterNot { excludeFields.contains(it.first) }
                    .joinToString("\n") { "${it.first}: ${it.second}" }
                    .also { append(it) }
            }
        }
    }
}
