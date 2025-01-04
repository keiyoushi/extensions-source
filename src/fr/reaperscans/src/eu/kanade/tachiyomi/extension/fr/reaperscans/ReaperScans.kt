package eu.kanade.tachiyomi.extension.fr.reaperscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document

class ReaperScans : Keyoapp(
    "Reaper Scans",
    "https://reaper-scans.fr",
    "fr",
) {

    override fun mangaDetailsParse(document: Document): SManga =
        super.mangaDetailsParse(document).apply {
            // Fix a typo in the class name
            if (description.isNullOrBlank() == true) {
                description = document.selectFirst("div.grid > div.overflow-hiddfen > p")?.text()
            }

            // Check that text is "Statut"
            val statusSpan =
                document.select("div:has(> img) > span").firstOrNull { it.text() == "Status" }
            status =
                statusSpan?.parent()?.parent()?.selectXpath("div[2]")?.firstOrNull().parseStatus()

            genre = buildList {
                document.select("div:has(>h1) > div > a").forEach { add(it.text()) }
            }.joinToString()
        }

    // Migrated from Madara to Keyoapp.
    override val versionId = 4
}
